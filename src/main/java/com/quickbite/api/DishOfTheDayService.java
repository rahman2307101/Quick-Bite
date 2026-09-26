package com.quickbite.api;

import com.quickbite.concurrency.ConcurrencyMonitorService;
import com.quickbite.dao.FoodItemDAO;
import com.quickbite.dao.RestaurantDAO;
import com.quickbite.model.DishOfTheDay;
import com.quickbite.model.FoodItem;
import com.quickbite.model.Restaurant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * "Dish of the Day" API/service.
 *
 * IMPORTANT: The featured dish is NEVER hardcoded. Every call queries the live
 * SQLite database for every currently AVAILABLE food item across every
 * registered restaurant (via {@link RestaurantDAO} + {@link FoodItemDAO}), and
 * then deterministically picks one based on today's calendar date.
 *
 * Because the pick is a function of (today's date, the current live menu):
 *   - it automatically rotates to a different real dish every day,
 *   - it automatically adjusts as restaurants add, remove, or toggle dishes,
 *   - it is identical for every customer who opens the app on the same day
 *     (a deterministic "daily seed" rather than a fresh random pick on every
 *     single login, which would be a poor "deal of the day" experience).
 *
 * If a future requirement calls for a real third-party "deal" REST API, this
 * class is the single seam to swap: {@link #getDishOfTheDay()} could instead
 * call out over HttpClient (the same pattern already used by
 * {@code WeatherApiClient}) and merge the response with local menu data,
 * without changing any caller in the UI layer.
 */
public class DishOfTheDayService {
    private static final DishOfTheDayService INSTANCE = new DishOfTheDayService();

    // A small rotation of discount tiers so even the "deal" itself isn't a fixed number.
    private static final int[] DISCOUNT_TIERS = {10, 15, 20, 25, 30};

    private final RestaurantDAO restaurantDAO = new RestaurantDAO();
    private final FoodItemDAO foodItemDAO = new FoodItemDAO();
    private final ConcurrencyMonitorService monitor = ConcurrencyMonitorService.getInstance();

    private DishOfTheDayService() {
    }

    public static DishOfTheDayService getInstance() {
        return INSTANCE;
    }

    /**
     * Returns today's Dish of the Day, dynamically selected from every currently
     * available food item across every registered restaurant, or {@code null}
     * if there are no available items in the system at all.
     */
    public DishOfTheDay getDishOfTheDay() {
        List<Restaurant> restaurants = restaurantDAO.getAll();
        List<FoodItem> candidateItems = new ArrayList<>();
        List<Restaurant> candidateRestaurants = new ArrayList<>();

        for (Restaurant r : restaurants) {
            for (FoodItem item : foodItemDAO.getByRestaurantId(r.getId())) {
                if (item.isAvailable()) {
                    candidateItems.add(item);
                    candidateRestaurants.add(r);
                }
            }
        }

        if (candidateItems.isEmpty()) {
            monitor.logEvent("DishOfTheDay", "No available menu items found in the database — skipping today's promotion.");
            return null;
        }

        // Deterministic daily rotation: same dish for everyone on a given calendar day,
        // automatically changes the next day, and shifts naturally as menus change size.
        long epochDay = LocalDate.now().toEpochDay();

        int itemIndex = (int) Math.floorMod(epochDay, candidateItems.size());
        FoodItem chosenItem = candidateItems.get(itemIndex);
        Restaurant chosenRestaurant = candidateRestaurants.get(itemIndex);

        int discountPercent = DISCOUNT_TIERS[(int) Math.floorMod(epochDay, DISCOUNT_TIERS.length)];
        double discountedPrice = Math.round(chosenItem.getPrice() * (100 - discountPercent) / 100.0 * 100.0) / 100.0;

        monitor.logEvent("DishOfTheDay", "Selected '" + chosenItem.getName() + "' from " + chosenRestaurant.getName()
                + " as today's Dish of the Day (" + discountPercent + "% off, chosen from "
                + candidateItems.size() + " eligible available dishes).");

        return new DishOfTheDay(chosenItem, chosenRestaurant.getId(), chosenRestaurant.getName(), discountPercent, discountedPrice);
    }
}
