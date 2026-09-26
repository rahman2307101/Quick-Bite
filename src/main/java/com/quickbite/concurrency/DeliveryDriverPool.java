package com.quickbite.concurrency;

import com.quickbite.dao.DeliveryDAO;
import com.quickbite.dao.UserDAO;
import com.quickbite.model.Delivery;
import com.quickbite.model.DeliveryStaff;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates shared resource concurrency and thread synchronization.
 * Multiple concurrent orders compete to acquire available delivery drivers.
 * Uses synchronized critical sections to prevent duplicate driver assignment and race conditions.
 *
 * Orders that can't get a driver immediately are held in a real pending queue and
 * auto-dispatched the moment a driver becomes free, instead of just being logged and dropped.
 */
public class DeliveryDriverPool {
    private static DeliveryDriverPool instance;

    /** Drivers free for a new delivery. LinkedHashSet keeps FIFO order, so a driver who has
     *  been idle longest is picked first — a simple, fair round-robin dispatch policy. */
    private final Set<Integer> availableDriverIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<Integer, Integer> activeDriverToOrderMap = Collections.synchronizedMap(new HashMap<>());
    /** Orders waiting for a driver, oldest first. Drained automatically whenever releaseDriver() runs. */
    private final Deque<Integer> pendingOrderQueue = new ArrayDeque<>();
    /** Notified whenever an order (queued or not) gets a driver, so background workers can react. */
    private final List<DispatchListener> dispatchListeners = new CopyOnWriteArrayList<>();

    private final UserDAO userDAO = new UserDAO();
    private final DeliveryDAO deliveryDAO = new DeliveryDAO();
    private final ConcurrencyMonitorService monitor = ConcurrencyMonitorService.getInstance();

    private DeliveryDriverPool() {
        reloadDrivers();
    }

    public static synchronized DeliveryDriverPool getInstance() {
        if (instance == null) {
            instance = new DeliveryDriverPool();
        }
        return instance;
    }

    /** Fired whenever a driver is assigned to an order, whether immediately or from the pending queue. */
    public interface DispatchListener {
        void onDriverAssigned(int orderId, int driverId);
    }

    public void addDispatchListener(DispatchListener listener) {
        dispatchListeners.add(listener);
    }

    public void removeDispatchListener(DispatchListener listener) {
        dispatchListeners.remove(listener);
    }

    private void notifyDispatch(int orderId, int driverId) {
        // Listeners are expected to be fast/non-blocking (e.g. complete a Future or hop to
        // Platform.runLater) since this runs while other pool state may still be settling.
        for (DispatchListener l : dispatchListeners) {
            try {
                l.onDriverAssigned(orderId, driverId);
            } catch (Exception ex) {
                monitor.logEvent("DriverPool", "Dispatch listener threw an exception: " + ex.getMessage());
            }
        }
    }

    /**
     * Re-populates the driver pool from database records, reconciling with any deliveries
     * that are still in progress (e.g. if the app was restarted mid-delivery) so a driver
     * who is actually out on a run doesn't get incorrectly marked available again.
     */
    public synchronized void reloadDrivers() {
        // Rebuild the busy set from the DB truth, not just in-memory state, so a restart
        // mid-delivery doesn't hand out a driver who's still actually on the road.
        Map<Integer, Integer> busyFromDb = new HashMap<>();
        for (Delivery d : deliveryDAO.getAllActiveDeliveries()) {
            busyFromDb.put(d.getDeliveryStaffId(), d.getOrderId());
        }
        activeDriverToOrderMap.putAll(busyFromDb);

        List<DeliveryStaff> staffList = userDAO.getAllDeliveryStaff();
        int newlyAdded = 0;
        for (DeliveryStaff staff : staffList) {
            int id = staff.getId();
            boolean alreadyKnown = activeDriverToOrderMap.containsKey(id) || availableDriverIds.contains(id);
            if (!alreadyKnown) {
                availableDriverIds.add(id);
                newlyAdded++;
            }
        }
        monitor.logEvent("DriverPool", "Pool reloaded: " + availableDriverIds.size() + " available, " +
                activeDriverToOrderMap.size() + " busy (reconciled with DB)" +
                (newlyAdded > 0 ? ", " + newlyAdded + " newly registered driver(s) added." : "."));
    }

