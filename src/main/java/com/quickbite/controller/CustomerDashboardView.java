package com.quickbite.controller;

import com.quickbite.api.DishOfTheDayService;
import com.quickbite.api.SmartDeliveryService;
import com.quickbite.dao.FoodItemDAO;
import com.quickbite.dao.RestaurantDAO;
import com.quickbite.dao.ReviewDAO;
import com.quickbite.model.*;
import com.quickbite.service.AuthService;
import com.quickbite.service.MenuService;
import com.quickbite.service.OrderService;
import com.quickbite.util.AlertUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * QuickBite Customer Dashboard View — faithful implementation of the Figma dark UI design.
 * Features:
 * - Left Navigation Rail (Home, Explore, Orders, Saved, Profile, Logout)
 * - Top Search & Location Bar with personalized greeting
 * - Recent Orders Carousel with status badges
 * - Category Filter Pills (Pizza, Sushi, Burgers, Thai, Salads, Desserts)
 * - "Near You" 6-card Restaurant Grid with badges, ratings, delivery times & menu inspector
 * - Right Sidebar with Live Active Order Stepper + Courier Card & Interactive Shopping Cart
 * - Real-time checkout, SQLite persistence, and Smart Delivery Weather API integration
 * - "Dish of the Day" popup shown on login, dynamically selected via DishOfTheDayService
 *   (never hardcoded — pulled from the live menu database and rotated daily)
 */
public class CustomerDashboardView {

    private final Customer customer;
    private final RestaurantDAO restaurantDAO = new RestaurantDAO();
    private final MenuService menuService = new MenuService();
    private final OrderService orderService = new OrderService();
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final FoodItemDAO foodItemDAO = new FoodItemDAO();
    private final SmartDeliveryService smartDeliveryService = SmartDeliveryService.getInstance();
    private final DishOfTheDayService dishOfTheDayService = DishOfTheDayService.getInstance();

    // Flat delivery charge — no free-delivery offers
    private static final double DELIVERY_FEE = 65.00;

    // Cart state
    private final List<CartItem> cart = new ArrayList<>();
    private Restaurant selectedRestaurant;
    private String selectedCategory = "All";
    private double promoDiscount = 0.0;
    private String appliedPromo = "";

    // UI dynamic components
    private VBox mainScrollContent;
    private FlowPane restaurantGrid;
    private VBox cartItemsBox;
    private Label lblSubtotal;
    private Label lblDeliveryFee;
    private Label lblServiceFee;
    private Label lblTotal;
    private Label lblCartCountBadge;
    private Label lblCartSource;
    private Button btnPlaceOrder;
    private TextField searchField;
    private HBox categoryPillsRow;
    private Label lblNearYouCount;

    public CustomerDashboardView(Customer customer) {
        this.customer = customer;
    }

    public void show(Stage stage) {
        stage.setTitle("QuickBite — " + customer.getName());

        // Ensure default restaurant is KFC Bangladesh or first available
        List<Restaurant> allRests = restaurantDAO.getAll();
        for (Restaurant r : allRests) {
            if ("KFC Bangladesh".equalsIgnoreCase(r.getName())) {
                selectedRestaurant = r;
                break;
            }
        }
        if (selectedRestaurant == null && !allRests.isEmpty()) {
            selectedRestaurant = allRests.get(0);
        }

        HBox root = new HBox(0);
        root.setStyle("-fx-background-color: #0E0E10;");

        // 1. Left Nav Rail (fixed width 74px)
        VBox leftNav = buildLeftNavRail(stage);
        leftNav.setPrefWidth(74);
        leftNav.setMinWidth(74);
        leftNav.setMaxWidth(74);

        // 2. Center Content Area (Scrollable)
        VBox centerArea = buildCenterContent(stage);
        ScrollPane centerScroll = new ScrollPane(centerArea);
        centerScroll.setFitToWidth(true);
        centerScroll.setStyle("-fx-background-color: transparent; -fx-background: #111113; -fx-border-width: 0;");
        HBox.setHgrow(centerScroll, Priority.ALWAYS);

        // 3. Right Sidebar (fixed width 340px)
        VBox rightSidebar = buildRightSidebar(stage);
        rightSidebar.setPrefWidth(340);
        rightSidebar.setMinWidth(340);
        rightSidebar.setMaxWidth(340);

        root.getChildren().addAll(leftNav, centerScroll, rightSidebar);

        Scene scene = new Scene(root, 1280, 820);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }

        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();

        // Show today's "Dish of the Day" promo shortly after the dashboard renders,
        // so the main window is visible behind the popup instead of appearing blank.
        Platform.runLater(() -> showDishOfTheDayPopup(stage));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. LEFT NAVIGATION RAIL
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildLeftNavRail(Stage stage) {
        VBox rail = new VBox(22);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setPadding(new Insets(24, 10, 24, 10));
        rail.setStyle("-fx-background-color: #0C0C0E; -fx-border-color: #1F1F24; -fx-border-width: 0 1px 0 0;");

        // QuickBite Logo Button
        Button logoBtn = new Button("⚡");
        logoBtn.setStyle(
                "-fx-background-color: #FF5722;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 18px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-min-width: 44px;" +
                        "-fx-min-height: 44px;" +
                        "-fx-max-width: 44px;" +
                        "-fx-max-height: 44px;" +
                        "-fx-background-radius: 12px;" +
                        "-fx-cursor: hand;"
        );

        // Nav Items (Home, Explore, Orders, Saved, Profile)
        VBox navItems = new VBox(16);
        navItems.setAlignment(Pos.TOP_CENTER);

        VBox btnHome = navRailItem("⌂", "Home", true, e -> {
            selectedCategory = "All";
            refreshPills();
            refreshRestaurants();
        });

        VBox btnExplore = navRailItem("🧭", "Explore", false, e -> {
            showExploreDishesModal(stage);
        });

        VBox btnOrders = navRailItem("🛍", "Orders", false, e -> {
            showOrderHistoryModal(stage);
        });

        VBox btnSaved = navRailItem("🔖", "Saved", false, e -> {
            AlertUtil.showInfo("Saved Places", "You have saved 'KFC Bangladesh' to your favorites!");
        });



        navItems.getChildren().addAll(btnHome, btnExplore, btnOrders, btnSaved);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Logout Button
        Button btnLogout = new Button("⇥");
        btnLogout.setStyle(
                "-fx-background-color: #1A1A20;" +
                        "-fx-text-fill: #9CA3AF;" +
                        "-fx-font-size: 16px;" +
                        "-fx-min-width: 38px;" +
                        "-fx-min-height: 38px;" +
                        "-fx-background-radius: 19px;" +
                        "-fx-cursor: hand;"
        );
        btnLogout.setTooltip(new Tooltip("Logout"));
        btnLogout.setOnAction(e -> {
            new AuthService().logout();
            new LoginView().show(stage);
        });

        // User Avatar with Online Dot
        StackPane avatarBox = new StackPane();
        Label avatar = new Label(customer.getName() != null && !customer.getName().isEmpty() ? customer.getName().substring(0, 1).toUpperCase() : "A");
        avatar.setStyle(
                "-fx-background-color: #27272A;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-min-width: 36px;" +
                        "-fx-min-height: 36px;" +
                        "-fx-background-radius: 18px;" +
                        "-fx-alignment: center;"
        );
        Circle onlineDot = new Circle(4.5, Color.web("#22C55E"));
        StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);
        avatarBox.getChildren().addAll(avatar, onlineDot);
        avatarBox.setCursor(Cursor.HAND);
        avatarBox.setOnMouseClicked(e -> showProfileModal(stage));

