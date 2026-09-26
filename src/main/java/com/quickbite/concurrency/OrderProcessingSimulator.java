package com.quickbite.concurrency;

import com.quickbite.dao.DeliveryDAO;
import com.quickbite.dao.OrderDAO;
import com.quickbite.model.Delivery;
import com.quickbite.model.Order;
import javafx.application.Platform;
import java.util.List;
import java.util.concurrent.*;

/**
 * Handles concurrent background order-processing tasks using ExecutorService and Runnable workers.
 * Manages the order lifecycle progression asynchronously without freezing the JavaFX application thread.
 */
public class OrderProcessingSimulator {
    private static OrderProcessingSimulator instance;

    /** How long a queued order will wait for a driver to free up before giving up. */
    private static final long DRIVER_WAIT_TIMEOUT_SECONDS = 30;
    /** Chance a just-assigned driver "declines" and the order has to be reassigned. */
    private static final double DRIVER_DECLINE_PROBABILITY = 0.15;

    // 4-thread pool as recommended in design specifications
    private final ExecutorService executorService = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private int count = 1;
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "QuickBite-Worker-" + (count++));
            t.setDaemon(true); // Allows JVM to exit cleanly
            return t;
        }
    });

    private final OrderDAO orderDAO = new OrderDAO();
    private final DeliveryDAO deliveryDAO = new DeliveryDAO();
    private final DeliveryDriverPool driverPool = DeliveryDriverPool.getInstance();
    private final ConcurrencyMonitorService monitor = ConcurrencyMonitorService.getInstance();

    /** Multiple listeners supported so several "Live Tracking" windows can be open at once
     *  (each for a different order) without clobbering one another's callback. */
    private final List<BiConsumerListener> statusListeners = new CopyOnWriteArrayList<>();

    private OrderProcessingSimulator() {}

    public static synchronized OrderProcessingSimulator getInstance() {
        if (instance == null) {
            instance = new OrderProcessingSimulator();
        }
        return instance;
    }

    /** Functional listener type — kept as a named interface (rather than reusing BiConsumer
     *  directly) purely so add/remove by reference works predictably with lambdas. */
    public interface BiConsumerListener {
        void onStatusUpdate(int orderId, String newStatus);
    }

    /** @deprecated kept for backward compatibility; prefer {@link #addStatusUpdateListener}, since
     *  this replaces any previously-registered listener rather than adding to them. */
    @Deprecated
    public void setStatusUpdateCallback(BiConsumerListener listener) {
        statusListeners.clear();
        statusListeners.add(listener);
    }

    public void addStatusUpdateListener(BiConsumerListener listener) {
        statusListeners.add(listener);
    }

    public void removeStatusUpdateListener(BiConsumerListener listener) {
        statusListeners.remove(listener);
    }

    private void notifyListeners(int orderId, String newStatus) {
        for (BiConsumerListener l : statusListeners) {
            try {
                l.onStatusUpdate(orderId, newStatus);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Submits an asynchronous background simulation pipeline for an order.
     * Progresses through lifecycle: PLACED -> CONFIRMED -> PREPARING -> READY -> OUT_FOR_DELIVERY -> DELIVERED.
     *
     * @param orderId ID of order to simulate
     * @param autoAdvanceAll If true, automatically progresses through all stages with timed delays;
     *                       if false, simulates kitchen prep then halts at READY for driver staff.
     */
    public void startOrderSimulation(int orderId, boolean autoAdvanceAll) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    monitor.logEvent("OrderProcessor", "Started asynchronous processing pipeline for Order #" + orderId);

                    // Step 1: PLACED -> CONFIRMED
                    Thread.sleep(randomDelayMillis(1500, 2500));
                    advanceStatus(orderId, Order.STATUS_CONFIRMED);

                    // Step 2: CONFIRMED -> PREPARING
                    Thread.sleep(randomDelayMillis(2500, 3500));
                    advanceStatus(orderId, Order.STATUS_PREPARING);

                    // Step 3: PREPARING -> READY
                    Thread.sleep(randomDelayMillis(3000, 5000));
                    advanceStatus(orderId, Order.STATUS_READY);

                    // Step 4: Acquire a driver from the shared resource pool, retrying through
                    // a decline or two, and really waiting (not just checking once) if the
                    // pool is temporarily exhausted.
                    int driverId = acquireDriverWithRetries(orderId);

                    if (driverId != -1) {
                        monitor.logEvent("OrderProcessor", "Driver #" + driverId + " successfully dispatched for Order #" + orderId);
                    } else {
                        monitor.logEvent("OrderProcessor", "Order #" + orderId + " is READY but no driver became available " +
                                "within " + DRIVER_WAIT_TIMEOUT_SECONDS + "s. Remains queued for manual/auto dispatch.");
                    }

                    if (autoAdvanceAll && driverId != -1) {
                        // Step 5: READY -> OUT_FOR_DELIVERY
                        Thread.sleep(randomDelayMillis(2500, 4500));
                        advanceStatus(orderId, Order.STATUS_OUT_FOR_DELIVERY);
                        Delivery deliv = deliveryDAO.getByOrderId(orderId);
                        if (deliv != null) {
                            deliveryDAO.updateStatus(deliv.getId(), Delivery.STATUS_PICKED_UP);
                        }

                        // Step 6: OUT_FOR_DELIVERY -> DELIVERED
                        Thread.sleep(randomDelayMillis(4000, 6000));
                        advanceStatus(orderId, Order.STATUS_DELIVERED);
                        if (deliv != null) {
                            deliveryDAO.updateStatus(deliv.getId(), Delivery.STATUS_DELIVERED);
                        }
                        driverPool.releaseDriver(driverId);
                        monitor.logEvent("OrderProcessor", "Order #" + orderId + " fully delivered! Task completed.");
                    } else if (autoAdvanceAll) {
                        monitor.logEvent("OrderProcessor", "Order #" + orderId + " halted at READY — no driver assigned yet.");
                    } else {
                        monitor.logEvent("OrderProcessor", "Order #" + orderId + " is READY for pickup. Awaiting delivery staff action.");
                    }

                } catch (InterruptedException e) {
                    monitor.logEvent("OrderProcessor", "Simulation interrupted for Order #" + orderId);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    monitor.logEvent("OrderProcessor", "Error during simulation for Order #" + orderId + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * Tries to get a driver for this order, simulating an occasional decline (the assigned
     * driver backs out and a different one has to be found) before falling back to a real,
     * bounded wait if the whole pool is currently exhausted.
     */
    private int acquireDriverWithRetries(int orderId) throws InterruptedException {
        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            int driverId = driverPool.acquireDriver(orderId);

            if (driverId == -1) {
                // Pool was empty at the moment of asking — really wait for the next release
                // (auto-dispatch) instead of giving up after a single check.
                try {
                    driverId = driverPool.acquireDriverAsync(orderId, DRIVER_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .get(DRIVER_WAIT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    driverId = -1;
                }
                if (driverId == -1) {
                    return -1; // Genuinely no driver became available in time
                }
            }

            if (ThreadLocalRandom.current().nextDouble() < DRIVER_DECLINE_PROBABILITY) {
                monitor.logEvent("OrderProcessor", "Driver #" + driverId + " declined Order #" + orderId +
                        " — releasing and finding another driver (attempt " + attempts + "/3).");
                driverPool.releaseDriver(driverId);
                Thread.sleep(randomDelayMillis(500, 1200));
                continue; // Try again with a different driver
            }

            return driverId; // Accepted
        }
        // Ran out of retries — whichever driver we last held is already released above,
        // so just report failure; the order stays queued for manual/next-cycle dispatch.
        return -1;
    }

    private long randomDelayMillis(long minInclusive, long maxInclusive) {
        return ThreadLocalRandom.current().nextLong(minInclusive, maxInclusive + 1);
    }

    private void advanceStatus(int orderId, String newStatus) {
        orderDAO.updateStatus(orderId, newStatus);
        monitor.logEvent("OrderProcessor", "Order #" + orderId + " transitioned to status: " + newStatus);

        // Notify UI on JavaFX thread via Platform.runLater if toolkit is initialized
        try {
            Platform.runLater(() -> notifyListeners(orderId, newStatus));
        } catch (IllegalStateException ignored) {}
    }

    public void submitCustomTask(Runnable task) {
        executorService.submit(task);
    }

    public void shutdown() {
        monitor.logEvent("OrderProcessor", "Shutting down thread pool executor...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}