    /**
     * CRITICAL SECTION:
     * Synchronized method protecting the shared pool of delivery drivers.
     * Prevents race conditions where two simultaneous orders try to claim the same driver.
     *
     * @param orderId ID of the order needing delivery
     * @return Assigned driver ID, or -1 if all drivers are currently busy (the order is
     *         queued internally and will be auto-dispatched when a driver next frees up)
     */
    public synchronized int acquireDriver(int orderId) {
        monitor.logEvent("DriverPool", "Thread requesting driver for Order #" + orderId + " [LOCK ACQUIRED]");
        try {
            // Check if this order already has an assigned driver
            for (Map.Entry<Integer, Integer> entry : activeDriverToOrderMap.entrySet()) {
                if (entry.getValue().equals(orderId)) {
                    monitor.logEvent("DriverPool", "Order #" + orderId + " already has Driver #" + entry.getKey());
                    return entry.getKey();
                }
            }

            if (availableDriverIds.isEmpty()) {
                if (!pendingOrderQueue.contains(orderId)) {
                    pendingOrderQueue.addLast(orderId);
                }
                monitor.logEvent("DriverPool", "RESOURCE EXHAUSTION: No available drivers for Order #" + orderId +
                        ". Queued at position " + pendingOrderQueue.size() + "; will auto-dispatch on next release.");
                return -1;
            }

            // Pick the longest-idle available driver (FIFO)
            Iterator<Integer> it = availableDriverIds.iterator();
            int selectedDriverId = it.next();
            it.remove(); // Remove from available set

            activeDriverToOrderMap.put(selectedDriverId, orderId);

            // Record assignment in persistent database
            deliveryDAO.createOrAssignDelivery(orderId, selectedDriverId);

            monitor.logEvent("DriverPool", "SUCCESS: Assigned Driver #" + selectedDriverId + " to Order #" + orderId +
                    ". Remaining available: " + availableDriverIds.size());
            notifyDispatch(orderId, selectedDriverId);
            return selectedDriverId;
        } finally {
            monitor.logEvent("DriverPool", "Exiting assignment lock for Order #" + orderId + " [LOCK RELEASED]");
        }
    }

    /**
     * Same as acquireDriver(), but if no driver is free right now, waits (without blocking
     * the JavaFX thread — callers should invoke this from a background thread) until either
     * a driver frees up and this order is auto-dispatched, or the timeout elapses.
     *
     * @return a future completing with the assigned driver ID, or -1 if the timeout elapses first
     */
    public CompletableFuture<Integer> acquireDriverAsync(int orderId, long timeout, TimeUnit unit) {
        int immediate = acquireDriver(orderId);
        if (immediate != -1) {
            return CompletableFuture.completedFuture(immediate);
        }

        CompletableFuture<Integer> future = new CompletableFuture<>();
        DispatchListener[] listenerHolder = new DispatchListener[1];
        listenerHolder[0] = (oid, driverId) -> {
            if (oid == orderId && !future.isDone()) {
                future.complete(driverId);
                removeDispatchListener(listenerHolder[0]);
            }
        };
        addDispatchListener(listenerHolder[0]);

        // orTimeout completes exceptionally rather than with -1, so translate that for simpler callers.
        CompletableFuture<Integer> withFallback = new CompletableFuture<>();
        future.orTimeout(timeout, unit).whenComplete((result, ex) -> {
            removeDispatchListener(listenerHolder[0]);
            synchronized (this) {
                pendingOrderQueue.remove(orderId);
            }
            if (ex != null) withFallback.complete(-1);
            else withFallback.complete(result);
        });
        return withFallback;
    }

    /**
     * Releases a driver back into the pool upon delivery completion. If any order is
     * waiting in the pending queue, the freed driver is immediately auto-dispatched to the
     * oldest one instead of sitting idle — this is the real "retry when a driver frees up"
     * behavior; nothing has to poll for it.
     *
     * @param driverId ID of driver to return to pool
     */
    public synchronized void releaseDriver(int driverId) {
        Integer completedOrderId = activeDriverToOrderMap.remove(driverId);
        monitor.logEvent("DriverPool", "Driver #" + driverId + " finished Order #" +
                (completedOrderId != null ? completedOrderId : "N/A") + " and is back in the pool.");

        Integer nextOrderId = pendingOrderQueue.pollFirst();
        if (nextOrderId != null) {
            activeDriverToOrderMap.put(driverId, nextOrderId);
            deliveryDAO.createOrAssignDelivery(nextOrderId, driverId);
            monitor.logEvent("DriverPool", "AUTO-DISPATCH: Driver #" + driverId +
                    " immediately reassigned to queued Order #" + nextOrderId +
                    ". " + pendingOrderQueue.size() + " order(s) still waiting.");
            notifyDispatch(nextOrderId, driverId);
        } else {
            availableDriverIds.add(driverId);
            monitor.logEvent("DriverPool", "Available count: " + availableDriverIds.size());
        }
    }

    public synchronized int getAvailableCount() {
        return availableDriverIds.size();
    }

    public synchronized int getBusyCount() {
        return activeDriverToOrderMap.size();
    }

    public synchronized int getQueuedCount() {
        return pendingOrderQueue.size();
    }

    public synchronized Map<Integer, Integer> getActiveAssignments() {
        return new HashMap<>(activeDriverToOrderMap);
    }
}