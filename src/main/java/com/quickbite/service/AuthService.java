package com.quickbite.service;

import com.quickbite.dao.UserDAO;
import com.quickbite.model.Customer;
import com.quickbite.model.DeliveryStaff;
import com.quickbite.model.RestaurantAdmin;
import com.quickbite.model.User;
import com.quickbite.util.SessionContext;

/**
 * Service handling user authentication, registration, input validation, and session context.
 */
public class AuthService {
    private final UserDAO userDAO = new UserDAO();

    /**
     * Authenticates a user by email and password only (no role check).
     * Kept for backward compatibility / internal reuse.
     */
    public User login(String email, String password) throws IllegalArgumentException {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        User user = userDAO.authenticate(email, password);
        if (user == null) {
            throw new IllegalArgumentException("Invalid email or password. Please try again.");
        }

        SessionContext.setCurrentUser(user);
        return user;
    }

    /**
     * Role-checked login. Only succeeds if the authenticated account's actual role
     * matches the role card the person selected on the login screen
     * (e.g. "CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_STAFF").
     *
     * This prevents, for example, a Restaurant Admin account from logging in
     * via the "Customer" tab, and vice versa.
     */
    public User login(String email, String password, String expectedRole) throws IllegalArgumentException {
        User user = login(email, password); // reuses validation + authentication above

        if (expectedRole != null && !expectedRole.equalsIgnoreCase(user.getRole())) {
            // Undo the session that login(email, password) just established,
            // since this login attempt is being rejected.
            SessionContext.logout();
            throw new IllegalArgumentException(
                    "User credentials are incorrect. Please try again."
            );
        }

        return user;
    }

    public User register(String name, String email, String password, String phone, String address, String role) throws IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("Please provide a valid email address.");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Address is required.");
        }

        // Check for duplicate email
        if (userDAO.findByEmail(email) != null) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        User newUser;
        if ("RESTAURANT_ADMIN".equalsIgnoreCase(role)) {
            newUser = new RestaurantAdmin(0, name.trim(), email.trim().toLowerCase(), password, phone.trim(), address.trim(), null, 1);
        } else if ("DELIVERY_STAFF".equalsIgnoreCase(role)) {
            newUser = new DeliveryStaff(0, name.trim(), email.trim().toLowerCase(), password, phone.trim(), address.trim(), null, true, "Motorbike");
        } else {
            newUser = new Customer(0, name.trim(), email.trim().toLowerCase(), password, phone.trim(), address.trim(), null);
        }

        boolean created = userDAO.create(newUser);
        if (!created) {
            throw new RuntimeException("Failed to create user account. Please check database connectivity.");
        }

        return newUser;
    }

    public void logout() {
        SessionContext.logout();
    }



}