package com.quickbite.model;

/**
 * Data Transfer Object representing the current "Dish of the Day" promotion.
 *
 * This is never a fixed/hardcoded dish — it wraps a real {@link FoodItem} that
 * {@code com.quickbite.api.DishOfTheDayService} selected dynamically from the
 * live database, along with which restaurant it belongs to and today's
 * special discounted price.
 */
public class DishOfTheDay {
    private final FoodItem foodItem;
    private final int restaurantId;
    private final String restaurantName;
    private final int discountPercent;
    private final double discountedPrice;

    public DishOfTheDay(FoodItem foodItem, int restaurantId, String restaurantName, int discountPercent, double discountedPrice) {
        this.foodItem = foodItem;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.discountPercent = discountPercent;
        this.discountedPrice = discountedPrice;
    }

    public FoodItem getFoodItem() {
        return foodItem;
    }

    public int getRestaurantId() {
        return restaurantId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public double getDiscountedPrice() {
        return discountedPrice;
    }

    @Override
    public String toString() {
        return String.format("Dish of the Day: %s from %s — BDT %.2f (%d%% off)",
                foodItem.getName(), restaurantName, discountedPrice, discountPercent);
    }
}
