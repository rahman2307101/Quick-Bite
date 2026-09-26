package com.quickbite.controller;

import com.quickbite.concurrency.DeliveryDriverPool;
import com.quickbite.model.Delivery;
import com.quickbite.model.DeliveryStaff;
import com.quickbite.service.AuthService;
import com.quickbite.service.DeliveryService;
import com.quickbite.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * Modern JavaFX Delivery Staff Dashboard View.
 * Handles viewing assigned tasks, advancing delivery status (Picked Up -> Delivered),
 * releasing drivers back into the synchronized shared pool, and reviewing delivery history.
 */
public class DeliveryDashboardView {
    private final DeliveryStaff staff;
    private final DeliveryService deliveryService = new DeliveryService();
    private final DeliveryDriverPool driverPool = DeliveryDriverPool.getInstance();

    private TableView<Delivery> activeTable;
    private TableView<Delivery> historyTable;
    private Label lblDriverStatus;
    private Label lblPoolCount;

    public DeliveryDashboardView(DeliveryStaff staff) {
        this.staff = staff;
    }

    public void show(Stage stage) {
        stage.setTitle("QuickBite - Delivery Partner (" + staff.getName() + ")");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F8FAFC;");

        // Top Navigation Bar
        root.setTop(createNavBar(stage));

        // Center Content: Status Banner + Tabs (Active vs History)
        VBox centerBox = new VBox(16);
        centerBox.setPadding(new Insets(16));

        // Status Card
        HBox statusCard = createStatusBanner();
        centerBox.getChildren().add(statusCard);

        // TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Tab activeTab = new Tab("🛵 Assigned & In-Progress Deliveries");
        activeTab.setContent(createActiveDeliveriesTab());

        Tab historyTab = new Tab("📜 Completed Delivery History");
        historyTab.setContent(createHistoryTab());

        tabPane.getTabs().addAll(activeTab, historyTab);
        centerBox.getChildren().add(tabPane);

        root.setCenter(centerBox);

        refreshData();

        Scene scene = new Scene(root, 980, 680);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}
        stage.setScene(scene);
        stage.show();
    }

    private HBox createNavBar(Stage stage) {
        HBox nav = new HBox(16);
        nav.setStyle("-fx-background-color: #1E293B; -fx-padding: 12px 20px; -fx-alignment: CENTER_LEFT;");

        Label brand = new Label("QuickBite Courier Dispatch");
        brand.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #FF6B00;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Driver: " + staff.getName() + " (" + staff.getVehicleType() + ")");
        userLabel.setStyle("-fx-text-fill: #F1F5F9; -fx-font-weight: bold;");

        Label roleBadge = new Label("Delivery Staff");
        roleBadge.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-padding: 3px 8px; -fx-background-radius: 12px; -fx-font-size: 11px;");

        Button btnLogout = new Button("Logout");
        btnLogout.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-font-size: 12px;");
        btnLogout.setOnAction(e -> {
            new AuthService().logout();
            new LoginView().show(stage);
        });

        nav.getChildren().addAll(brand, spacer, userLabel, roleBadge, btnLogout);
        return nav;
    }

    private HBox createStatusBanner() {
        HBox banner = new HBox(20);
        banner.setPadding(new Insets(14));
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.03), 4, 0, 0, 1);");

        lblDriverStatus = new Label("Status: Available for Dispatch");
        lblDriverStatus.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #059669;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        lblPoolCount = new Label("Synchronized Pool: Checking...");
        lblPoolCount.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        banner.getChildren().addAll(lblDriverStatus, sp, lblPoolCount);
        return banner;
    }

    private VBox createActiveDeliveriesTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16, 0, 0, 0));

        activeTable = new TableView<>();
        activeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Delivery, String> colId = new TableColumn<>("Delivery #");
        colId.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getId()));
        colId.setMaxWidth(90);

        TableColumn<Delivery, String> colOrder = new TableColumn<>("Order #");
        colOrder.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getOrderId()));
        colOrder.setMaxWidth(80);

        TableColumn<Delivery, String> colRest = new TableColumn<>("Pickup Restaurant");
        colRest.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRestaurantName()));

        TableColumn<Delivery, String> colCust = new TableColumn<>("Customer");
        colCust.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCustomerName()));

        TableColumn<Delivery, String> colAddress = new TableColumn<>("Drop-off Address");
        colAddress.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDeliveryAddress()));

        TableColumn<Delivery, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        colStatus.setMaxWidth(130);

        activeTable.getColumns().addAll(colId, colOrder, colRest, colCust, colAddress, colStatus);
        VBox.setVgrow(activeTable, Priority.ALWAYS);

        // Actions
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button btnPickup = new Button("🛵 Picked Up from Kitchen (Out for Delivery)");
        btnPickup.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold;");
        btnPickup.setOnAction(e -> {
            Delivery sel = activeTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                deliveryService.markPickedUp(sel.getId(), sel.getOrderId());
                refreshData();
            } else {
                AlertUtil.showWarning("Select Delivery", "Please select a delivery from the table.");
            }
        });

        Button btnDeliver = new Button("✔ Mark Delivered (Complete & Release Driver)");
        btnDeliver.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold;");
        btnDeliver.setOnAction(e -> {
            Delivery sel = activeTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                deliveryService.markDelivered(sel.getId(), sel.getOrderId(), staff.getId());
                AlertUtil.showInfo("Delivery Completed", "Order #" + sel.getOrderId() + " marked as DELIVERED!\nYou have returned to the available driver pool.");
                refreshData();
            } else {
                AlertUtil.showWarning("Select Delivery", "Please select a delivery from the table.");
            }
        });

        Button btnRefresh = new Button("🔄 Refresh");
        btnRefresh.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #0F172A;");
        btnRefresh.setOnAction(e -> refreshData());

        actionsRow.getChildren().addAll(btnPickup, btnDeliver, btnRefresh);

        box.getChildren().addAll(activeTable, actionsRow);
        return box;
    }

    private VBox createHistoryTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16, 0, 0, 0));

        historyTable = new TableView<>();
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Delivery, String> colId = new TableColumn<>("Delivery #");
        colId.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getId()));
        colId.setMaxWidth(90);

        TableColumn<Delivery, String> colOrder = new TableColumn<>("Order #");
        colOrder.setCellValueFactory(data -> new SimpleStringProperty("#" + data.getValue().getOrderId()));
        colOrder.setMaxWidth(80);

        TableColumn<Delivery, String> colRest = new TableColumn<>("Restaurant");
        colRest.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRestaurantName()));

        TableColumn<Delivery, String> colAddress = new TableColumn<>("Delivered To");
        colAddress.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDeliveryAddress()));

        TableColumn<Delivery, String> colTime = new TableColumn<>("Delivered At");
        colTime.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDeliveredAt() != null ? data.getValue().getDeliveredAt() : "N/A"));

        TableColumn<Delivery, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        colStatus.setMaxWidth(110);

        historyTable.getColumns().addAll(colId, colOrder, colRest, colAddress, colTime, colStatus);
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        box.getChildren().add(historyTable);
        return box;
    }

    private void refreshData() {
        List<Delivery> allStaffDeliveries = deliveryService.getStaffDeliveries(staff.getId());

        List<Delivery> active = allStaffDeliveries.stream()
                .filter(d -> !Delivery.STATUS_DELIVERED.equals(d.getStatus()) && !Delivery.STATUS_CANCELLED.equals(d.getStatus()))
                .toList();

        List<Delivery> history = allStaffDeliveries.stream()
                .filter(d -> Delivery.STATUS_DELIVERED.equals(d.getStatus()) || Delivery.STATUS_CANCELLED.equals(d.getStatus()))
                .toList();

        activeTable.getItems().setAll(active);
        historyTable.getItems().setAll(history);

        if (active.isEmpty()) {
            lblDriverStatus.setText("Status: Available for Next Dispatch");
            lblDriverStatus.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #059669;");
        } else {
            lblDriverStatus.setText("Status: On Active Delivery Duty (" + active.size() + " orders)");
            lblDriverStatus.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #D97706;");
        }

        lblPoolCount.setText("Driver Pool: " + driverPool.getAvailableCount() + " available | " +
                driverPool.getBusyCount() + " busy | " + driverPool.getQueuedCount() + " order(s) queued");
    }
}