        rail.getChildren().addAll(logoBtn, navItems, spacer, btnLogout, avatarBox);
        return rail;
    }

    private VBox navRailItem(String icon, String label, boolean active, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER);
        box.setCursor(Cursor.HAND);
        box.setPrefWidth(52);
        box.setPadding(new Insets(6, 4, 6, 4));

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 18px; -fx-text-fill: " + (active ? "#FF5722;" : "#71717A;"));

        Label textLbl = new Label(label);
        textLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: " + (active ? "bold;" : "normal;") + " -fx-text-fill: " + (active ? "#FF5722;" : "#71717A;"));

        if (active) {
            box.setStyle("-fx-background-color: #23120B; -fx-border-color: #FF5722; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px;");
        } else {
            box.setStyle("-fx-background-color: transparent; -fx-background-radius: 10px;");
            box.setOnMouseEntered(e -> box.setStyle("-fx-background-color: #191920; -fx-background-radius: 10px;"));
            box.setOnMouseExited(e -> box.setStyle("-fx-background-color: transparent; -fx-background-radius: 10px;"));
        }

        box.getChildren().addAll(iconLbl, textLbl);
        box.setOnMouseClicked(e -> action.handle(new javafx.event.ActionEvent()));
        return box;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CENTER SCROLLABLE DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildCenterContent(Stage stage) {
        mainScrollContent = new VBox(22);
        mainScrollContent.setPadding(new Insets(26, 30, 36, 30));
        mainScrollContent.setStyle("-fx-background-color: #111113;");

        // Top Bar: Greeting + Search + Location + Notification
        HBox topBar = buildTopBar(stage);

        // Section 1: Recent Orders
        VBox recentOrdersSection = buildRecentOrdersSection(stage);

        // Section 2: Category Filter Pills
        categoryPillsRow = buildCategoryPillsRow();

        // Section 3: "Near you" Restaurant Grid
        VBox nearYouSection = buildNearYouSection(stage);

        mainScrollContent.getChildren().addAll(topBar, recentOrdersSection, categoryPillsRow, nearYouSection);
        return mainScrollContent;
    }

    private HBox buildTopBar(Stage stage) {
        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Greeting
        VBox greetBox = new VBox(2);
        Label quickBiteSub = new Label("QUICK BITE");
        quickBiteSub.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #71717A; -fx-letter-spacing: 1px;");

        HBox nameRow = new HBox(6);
        nameRow.setAlignment(Pos.BASELINE_LEFT);
        String firstName = customer.getName() != null ? customer.getName().split(" ")[0] : "Alex";
        Label heyName = new Label("Hey, " + firstName + " 👋");
        heyName.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label cravingLbl = new Label("What are you craving?");
        cravingLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #71717A;");
        nameRow.getChildren().addAll(heyName, cravingLbl);
        greetBox.getChildren().addAll(quickBiteSub, nameRow);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Search Field
        searchField = new TextField();
        searchField.setPromptText("🔍  Restaurants, dishes...");
        searchField.setStyle(
                "-fx-background-color: #18181C;" +
                        "-fx-text-fill: white;" +
                        "-fx-prompt-text-fill: #71717A;" +
                        "-fx-border-color: #27272F;" +
                        "-fx-border-radius: 20px;" +
                        "-fx-background-radius: 20px;" +
                        "-fx-padding: 8px 16px;" +
                        "-fx-pref-width: 250px;" +
                        "-fx-font-size: 12px;"
        );
        searchField.textProperty().addListener((obs, oldV, newV) -> refreshRestaurants());

        // Location Pill
        HBox locPill = new HBox(6);
        locPill.setAlignment(Pos.CENTER);
        locPill.setCursor(Cursor.HAND);
        locPill.setStyle(
                "-fx-background-color: #18181C;" +
                        "-fx-border-color: #27272F;" +
                        "-fx-border-radius: 20px;" +
                        "-fx-background-radius: 20px;" +
                        "-fx-padding: 8px 14px;"
        );
        String addr = customer.getAddress() != null && !customer.getAddress().isEmpty() ? customer.getAddress() : "W 72nd St, New York";
        if (addr.length() > 20) addr = addr.substring(0, 18) + "...";
        Label locText = new Label("📍 " + addr + "  ▾");
        locText.setStyle("-fx-font-size: 12px; -fx-text-fill: #E4E4E7;");
        locPill.getChildren().add(locText);
        locPill.setOnMouseClicked(e -> showAddressChangeDialog());
        topBar.getChildren().addAll(greetBox, spacer, searchField, locPill);
        return topBar;
    }

    // ── Recent Orders ────────────────────────────────────────────────────────

    private VBox buildRecentOrdersSection(Stage stage) {
        VBox section = new VBox(12);

        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Recent orders");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: white;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label viewAll = new Label("View all");
        viewAll.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #FF5722; -fx-cursor: hand;");
        viewAll.setOnMouseClicked(e -> showOrderHistoryModal(stage));

        headerRow.getChildren().addAll(title, sp, viewAll);

        // Real orders for this customer, newest first (OrderDAO already sorts DESC by id)
        List<Order> recentOrders = orderService.getCustomerOrders(customer.getId());

        HBox cardsRow = new HBox(14);

        if (recentOrders.isEmpty()) {
            Label empty = new Label("No orders yet — place your first order below and it'll show up here!");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #71717A; -fx-padding: 16px 4px;");
            cardsRow.getChildren().add(empty);
        } else {
            int limit = Math.min(3, recentOrders.size());
            for (int i = 0; i < limit; i++) {
                cardsRow.getChildren().add(buildRecentOrderCard(recentOrders.get(i), stage));
            }
        }

        section.getChildren().addAll(headerRow, cardsRow);
        return section;
    }

    private HBox buildRecentOrderCard(Order order, Stage stage) {
        HBox card = new HBox(12);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
                "-fx-background-color: #17171C;" +
                        "-fx-border-color: #23232A;" +
                        "-fx-border-radius: 12px;" +
                        "-fx-background-radius: 12px;" +
                        "-fx-padding: 12px 14px;" +
                        "-fx-cursor: hand;"
        );

        String restName = order.getRestaurantName() != null ? order.getRestaurantName() : "Restaurant #" + order.getRestaurantId();

        // Image / Emoji Icon Box
        StackPane iconBox = new StackPane();
        iconBox.setPrefSize(44, 44);
        iconBox.setStyle("-fx-background-color: #22222A; -fx-background-radius: 10px;");
        Label iconLbl = new Label(emojiForRestaurant(restName));
        iconLbl.setStyle("-fx-font-size: 20px;");
        iconBox.getChildren().add(iconLbl);

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLbl = new Label(restName);
        nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label itemsLbl = new Label(formatOrderItemsSummary(order));
        itemsLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF; -fx-text-overflow: ellipsis;");
        itemsLbl.setWrapText(false);
        itemsLbl.setMaxWidth(190);

        HBox priceTimeRow = new HBox(6);
        Label priceLbl = new Label(String.format("BDT %.2f", order.getTotalAmount()));
        priceLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #E4E4E7;");

        Label timeLbl = new Label("· " + formatRelativeOrderTime(order.getCreatedAt()));
        timeLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #71717A;");
        priceTimeRow.getChildren().addAll(priceLbl, timeLbl);

        info.getChildren().addAll(nameLbl, itemsLbl, priceTimeRow);

        // Status pill reflects the order's real lifecycle status
        Label statusBadge = buildOrderStatusBadge(order.getStatus());

        card.getChildren().addAll(iconBox, info, statusBadge);

        card.setOnMouseClicked(e -> reorderFromOrder(stage, order));

        return card;
    }

    /**
     * Best-effort emoji for a restaurant based on its name/cuisine keywords.
     */
    private String emojiForRestaurant(String name) {
        if (name == null) return "🍽️";
        String n = name.toLowerCase();
        if (n.contains("kfc") || n.contains("chicken")) return "🍗";
        if (n.contains("pizza")) return "🍕";
        if (n.contains("sushi") || n.contains("japan")) return "🍣";
        if (n.contains("burger")) return "🍔";
        if (n.contains("thai")) return "🍜";
        if (n.contains("salad")) return "🥗";
        if (n.contains("dessert") || n.contains("cake") || n.contains("sweet")) return "🍰";
        return "🍽️";
    }

    /**
     * Joins an order's line items into a short "Name ×Qty, Name ×Qty" summary string.
     */
    private String formatOrderItemsSummary(Order order) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) return "No items";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            OrderItem oi = items.get(i);
            if (i > 0) sb.append(", ");
            sb.append(oi.getFoodName()).append(" ×").append(oi.getQuantity());
        }
        String result = sb.toString();
        return result.length() > 48 ? result.substring(0, 45) + "..." : result;
    }

    /**
     * Formats the order's "yyyy-MM-dd HH:mm:ss" timestamp as "Today · 12:34 PM" / "Yesterday · ..." / "Sep 20 · ...".
     */
    private String formatRelativeOrderTime(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) return "";
        try {
            LocalDateTime dt = LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDate today = LocalDate.now();
            String timePart = dt.format(DateTimeFormatter.ofPattern("h:mm a"));
            if (dt.toLocalDate().isEqual(today)) {
                return "Today · " + timePart;
            } else if (dt.toLocalDate().isEqual(today.minusDays(1))) {
                return "Yesterday · " + timePart;
            } else {
                return dt.format(DateTimeFormatter.ofPattern("MMM d")) + " · " + timePart;
            }
        } catch (DateTimeParseException ex) {
            return createdAt;
        }
    }

    /**
     * Builds a colored status pill matching the order's real lifecycle status.
     */
    private Label buildOrderStatusBadge(String status) {
        String text;
        String bg;
        String fg;
        String s = status == null ? "" : status;

        if (Order.STATUS_DELIVERED.equals(s)) {
            text = "✓ Delivered";
            bg = "#062818";
            fg = "#22C55E";
        } else if (Order.STATUS_CANCELLED.equals(s)) {
            text = "✕ Cancelled";
            bg = "#2A1212";
            fg = "#EF4444";
        } else if (Order.STATUS_OUT_FOR_DELIVERY.equals(s)) {
            text = "🚗 On the way";
            bg = "#241A08";
            fg = "#F59E0B";
        } else if (Order.STATUS_PREPARING.equals(s)) {
            text = "👨‍🍳 Preparing";
            bg = "#241A08";
            fg = "#F59E0B";
        } else if (Order.STATUS_READY.equals(s)) {
            text = "Ready";
            bg = "#0B2430";
            fg = "#22D3EE";
        } else if (Order.STATUS_CONFIRMED.equals(s)) {
            text = "Confirmed";
            bg = "#10192E";
            fg = "#60A5FA";
        } else {
            text = "Placed";
            bg = "#1C1C24";
            fg = "#9CA3AF";
        }

        Label badge = new Label(text);
        badge.setStyle(
                "-fx-background-color: " + bg + ";" +
                        "-fx-text-fill: " + fg + ";" +
                        "-fx-font-size: 9px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 4px 8px;" +
                        "-fx-background-radius: 6px;"
        );
        return badge;
    }

    // ── Category Filter Pills ────────────────────────────────────────────────

    private HBox buildCategoryPillsRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        String[] cats = {"All", "Pizza", "Sushi", "Burgers", "Thai", "Salads", "Desserts"};
        String[] icons = {"+", "🍕", "🍣", "🍔", "🍜", "🥗", "🍰"};

        for (int i = 0; i < cats.length; i++) {
            final String cat = cats[i];
            final String icon = icons[i];

            Button pill = new Button((icon.equals("+") ? "+ All" : icon + " " + cat));
            pill.setCursor(Cursor.HAND);
            styleCategoryPill(pill, cat.equalsIgnoreCase(selectedCategory));

            pill.setOnAction(e -> {
                selectedCategory = cat;
                refreshPills();
                refreshRestaurants();
            });

            row.getChildren().add(pill);
        }

        return row;
    }

    private void styleCategoryPill(Button pill, boolean isSelected) {
        if (isSelected) {
            pill.setStyle(
                    "-fx-background-color: #FF5722;" +
                            "-fx-text-fill: white;" +
                            "-fx-font-size: 12px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-padding: 7px 16px;" +
                            "-fx-background-radius: 20px;"
            );
        } else {
            pill.setStyle(
                    "-fx-background-color: #191920;" +
                            "-fx-text-fill: #D4D4D8;" +
                            "-fx-font-size: 12px;" +
                            "-fx-font-weight: normal;" +
                            "-fx-padding: 7px 16px;" +
                            "-fx-border-color: #272730;" +
                            "-fx-border-radius: 20px;" +
                            "-fx-background-radius: 20px;"
            );
        }
    }

    private void refreshPills() {
        if (categoryPillsRow == null) return;
        for (javafx.scene.Node node : categoryPillsRow.getChildren()) {
            if (node instanceof Button btn) {
                String txt = btn.getText();
                boolean isMatch = (selectedCategory.equalsIgnoreCase("All") && txt.contains("All")) ||
                        (!selectedCategory.equalsIgnoreCase("All") && txt.toLowerCase().contains(selectedCategory.toLowerCase()));
                styleCategoryPill(btn, isMatch);
            }
        }
    }

    // ── "Near you" Restaurant Grid ───────────────────────────────────────────

    private VBox buildNearYouSection(Stage stage) {
        VBox section = new VBox(14);

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.BASELINE_LEFT);

        Label title = new Label("Near you");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        lblNearYouCount = new Label("6 places");
        lblNearYouCount.setStyle("-fx-font-size: 13px; -fx-text-fill: #71717A;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button btnFilter = new Button("⚡ Filter");
        btnFilter.setStyle(
                "-fx-background-color: #18181C;" +
                        "-fx-text-fill: #D4D4D8;" +
                        "-fx-border-color: #272730;" +
                        "-fx-border-radius: 8px;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 5px 12px;" +
                        "-fx-cursor: hand;"
        );
        btnFilter.setOnAction(e -> {
            AlertUtil.showInfo("Filter Options", "Currently sorted by Top Rating & Nearest Delivery Zone.");
        });

        headerRow.getChildren().addAll(title, lblNearYouCount, sp, btnFilter);

        restaurantGrid = new FlowPane();
        restaurantGrid.setHgap(16);
        restaurantGrid.setVgap(18);
        restaurantGrid.setAlignment(Pos.TOP_LEFT);

        refreshRestaurants();

        section.getChildren().addAll(headerRow, restaurantGrid);
        return section;
    }

    private void refreshRestaurants() {
        if (restaurantGrid == null) return;
        restaurantGrid.getChildren().clear();

        List<Restaurant> allRests = restaurantDAO.getAll();
        String query = searchField != null ? searchField.getText().toLowerCase().trim() : "";
        int displayedCount = 0;


        // Map mockup details
        Map<String, String[]> meta = new LinkedHashMap<>();
        String deliveryFeeStr = String.format("BDT %.2f", DELIVERY_FEE);
        meta.put("KFC Bangladesh", new String[]{"TOP PICK", "20% OFF", "Crispy Fried Chicken & Burgers", "★ 5.0 (2.8k)", "20–30 min", deliveryFeeStr, "#E4002B"});
        meta.put("Ember & Ash", new String[]{"POPULAR", "30% OFF", "Wood-fired Pizza", "★ 4.9 (1.2k)", "22–32 min", deliveryFeeStr, "#E25822"});
        meta.put("Shogun Omakase", new String[]{"NEW", "", "Premium Sushi", "★ 4.8 (876)", "35–45 min", deliveryFeeStr, "#3B82F6"});
        meta.put("The Patty Lab", new String[]{"", "", "Craft Burgers", "★ 4.7 (2.1k)", "18–28 min", deliveryFeeStr, "#EAB308"});
        meta.put("Lemongrass House", new String[]{"", "", "Authentic Thai", "★ 4.6 (543)", "28–38 min", deliveryFeeStr, "#10B981"});
        meta.put("Field & Fork", new String[]{"HEALTHY", "", "Garden Salads", "★ 4.5 (389)", "15–25 min", deliveryFeeStr, "#14B8A6"});
        meta.put("Petite Maison", new String[]{"TOP RATED", "", "French Desserts", "★ 4.9 (718)", "30–40 min", deliveryFeeStr, "#EC4899"});

        for (Restaurant r : allRests) {
            String[] m = meta.get(r.getName());
            String cuisine = m != null ? m[2] : r.getDescription();

            // Filter by category
            if (!"All".equalsIgnoreCase(selectedCategory)) {
                boolean matchesCategory = false;
                if (cuisine != null && cuisine.toLowerCase().contains(selectedCategory.toLowerCase()))
                    matchesCategory = true;
                if (r.getName().toLowerCase().contains(selectedCategory.toLowerCase())) matchesCategory = true;
                // Check if any menu items match
                List<FoodItem> items = menuService.getFoodItems(r.getId());
                for (FoodItem fi : items) {
                    if (fi.getCategory().equalsIgnoreCase(selectedCategory)) {
                        matchesCategory = true;
                        break;
                    }
                }
                if (!matchesCategory) continue;
            }

            // Filter by search query
            if (!query.isEmpty()) {
                boolean matchesSearch = r.getName().toLowerCase().contains(query) || (cuisine != null && cuisine.toLowerCase().contains(query));
                if (!matchesSearch) {
                    List<FoodItem> items = menuService.getFoodItems(r.getId());
                    for (FoodItem fi : items) {
                        if (fi.getName().toLowerCase().contains(query) || fi.getDescription().toLowerCase().contains(query)) {
                            matchesSearch = true;
                            break;
                        }
                    }
                }
                if (!matchesSearch) continue;
            }

            restaurantGrid.getChildren().add(buildRestaurantCard(r, m));
            displayedCount++;
        }

        if (displayedCount == 0) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(30, 20, 30, 20));
            emptyBox.setPrefWidth(550);
            Label emptyIcon = new Label("🏪");
            emptyIcon.setStyle("-fx-font-size: 38px;");
            Label emptyTitle = new Label("No registered partner restaurants found.");
            emptyTitle.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 14px; -fx-font-weight: bold;");
            Label emptySub = new Label("Only restaurants registered in the Restaurant Admin Dashboard are listed here.");
            emptySub.setStyle("-fx-text-fill: #71717A; -fx-font-size: 12px;");
            emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub);
            restaurantGrid.getChildren().add(emptyBox);
        }

        if (lblNearYouCount != null) {
            lblNearYouCount.setText(displayedCount + (displayedCount == 1 ? " place" : " places"));
        }
    }

    private VBox buildRestaurantCard(Restaurant r, String[] m) {
        VBox card = new VBox(0);
        card.setPrefWidth(265);
        card.setStyle(
                "-fx-background-color: #17171B;" +
                        "-fx-border-color: #24242C;" +
                        "-fx-border-radius: 14px;" +
                        "-fx-background-radius: 14px;" +
                        "-fx-cursor: hand;"
        );

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #1C1C22; -fx-border-color: #383844; -fx-border-radius: 14px; -fx-background-radius: 14px; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #17171B; -fx-border-color: #24242C; -fx-border-radius: 14px; -fx-background-radius: 14px; -fx-cursor: hand;"));

        // 1. Top Image Banner with Badges
        StackPane banner = new StackPane();
        banner.setPrefSize(265, 128);

        // Background styling / loaded image
        ImageView bannerImg = new ImageView();
        bannerImg.setFitWidth(265);
        bannerImg.setFitHeight(128);
        bannerImg.setPreserveRatio(false);
        Rectangle clip = new Rectangle(265, 128);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        bannerImg.setClip(clip);

        boolean loaded = false;
        if (r.getImageUrl() != null && !r.getImageUrl().isBlank()) {
            // 1) Try classpath resource first (src/main/resources/images/)
            try {
                var stream = getClass().getResourceAsStream("/images/" + r.getImageUrl());
                if (stream != null) {
                    Image img = new Image(stream, 265, 128, false, true);
                    bannerImg.setImage(img);
                    stream.close();
                    loaded = true;
                }
            } catch (Exception ignored) {
            }
            // 2) Fallback: raw file path or URL
            if (!loaded) {
                try {
                    File f = new File(r.getImageUrl());
                    String uri = f.exists() ? f.toURI().toString() : r.getImageUrl();
                    Image img = new Image(uri, 265, 128, false, true, true);
                    bannerImg.setImage(img);
                    loaded = true;
                } catch (Exception ignored) {
                }
            }
        }

        // Stylish gradient placeholder with themed emoji if no image file
        StackPane placeholder = new StackPane();
        placeholder.setPrefSize(265, 128);
        String colorAccent = m != null && m.length > 6 ? m[6] : "#E25822";
        placeholder.setStyle("-fx-background-color: linear-gradient(to bottom right, #24242C, " + colorAccent + "33); -fx-background-radius: 14px 14px 0 0;");
        Label icon = new Label(getRestaurantEmoji(r.getName()));
        icon.setStyle("-fx-font-size: 44px;");
        placeholder.getChildren().add(icon);

        if (loaded) banner.getChildren().add(bannerImg);
        else banner.getChildren().add(placeholder);

        // Floating Badges on Image
        HBox badgeBar = new HBox(6);
        badgeBar.setPadding(new Insets(10));
        badgeBar.setAlignment(Pos.TOP_LEFT);

        String leftTag = m != null ? m[0] : "PARTNER";
        String rightTag = m != null ? m[1] : "";

        if (!leftTag.isEmpty()) {
            Label tagLbl = new Label(leftTag);
            tagLbl.setStyle(
                    "-fx-background-color: rgba(255, 87, 34, 0.9);" +
                            "-fx-text-fill: white;" +
                            "-fx-font-size: 9px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-padding: 3px 7px;" +
                            "-fx-background-radius: 6px;"
            );
            badgeBar.getChildren().add(tagLbl);
        }

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        badgeBar.getChildren().add(sp);

        if (!rightTag.isEmpty()) {
            Label rightTagLbl = new Label(rightTag);
            rightTagLbl.setStyle(
                    "-fx-background-color: rgba(220, 38, 38, 0.9);" +
                            "-fx-text-fill: white;" +
                            "-fx-font-size: 9px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-padding: 3px 7px;" +
                            "-fx-background-radius: 6px;"
            );
            badgeBar.getChildren().add(rightTagLbl);
        }
        banner.getChildren().add(badgeBar);

        // 2. Card Content
        VBox content = new VBox(6);
        content.setPadding(new Insets(12, 14, 14, 14));

        // Name + Rating Row
        HBox nameRow = new HBox(6);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(r.getName());
        nameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);

        String ratingStr = m != null ? m[3] : String.format("★ %.1f", r.getRating());
        Label ratingLbl = new Label(ratingStr);
        ratingLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #F59E0B;");

        nameRow.getChildren().addAll(nameLbl, sp2, ratingLbl);

        // Cuisine Description
        String cuisineStr = m != null ? m[2] : r.getDescription();
        Label cuisineLbl = new Label(cuisineStr);
        cuisineLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");

        // Footer info (Delivery time + Fee)
        HBox footerRow = new HBox(8);
        footerRow.setAlignment(Pos.CENTER_LEFT);
        footerRow.setPadding(new Insets(4, 0, 0, 0));

        String timeStr = m != null ? m[4] : "20–30 min";
        Label timeLbl = new Label("🕒 " + timeStr);
        timeLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #71717A;");

        String feeStr = m != null ? m[5] : String.format("BDT %.2f", DELIVERY_FEE);
        Label feeLbl = new Label(feeStr + " delivery");
        feeLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #9CA3AF;");

        footerRow.getChildren().addAll(timeLbl, feeLbl);

        content.getChildren().addAll(nameRow, cuisineLbl, footerRow);
        card.getChildren().addAll(banner, content);

        // Click on restaurant card opens its full menu inspector
        card.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                selectedRestaurant = r;
                if (lblCartSource != null) {
                    lblCartSource.setText("From " + r.getName());
                }
                showRestaurantMenuModal(r);
            }
        });

        // Right-click context menu for quick actions
        ContextMenu ctxMenu = new ContextMenu();
        ctxMenu.setStyle("-fx-background-color: #1A1A22; -fx-border-color: #2F2F3B;");
        MenuItem miMenu = new MenuItem("🍽 View Menu & Dishes");
        miMenu.setOnAction(e -> {
            selectedRestaurant = r;
            if (lblCartSource != null) lblCartSource.setText("From " + r.getName());
            showRestaurantMenuModal(r);
        });
        MenuItem miDelete = new MenuItem("🗑 Remove Restaurant");
        miDelete.setStyle("-fx-text-fill: #EF4444;");
        miDelete.setOnAction(e -> {
            if (AlertUtil.showConfirmation("Remove Restaurant", "Are you sure you want to remove '" + r.getName() + "' from QuickBite?")) {
                if (restaurantDAO.delete(r.getId())) {
                    AlertUtil.showInfo("Removed", "'" + r.getName() + "' was removed successfully.");
                    refreshRestaurants();
                } else {
                    AlertUtil.showError("Error", "Could not remove restaurant.");
                }
            }
        });
        ctxMenu.getItems().addAll(miMenu, miDelete);
        card.setOnContextMenuRequested(e -> ctxMenu.show(card, e.getScreenX(), e.getScreenY()));

        return card;
    }

    private String getRestaurantEmoji(String name) {
        if (name == null) return "🍽";
        if (name.contains("KFC")) return "🍗";
        if (name.contains("Ember") || name.contains("Italia") || name.contains("Pizza")) return "🍕";
        if (name.contains("Shogun") || name.contains("Tokyo") || name.contains("Sushi") || name.contains("Ramen"))
            return "🍣";
        if (name.contains("Patty") || name.contains("Burger")) return "🍔";
        if (name.contains("Lemongrass") || name.contains("Thai")) return "🍜";
        if (name.contains("Field") || name.contains("Green") || name.contains("Salad")) return "🥗";
        if (name.contains("Petite") || name.contains("Maison") || name.contains("Dessert")) return "🍰";
        return "🍽";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. RIGHT SIDEBAR — CART
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildRightSidebar(Stage stage) {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(24, 20, 24, 20));
        sidebar.setStyle("-fx-background-color: #131316; -fx-border-color: #1F1F24; -fx-border-width: 0 0 0 1px;");

        VBox cartBox = buildCartWidget(stage);
        VBox.setVgrow(cartBox, Priority.ALWAYS);

        sidebar.getChildren().add(cartBox);
        return sidebar;
    }

    // ── Cart Section ─────────────────────────────────────────────────────────

    private VBox buildCartWidget(Stage stage) {
        VBox box = new VBox(12);

        // Cart Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Your cart");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        lblCartCountBadge = new Label(cart.size() + " items");
        lblCartCountBadge.setStyle("-fx-background-color: #2D140D; -fx-text-fill: #FF5722; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2px 7px; -fx-background-radius: 6px;");

        header.getChildren().addAll(title, lblCartCountBadge);

        // Cart Restaurant Source
        String sourceName = selectedRestaurant != null ? selectedRestaurant.getName() : "KFC Bangladesh";
        lblCartSource = new Label("From " + sourceName);
        lblCartSource.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");

        // Scrollable Cart Items
        cartItemsBox = new VBox(10);
        ScrollPane cartScroll = new ScrollPane(cartItemsBox);
        cartScroll.setFitToWidth(true);
        cartScroll.setPrefHeight(170);
        cartScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-width: 0;");
        VBox.setVgrow(cartScroll, Priority.ALWAYS);

        // Price Breakdown
        VBox summary = new VBox(6);
        summary.setStyle("-fx-border-color: #22222A; -fx-border-width: 1px 0 0 0; -fx-padding: 10px 0 0 0;");

        HBox subtotalRow = new HBox();
        lblSubtotal = new Label("BDT 0.00");
        lblSubtotal.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 12px;");
        Region s1 = new Region();
        HBox.setHgrow(s1, Priority.ALWAYS);
        subtotalRow.getChildren().addAll(styledLabel("Subtotal"), s1, lblSubtotal);

        HBox deliveryRow = new HBox();
        lblDeliveryFee = new Label(String.format("BDT %.2f", DELIVERY_FEE));
        lblDeliveryFee.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 12px;");
        Region s2 = new Region();
        HBox.setHgrow(s2, Priority.ALWAYS);
        deliveryRow.getChildren().addAll(styledLabel("Delivery"), s2, lblDeliveryFee);

        HBox serviceRow = new HBox();
        lblServiceFee = new Label("BDT 2.50");
        lblServiceFee.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 12px;");
        Region s3 = new Region();
        HBox.setHgrow(s3, Priority.ALWAYS);
        serviceRow.getChildren().addAll(styledLabel("Service fee"), s3, lblServiceFee);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #22222A;");

        HBox totalRow = new HBox();
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Label totalTitle = new Label("Total");
        totalTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        Region s4 = new Region();
        HBox.setHgrow(s4, Priority.ALWAYS);
        lblTotal = new Label("BDT 0.00");
        lblTotal.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        totalRow.getChildren().addAll(totalTitle, s4, lblTotal);

        summary.getChildren().addAll(subtotalRow, deliveryRow, serviceRow, sep, totalRow);

        // Promo Code Box
        HBox promoBox = new HBox(8);
        promoBox.setAlignment(Pos.CENTER_LEFT);
        TextField txtPromo = new TextField();
        txtPromo.setPromptText("Promo code");
        txtPromo.setStyle(
                "-fx-background-color: #17171C;" +
                        "-fx-text-fill: white;" +
                        "-fx-prompt-text-fill: #52525B;" +
                        "-fx-border-color: #24242C;" +
                        "-fx-border-radius: 6px;" +
                        "-fx-background-radius: 6px;" +
                        "-fx-padding: 7px 10px;" +
                        "-fx-font-size: 11px;"
        );
        HBox.setHgrow(txtPromo, Priority.ALWAYS);

        Button btnApplyPromo = new Button("Apply");
        btnApplyPromo.setStyle("-fx-background-color: transparent; -fx-text-fill: #FF5722; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnApplyPromo.setOnAction(e -> {
            String code = txtPromo.getText().trim();
            if ("QUICKBITE".equalsIgnoreCase(code) || "EMBER".equalsIgnoreCase(code) || "FREE".equalsIgnoreCase(code)) {
                promoDiscount = 5.00;
                appliedPromo = code.toUpperCase();
                AlertUtil.showInfo("Promo Applied!", "Code '" + appliedPromo + "' applied! BDT 5.00 discount activated.");
                refreshCartDisplay();
            } else {
                AlertUtil.showWarning("Invalid Code", "Code '" + code + "' is not valid. Try 'QUICKBITE'.");
            }
        });

        promoBox.getChildren().addAll(txtPromo, btnApplyPromo);

        // Big Checkout Button
        btnPlaceOrder = new Button("Place order · BDT 0.00");
        btnPlaceOrder.setMaxWidth(Double.MAX_VALUE);
        btnPlaceOrder.setStyle(
                "-fx-background-color: #FF5722;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 13px;" +
                        "-fx-background-radius: 10px;" +
                        "-fx-cursor: hand;"
        );
        btnPlaceOrder.setOnAction(e -> handleCheckout(stage));

        box.getChildren().addAll(header, lblCartSource, cartScroll, summary, promoBox, btnPlaceOrder);
        refreshCartDisplay();
        return box;
    }

    private Label styledLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");
        return lbl;
    }

    private void refreshCartDisplay() {
        if (cartItemsBox == null) return;
        cartItemsBox.getChildren().clear();

        double subtotal = 0.0;
        int totalItems = 0;

        for (CartItem ci : cart) {
            subtotal += ci.getSubtotal();
            totalItems += ci.getQuantity();

            HBox itemRow = new HBox(8);
            itemRow.setAlignment(Pos.CENTER_LEFT);
            itemRow.setStyle("-fx-padding: 6px 0;");

            // Quantity stepper buttons
            HBox stepper = new HBox(4);
            stepper.setAlignment(Pos.CENTER);

            Button btnMinus = new Button("-");
            btnMinus.setStyle("-fx-background-color: #24242C; -fx-text-fill: #9CA3AF; -fx-font-size: 10px; -fx-padding: 1px 6px; -fx-background-radius: 4px; -fx-cursor: hand;");
            btnMinus.setOnAction(e -> {
                ci.decrementQuantity();
                refreshCartDisplay();
            });

            Label qtyLbl = new Label(String.valueOf(ci.getQuantity()));
            qtyLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 0 4px;");

            Button btnPlus = new Button("+");
            btnPlus.setStyle("-fx-background-color: #24242C; -fx-text-fill: #9CA3AF; -fx-font-size: 10px; -fx-padding: 1px 6px; -fx-background-radius: 4px; -fx-cursor: hand;");
            btnPlus.setOnAction(e -> {
                ci.incrementQuantity();
                refreshCartDisplay();
            });

            stepper.getChildren().addAll(btnMinus, qtyLbl, btnPlus);

            Label nameLbl = new Label(ci.getFoodItem().getName());
            nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            Label priceLbl = new Label(String.format("BDT %.2f", ci.getSubtotal()));
            priceLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");

            Button btnDel = new Button("✕");
            btnDel.setStyle("-fx-background-color: transparent; -fx-text-fill: #EF4444; -fx-font-size: 10px; -fx-cursor: hand;");
            btnDel.setOnAction(e -> {
                cart.remove(ci);
                refreshCartDisplay();
            });

            itemRow.getChildren().addAll(stepper, nameLbl, priceLbl, btnDel);
            cartItemsBox.getChildren().add(itemRow);
        }

        double delivery = cart.isEmpty() ? 0.0 : DELIVERY_FEE; // Flat delivery charge, no free-delivery offers
        double service = cart.isEmpty() ? 0.0 : 2.50;
        double total = Math.max(0.0, subtotal + delivery + service - promoDiscount);

        if (lblCartCountBadge != null) lblCartCountBadge.setText(totalItems + " items");
        if (lblSubtotal != null) lblSubtotal.setText(String.format("BDT %.2f", subtotal));
        if (lblDeliveryFee != null) lblDeliveryFee.setText(String.format("BDT %.2f", delivery));
        if (lblServiceFee != null) lblServiceFee.setText(String.format("BDT %.2f", service));
        if (lblTotal != null) lblTotal.setText(String.format("BDT %.2f", total));
        if (btnPlaceOrder != null) btnPlaceOrder.setText(String.format("Place order · BDT %.2f", total));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. RESTAURANT MENU MODAL (Adds dishes with food images!)
    // ─────────────────────────────────────────────────────────────────────────

    private void showRestaurantMenuModal(Restaurant r) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle(r.getName() + " — Full Menu");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #111113;");

        // Header
        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);

        Label rTitle = new Label(r.getName());
        rTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label ratingLbl = new Label(String.format("★ %.1f", r.getRating()));
        ratingLbl.setStyle("-fx-background-color: #2D140D; -fx-text-fill: #FF5722; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-background-radius: 6px;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnClose = new Button("✕ Close");
        btnClose.setStyle("-fx-background-color: #1F1F26; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6px;");
        btnClose.setOnAction(e -> modal.close());

        head.getChildren().addAll(rTitle, ratingLbl, sp, btnClose);

        Label desc = new Label(r.getDescription());
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");

        // Dishes Grid
        FlowPane dishesPane = new FlowPane();
        dishesPane.setHgap(14);
        dishesPane.setVgap(14);

        List<FoodItem> items = menuService.getFoodItems(r.getId());
        for (FoodItem item : items) {
            dishesPane.getChildren().add(buildFoodItemCard(item, modal));
        }

        ScrollPane scroll = new ScrollPane(dishesPane);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #111113; -fx-border-width: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(head, desc, scroll);

        Scene s = new Scene(root, 760, 560);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        modal.setScene(s);
        modal.show();
    }

    private VBox buildFoodItemCard(FoodItem item, Stage modal) {
        VBox card = new VBox(0);
        card.setPrefWidth(225);
        card.setStyle(
                "-fx-background-color: #17171C;" +
                        "-fx-border-color: #25252E;" +
                        "-fx-border-radius: 10px;" +
                        "-fx-background-radius: 10px;"
        );

        // Food Image Banner
        ImageView imgView = new ImageView();
        imgView.setFitWidth(225);
        imgView.setFitHeight(115);
        imgView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(225, 115);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        imgView.setClip(clip);

        boolean loaded = false;
        String path = item.getImageUrl();
        if (path != null && !path.isBlank()) {
            // 1) Classpath resource (/images/<path>)
            try {
                var stream = getClass().getResourceAsStream("/images/" + path);
                if (stream != null) {
                    Image img = new Image(stream, 225, 115, false, true);
                    imgView.setImage(img);
                    stream.close();
                    loaded = true;
                }
            } catch (Exception ignored) {
            }

            // 2) Project images folder (images/<path>)
            if (!loaded) {
                try {
                    File f = new File("images/" + path);
                    if (f.exists()) {
                        Image img = new Image(f.toURI().toString(), 225, 115, false, true, true);
                        imgView.setImage(img);
                        loaded = true;
                    }
                } catch (Exception ignored) {
                }
            }

            // 3) Direct file path or URI
            if (!loaded) {
                try {
                    File f = new File(path);
                    if (f.exists()) {
                        Image img = new Image(f.toURI().toString(), 225, 115, false, true, true);
                        imgView.setImage(img);
                        loaded = true;
                    }
                } catch (Exception ignored) {
                }
            }

            // 4) Fallback to kfc.png if dish has generic image
            if (!loaded) {
                try {
                    var stream = getClass().getResourceAsStream("/images/kfc.png");
                    if (stream != null) {
                        Image img = new Image(stream, 225, 115, false, true);
                        imgView.setImage(img);
                        stream.close();
                        loaded = true;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!loaded) {
            StackPane placeholder = new StackPane();
            placeholder.setPrefSize(225, 115);
            placeholder.setStyle("-fx-background-color: #24242D; -fx-background-radius: 10px 10px 0 0;");
            Label icon = new Label(getRestaurantEmoji(item.getName()));
            icon.setStyle("-fx-font-size: 32px;");
            placeholder.getChildren().add(icon);
            card.getChildren().add(placeholder);
        } else {
            card.getChildren().add(imgView);
        }

        // Details
        VBox body = new VBox(6);
        body.setPadding(new Insets(10));

        Label name = new Label(item.getName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: white;");
        name.setWrapText(true);

        Label desc = new Label(item.getDescription());
        desc.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        desc.setWrapText(true);
        desc.setPrefHeight(32);

        HBox btm = new HBox();
        btm.setAlignment(Pos.CENTER_LEFT);
        Label price = new Label(String.format("BDT %.2f", item.getPrice()));
        price.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #FF5722;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnAdd = new Button("+ Add");
        btnAdd.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnAdd.setOnAction(e -> {
            addToCart(item);
            AlertUtil.showInfo("Added to Cart", item.getName() + " was added to your cart!");
        });

        btm.getChildren().addAll(price, sp, btnAdd);
        body.getChildren().addAll(name, desc, btm);
        card.getChildren().add(body);
        return card;
    }

    private void addToCart(FoodItem item) {
        addToCartSilently(item, 1);
        refreshCartDisplay();
    }

    /**
     * Adds (or bumps the quantity of) an item without triggering a UI refresh — for batch operations like reordering.
     */
    private void addToCartSilently(FoodItem item, int quantity) {
        if (item == null || quantity <= 0) return;
        for (CartItem ci : cart) {
            if (ci.getFoodItem().getId() == item.getId()) {
                ci.setQuantity(ci.getQuantity() + quantity);
                return;
            }
        }
        cart.add(new CartItem(item, quantity));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CHECKOUT & LIVE ORDER TRACKING
    // ─────────────────────────────────────────────────────────────────────────

    private void handleCheckout(Stage stage) {
        if (cart.isEmpty()) {
            AlertUtil.showWarning("Empty Cart", "Please add items to your cart before proceeding.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(stage);
        dialog.setTitle("QuickBite — Confirm Order");

        VBox form = new VBox(14);
        form.setPadding(new Insets(24));
        form.setStyle("-fx-background-color: #141417;");
        form.setPrefWidth(420);

        Label title = new Label("Review & Place Order");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        TextField txtAddress = new TextField(customer.getAddress() != null ? customer.getAddress() : "104 Sullivan St, SoHo");
        txtAddress.setStyle("-fx-background-color: #1D1D24; -fx-text-fill: white; -fx-border-color: #2F2F3B; -fx-border-radius: 6px; -fx-padding: 8px;");

        ComboBox<String> paymentCombo = new ComboBox<>();
        paymentCombo.getItems().addAll("Apple Pay", "Credit Card (•••• 4821)", "Cash on Delivery");
        paymentCombo.setValue("Apple Pay");
        paymentCombo.setMaxWidth(Double.MAX_VALUE);
        paymentCombo.setStyle("-fx-background-color: #1D1D24; -fx-border-color: #2F2F3B;");

        double subtotal = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
        double total = Math.max(0.0, subtotal + DELIVERY_FEE + 2.50 - promoDiscount);

        Label totalDue = new Label(String.format("Total Due: BDT %.2f (BDT %.2f delivery + BDT 2.50 service fee)", total, DELIVERY_FEE));
        totalDue.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #FF5722;");

        Button btnConfirm = new Button("Confirm & Pay · " + String.format("BDT %.2f", total));
        btnConfirm.setMaxWidth(Double.MAX_VALUE);
        btnConfirm.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 8px; -fx-cursor: hand;");

        btnConfirm.setOnAction(e -> {
            try {
                int restId = selectedRestaurant != null ? selectedRestaurant.getId() : 1;
                Order order = orderService.placeOrder(
                        customer.getId(),
                        restId,
                        cart,
                        txtAddress.getText(),
                        paymentCombo.getValue(),
                        DELIVERY_FEE,
                        true
                );

                dialog.close();
                cart.clear();
                promoDiscount = 0.0;
                refreshCartDisplay();

                AlertUtil.showInfo("Order Placed!", "Your order #" + order.getId() + " was placed successfully!");
                showLiveOrderTracking(stage, order.getId());

            } catch (Exception ex) {
                AlertUtil.showError("Order Error", ex.getMessage());
            }
        });

        form.getChildren().addAll(
                title,
                new Label("Delivery Address:") {{
                    setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 11px;");
                }},
                txtAddress,
                new Label("Payment Method:") {{
                    setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 11px;");
                }},
                paymentCombo,
                totalDue,
                new Region() {{
                    setPrefHeight(6);
                }},
                btnConfirm
        );

        Scene s = new Scene(form);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        dialog.setScene(s);
        dialog.show();
    }

    private void showLiveOrderTracking(Stage stage, int orderId) {
        Stage trackStage = new Stage();
        trackStage.initModality(Modality.APPLICATION_MODAL);
        trackStage.setTitle("Live Tracking — Order #" + orderId);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #141417;");

        Label title = new Label("Live Delivery Tracker: Order #" + orderId);
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        // 6 Lifecycle steps
        HBox stepsBox = new HBox(8);
        stepsBox.setAlignment(Pos.CENTER);
        stepsBox.setStyle("-fx-background-color: #1C1C23; -fx-padding: 14px; -fx-background-radius: 10px;");

        String[] stages = {"PLACED", "CONFIRMED", "PREPARING", "READY", "OUT_FOR_DELIVERY", "DELIVERED"};
        Map<String, Label> stageLabels = new LinkedHashMap<>();

        for (int i = 0; i < stages.length; i++) {
            Label step = new Label((i + 1) + ". " + stages[i].replace("_", " "));
            step.setStyle("-fx-padding: 6px 10px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #272730; -fx-text-fill: #71717A;");
            stageLabels.put(stages[i], step);
            stepsBox.getChildren().add(step);
            if (i < stages.length - 1) {
                Label arrow = new Label("➔");
                arrow.setStyle("-fx-text-fill: #3F3F46;");
                stepsBox.getChildren().add(arrow);
            }
        }

        Label currentStatusBadge = new Label("STATUS: FETCHING...");
        currentStatusBadge.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #FF5722;");

        // Smart Delivery / Weather Advisory
        VBox weatherBox = new VBox(6);
        weatherBox.setStyle("-fx-background-color: #1A1F2C; -fx-border-color: #2A3B5C; -fx-border-radius: 8px; -fx-padding: 12px;");
        Label weatherTitle = new Label("🌦 Smart Delivery Intelligence (Live Weather API)");
        weatherTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #60A5FA; -fx-font-size: 12px;");

        Label weatherDetail = new Label("Fetching real-time weather advisory via background HttpClient...");
        weatherDetail.setStyle("-fx-font-size: 11px; -fx-text-fill: #93C5FD;");
        weatherDetail.setWrapText(true);
        weatherBox.getChildren().addAll(weatherTitle, weatherDetail);

        smartDeliveryService.fetchSmartDeliveryInfoAsync(info -> {
            Platform.runLater(() -> {
                weatherDetail.setText(String.format("Weather: %s (%.1f°C) | Estimated Delivery: ~%d mins\nAdvisory: %s",
                        info.getWeatherCondition(),
                        info.getTemperatureCelsius(),
                        info.getEstimatedMinutes(),
                        info.getWeatherAdvisory()));
            });
        });

        Runnable updateUI = () -> {
            Order o = orderService.getOrder(orderId);
            if (o != null) {
                currentStatusBadge.setText("CURRENT STATUS: " + o.getStatus().replace("_", " "));
                boolean passedCurrent = false;
                for (String s : stages) {
                    Label stepLbl = stageLabels.get(s);
                    if (s.equals(o.getStatus())) {
                        stepLbl.setStyle("-fx-padding: 6px 10px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #FF5722; -fx-text-fill: white;");
                        passedCurrent = true;
                    } else if (!passedCurrent) {
                        stepLbl.setStyle("-fx-padding: 6px 10px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #10B981; -fx-text-fill: white;");
                    } else {
                        stepLbl.setStyle("-fx-padding: 6px 10px; -fx-background-radius: 6px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #272730; -fx-text-fill: #71717A;");
                    }
                }
            }
        };

        com.quickbite.concurrency.OrderProcessingSimulator.getInstance().setStatusUpdateCallback((oid, newStatus) -> {
            if (oid == orderId) Platform.runLater(updateUI);
        });

        Button btnRefresh = new Button("🔄 Refresh Status");
        btnRefresh.setStyle("-fx-background-color: #272730; -fx-text-fill: white; -fx-cursor: hand;");
        btnRefresh.setOnAction(e -> updateUI.run());
        updateUI.run();

        root.getChildren().addAll(title, stepsBox, currentStatusBadge, weatherBox, btnRefresh);

        Scene s = new Scene(root, 720, 360);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        trackStage.setScene(s);
        trackStage.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. ORDER HISTORY & PROFILE MODALS
    // ─────────────────────────────────────────────────────────────────────────

    private void showOrderHistoryModal(Stage ownerStage) {
        Stage histStage = new Stage();
        histStage.initModality(Modality.APPLICATION_MODAL);
        histStage.initOwner(ownerStage);
        histStage.setTitle("QuickBite — My Past Orders & Reviews");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #111113;");

        Label title = new Label("Past Orders & Reviews");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        VBox ordersList = new VBox(12);
        ScrollPane scroll = new ScrollPane(ordersList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #111113; -fx-border-width: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        List<Order> orders = orderService.getCustomerOrders(customer.getId());

        if (orders.isEmpty()) {
            Label noOrders = new Label("You have not placed any orders yet. Explore our top restaurants to get started!");
            noOrders.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 13px;");
            ordersList.getChildren().add(noOrders);
        } else {
            for (Order o : orders) {
                VBox card = new VBox(8);
                card.setStyle("-fx-background-color: #17171C; -fx-border-color: #24242C; -fx-border-radius: 10px; -fx-padding: 14px;");

                HBox h = new HBox(8);
                h.setAlignment(Pos.CENTER_LEFT);
                Label oId = new Label("Order #" + o.getId() + " • " + o.getRestaurantName());
                oId.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Label statusBadge = new Label(o.getStatus());
                statusBadge.setStyle("-fx-background-color: #062818; -fx-text-fill: #22C55E; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-background-radius: 6px;");
                h.getChildren().addAll(oId, sp, statusBadge);

                Label dateLbl = new Label("Placed on: " + o.getCreatedAt() + " | Total: " + String.format("BDT %.2f", o.getTotalAmount()));
                dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #71717A;");

                HBox actions = new HBox(8);
                actions.setAlignment(Pos.CENTER_RIGHT);

                Button btnTrack = new Button("Track Order");
                btnTrack.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;");
                btnTrack.setOnAction(e -> showLiveOrderTracking(ownerStage, o.getId()));

                Button btnReview = new Button("★ Rate & Review");
                btnReview.setStyle("-fx-background-color: #272730; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand;");
                btnReview.setOnAction(e -> showReviewDialog(ownerStage, o));

                actions.getChildren().addAll(btnTrack, btnReview);
                card.getChildren().addAll(h, dateLbl, actions);
                ordersList.getChildren().add(card);
            }
        }

        root.getChildren().addAll(title, scroll);

        Scene s = new Scene(root, 640, 520);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        histStage.setScene(s);
        histStage.show();
    }

    private void showReviewDialog(Stage ownerStage, Order order) {
        if (reviewDAO.hasUserReviewedOrder(customer.getId(), order.getId())) {
            AlertUtil.showInfo("Already Reviewed", "You have already reviewed Order #" + order.getId() + ".");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Rate & Review — Order #" + order.getId());

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #141417;");

        Label title = new Label("Review: " + order.getRestaurantName());
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        ComboBox<Integer> ratingCombo = new ComboBox<>();
        ratingCombo.getItems().addAll(5, 4, 3, 2, 1);
        ratingCombo.setValue(5);
        ratingCombo.setStyle("-fx-background-color: #1E1E26;");

        TextArea commentArea = new TextArea();
        commentArea.setPromptText("How was the food taste, delivery speed, and packaging?");
        commentArea.setPrefRowCount(4);
        commentArea.setStyle("-fx-control-inner-background: #1E1E26; -fx-text-fill: white;");

        Button btnSubmit = new Button("Submit Review");
        btnSubmit.setMaxWidth(Double.MAX_VALUE);
        btnSubmit.setStyle("-fx-background-color: #FF5722; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10px; -fx-cursor: hand;");
        btnSubmit.setOnAction(e -> {
            Review r = new Review();
            r.setUserId(customer.getId());
            r.setRestaurantId(order.getRestaurantId());
            r.setOrderId(order.getId());
            r.setRating(ratingCombo.getValue());
            r.setComment(commentArea.getText());

            if (reviewDAO.create(r)) {
                AlertUtil.showInfo("Review Published", "Thank you! Your review for " + order.getRestaurantName() + " has been posted.");
                dialog.close();
            } else {
                AlertUtil.showError("Error", "Could not submit review.");
            }
        });

        root.getChildren().addAll(title, new Label("Rating:") {{
            setStyle("-fx-text-fill: #9CA3AF;");
        }}, ratingCombo, new Label("Feedback:") {{
            setStyle("-fx-text-fill: #9CA3AF;");
        }}, commentArea, btnSubmit);

        Scene s = new Scene(root, 400, 320);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        dialog.setScene(s);
        dialog.show();
    }

    /**
     * Clicking a Recent Orders card re-adds that order's real line items into the current cart.
     */
    private void reorderFromOrder(Stage stage, Order order) {
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            AlertUtil.showWarning("No Items", "This order has no items to reorder.");
            return;
        }

        int addedCount = 0;
        int skippedCount = 0;
        for (OrderItem oi : items) {
            FoodItem fi = foodItemDAO.getById(oi.getFoodId());
            if (fi != null) {
                addToCartSilently(fi, oi.getQuantity());
                addedCount++;
            } else {
                skippedCount++;
            }
        }

        if (addedCount == 0) {
            AlertUtil.showWarning("Unavailable", "None of the items from this order are available anymore.");
            return;
        }

        // Keep the cart's "source restaurant" label consistent with what was just added
        Restaurant r = restaurantDAO.getById(order.getRestaurantId());
        if (r != null) {
            selectedRestaurant = r;
        }
        String restName = order.getRestaurantName() != null ? order.getRestaurantName() : "Restaurant #" + order.getRestaurantId();
        if (lblCartSource != null) {
            lblCartSource.setText("From " + restName);
        }

        refreshCartDisplay();

        String msg = "Items from Order #" + order.getId() + " were added to your cart!" +
                (skippedCount > 0 ? " (" + skippedCount + " item(s) no longer available)" : "");
        AlertUtil.showInfo("Added to Cart", msg);
    }

    private void showExploreDishesModal(Stage stage) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Explore All Cuisines & Dishes");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #111113;");

        Label title = new Label("Explore All Dishes Across Top Restaurants");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        FlowPane grid = new FlowPane();
        grid.setHgap(14);
        grid.setVgap(14);

        for (Restaurant r : restaurantDAO.getAll()) {
            for (FoodItem fi : menuService.getFoodItems(r.getId())) {
                grid.getChildren().add(buildFoodItemCard(fi, modal));
            }
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #111113; -fx-border-width: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(title, scroll);

        Scene s = new Scene(root, 820, 600);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        modal.setScene(s);
        modal.show();
    }

    private void showProfileModal(Stage stage) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Customer Profile — QuickBite");

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #141417;");
        root.setPrefWidth(380);

        Label title = new Label("Customer Profile");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label nameLbl = new Label("Name: " + customer.getName());
        nameLbl.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 13px;");

        Label emailLbl = new Label("Email: " + customer.getEmail());
        emailLbl.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 13px;");

        Label phoneLbl = new Label("Phone: " + (customer.getPhone() != null ? customer.getPhone() : "+1 555-0101"));
        phoneLbl.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 13px;");

        Label addrLbl = new Label("Delivery Address: " + (customer.getAddress() != null ? customer.getAddress() : "W 72nd St, New York"));
        addrLbl.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 13px;");
        Button btnClose = new Button("Close");
        btnClose.setMaxWidth(Double.MAX_VALUE);
        btnClose.setStyle("-fx-background-color: #272730; -fx-text-fill: white; -fx-padding: 8px; -fx-cursor: hand;");
        btnClose.setOnAction(e -> modal.close());

        root.getChildren().addAll(title, nameLbl, emailLbl, phoneLbl, addrLbl, btnClose);

        Scene s = new Scene(root);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        modal.setScene(s);
        modal.show();
    }

    private void showAddressChangeDialog() {
        TextInputDialog tid = new TextInputDialog(customer.getAddress() != null ? customer.getAddress() : "W 72nd St, New York");
        tid.setTitle("Change Delivery Address");
        tid.setHeaderText(null);
        tid.setContentText("Enter your delivery address:");
        tid.showAndWait().ifPresent(newAddr -> {
            customer.setAddress(newAddr);
            AlertUtil.showInfo("Address Updated", "Delivery destination updated to: " + newAddr);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. DISH OF THE DAY POPUP (dynamic — never hardcoded)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches today's Dish of the Day from {@link DishOfTheDayService} — which
     * selects a real, currently-available menu item straight from the database,
     * deterministically rotated by today's calendar date, so it is never a fixed
     * dish name baked into the code — and shows it in a dismissible popup.
     * Clicking "Add to Cart" adds that exact dish to the customer's cart at its
     * discounted "today only" price.
     */
    private void showDishOfTheDayPopup(Stage ownerStage) {
        DishOfTheDay dod = dishOfTheDayService.getDishOfTheDay();
        if (dod == null) {
            return; // No available menu items anywhere yet — nothing to promote today.
        }

        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(ownerStage);
        modal.setTitle("Dish of the Day");

        VBox root = new VBox(14);
        root.setPadding(new Insets(22));
        root.setStyle("-fx-background-color: #141417; -fx-background-radius: 14px;");
        root.setPrefWidth(360);

        HBox badgeRow = new HBox();
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label("🌟 DISH OF THE DAY");
        badge.setStyle(
                "-fx-background-color: #FF5722;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 5px 10px;" +
                        "-fx-background-radius: 6px;"
        );
        badgeRow.getChildren().add(badge);

        // Dish image — reuses the same classpath / file-path fallback strategy as the menu cards
        StackPane imageBox = new StackPane();
        imageBox.setPrefSize(316, 160);
        ImageView imgView = new ImageView();
        imgView.setFitWidth(316);
        imgView.setFitHeight(160);
        imgView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(316, 160);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        imgView.setClip(clip);

        boolean loaded = loadDishOfTheDayImage(imgView, dod.getFoodItem().getImageUrl());
        if (loaded) {
            imageBox.getChildren().add(imgView);
        } else {
            imageBox.setStyle("-fx-background-color: #24242D; -fx-background-radius: 12px;");
            Label icon = new Label(getRestaurantEmoji(dod.getFoodItem().getName()));
            icon.setStyle("-fx-font-size: 40px;");
            imageBox.getChildren().add(icon);
        }

        Label nameLbl = new Label(dod.getFoodItem().getName());
        nameLbl.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: white;");
        nameLbl.setWrapText(true);

        Label restLbl = new Label("From " + dod.getRestaurantName());
        restLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");

        Label descLbl = new Label(dod.getFoodItem().getDescription());
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #D4D4D8;");
        descLbl.setWrapText(true);

        HBox priceRow = new HBox(10);
        priceRow.setAlignment(Pos.CENTER_LEFT);

        Label origPrice = new Label(String.format("BDT %.2f", dod.getFoodItem().getPrice()));
        origPrice.setStyle("-fx-font-size: 13px; -fx-text-fill: #71717A; -fx-strikethrough: true;");

        Label discPrice = new Label(String.format("BDT %.2f", dod.getDiscountedPrice()));
        discPrice.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #FF5722;");

        Label discBadge = new Label(dod.getDiscountPercent() + "% OFF TODAY");
        discBadge.setStyle(
                "-fx-background-color: #062818;" +
                        "-fx-text-fill: #22C55E;" +
                        "-fx-font-size: 10px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 3px 8px;" +
                        "-fx-background-radius: 6px;"
        );

        priceRow.getChildren().addAll(origPrice, discPrice, discBadge);

        Button btnAdd = new Button("🛒 Add to Cart at This Price");
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnAdd.setStyle(
                "-fx-background-color: #FF5722;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 11px;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-cursor: hand;"
        );
        btnAdd.setOnAction(e -> {
            addDishOfTheDayToCart(dod);
            modal.close();
            AlertUtil.showInfo(
                    "Added to Cart!",
                    dod.getFoodItem().getName() + " was added to your cart at today's special price of BDT "
                            + String.format("%.2f", dod.getDiscountedPrice()) + "!"
            );
        });

        Button btnDismiss = new Button("Maybe later");
        btnDismiss.setMaxWidth(Double.MAX_VALUE);
        btnDismiss.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #9CA3AF;" +
                        "-fx-font-size: 12px;" +
                        "-fx-cursor: hand;"
        );
        btnDismiss.setOnAction(e -> modal.close());

        root.getChildren().addAll(badgeRow, imageBox, nameLbl, restLbl, descLbl, priceRow, btnAdd, btnDismiss);

        Scene s = new Scene(root);
        try {
            s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {
        }
        modal.setScene(s);
        modal.show();
    }

    /**
     * Adds the Dish of the Day's exact {@link FoodItem} to the cart, switching the
     * cart's "source restaurant" to match — the same convention already used by
     * {@link #reorderFromOrder(Stage, Order)}.
     */
    private void addDishOfTheDayToCart(DishOfTheDay dod) {
        Restaurant r = restaurantDAO.getById(dod.getRestaurantId());
        if (r != null) {
            selectedRestaurant = r;
            if (lblCartSource != null) {
                lblCartSource.setText("From " + r.getName());
            }
        }
        addToCartSilently(dod.getFoodItem(), 1);
        refreshCartDisplay();
    }

    /**
     * Loads a dish image the same way the menu cards do: classpath resource,
     * then project images/ folder, then a direct file path. Returns true if an
     * image was successfully loaded into the given ImageView.
     */
    private boolean loadDishOfTheDayImage(ImageView view, String path) {
        if (path == null || path.isBlank()) return false;

        try {
            var stream = getClass().getResourceAsStream("/images/" + path);
            if (stream != null) {
                view.setImage(new Image(stream, 316, 160, false, true));
                stream.close();
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            File f = new File("images/" + path);
            if (f.exists()) {
                view.setImage(new Image(f.toURI().toString(), 316, 160, false, true, true));
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            File f = new File(path);
            if (f.exists()) {
                view.setImage(new Image(f.toURI().toString(), 316, 160, false, true, true));
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }
}