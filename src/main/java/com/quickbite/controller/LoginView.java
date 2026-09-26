package com.quickbite.controller;

import com.quickbite.model.User;
import com.quickbite.service.AuthService;
import com.quickbite.util.AlertUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * QuickBite Login View — matches Figma dark split-panel design.
 * Left panel: branding, stats, testimonial.
 * Right panel: role-selector cards, sign-in form, social login buttons.
 * Demonstrates OOP polymorphism: user.showDashboard() routes to the correct dashboard subclass.
 *
 * Role enforcement: the person must select the role card matching their account
 * (Customer / Restaurant / Rider) before signing in — AuthService.login(email, password, expectedRole)
 * rejects the attempt if the account's actual role doesn't match the selected card.
 */
public class LoginView {

    private final AuthService authService = new AuthService();

    // Tracks which role card is currently selected (display label + internal role key)
    private String selectedRole = "Customer";
    private String selectedRoleKey = "CUSTOMER";

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    public void show(Stage stage) {
        stage.setTitle("QuickBite — Food Ordering & Delivery Management System");

        HBox root = new HBox();
        root.setStyle("-fx-background-color: #0D0D0D;");

        VBox leftPanel = buildLeftPanel();
        leftPanel.setPrefWidth(300);
        leftPanel.setMinWidth(300);
        leftPanel.setMaxWidth(300);

        VBox rightPanel = buildRightPanel(stage);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        root.getChildren().addAll(leftPanel, rightPanel);

        Scene scene = new Scene(root, 960, 660);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEFT PANEL — branding, stats, testimonial
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildLeftPanel() {
        VBox panel = new VBox();
        panel.setStyle("-fx-background-color: #141414;");
        panel.setPadding(new Insets(32, 28, 32, 28));

        // ── Logo row ──────────────────────────────────────────────────────────
        HBox logoRow = new HBox(10);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        Label logoIcon = new Label("⚡");
        logoIcon.setStyle(
                "-fx-font-size: 16px;" +
                        "-fx-background-color: #FF6B00;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-padding: 6px 9px;"
        );

        Label logoText = new Label("Quick Bite");
        logoText.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;");
        logoRow.getChildren().addAll(logoIcon, logoText);

        // ── Top flexible spacer ───────────────────────────────────────────────
        Region topSpacer = new Region();
        VBox.setVgrow(topSpacer, Priority.ALWAYS);

        // ── Tagline ───────────────────────────────────────────────────────────
        VBox taglineBox = new VBox(4);

        Label line1 = new Label("Good food");
        line1.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-style: italic;");

        Label line2 = new Label("good mood");
        line2.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #FF6B00; -fx-font-style: italic;");

        Region taglineSpacer = new Region();
        taglineSpacer.setPrefHeight(10);

        Label subtitle = new Label(
                "A single platform connecting hungry customers,\n" +
                        "restaurant partners, and delivery riders."
        );
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #777777; -fx-line-spacing: 3;");
        subtitle.setWrapText(true);

        taglineBox.getChildren().addAll(line1, line2, taglineSpacer, subtitle);

        // ── Stats row ─────────────────────────────────────────────────────────
        HBox statsRow = new HBox(22);
        statsRow.setPadding(new Insets(22, 0, 0, 0));
        statsRow.getChildren().addAll(
                statBlock("03",   "Restaurants"),
                statBlock("98%",    "On-time rate"),
                statBlock("18 min", "Avg. delivery")
        );

        // ── Bottom flexible spacer ────────────────────────────────────────────
        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        // ── Testimonial card ──────────────────────────────────────────────────
        VBox testimonial = new VBox(12);
        testimonial.setStyle(
                "-fx-background-color: #1E1E1E;" +
                        "-fx-background-radius: 10px;" +
                        "-fx-padding: 16px;"
        );

        Label quote = new Label(
                "\"Delicious food, fast delivery, and a seamless ordering experience. I never have to worry about dinner anymore!”\n"
        );
        quote.setStyle("-fx-font-size: 11px; -fx-text-fill: #CCCCCC; -fx-line-spacing: 3; -fx-font-style: italic;");
        quote.setWrapText(true);

        HBox authorRow = new HBox(10);
        authorRow.setAlignment(Pos.CENTER_LEFT);

        Label avatar = new Label("S");
        avatar.setStyle(
                "-fx-background-color: #FF6B00;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-min-width: 34px;" +
                        "-fx-min-height: 34px;" +
                        "-fx-background-radius: 17px;" +
                        "-fx-alignment: center;"
        );

        VBox authorInfo = new VBox(2);
        Label authorName = new Label("Sadia Jahan Turabe");
        authorName.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label authorTitle = new Label("Verified Customer");
        authorTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #777777;");
        authorInfo.getChildren().addAll(authorName, authorTitle);

        authorRow.getChildren().addAll(avatar, authorInfo);
        testimonial.getChildren().addAll(quote, authorRow);

        panel.getChildren().addAll(logoRow, topSpacer, taglineBox, statsRow, bottomSpacer, testimonial);
        return panel;
    }

    private VBox statBlock(String value, String label) {
        VBox b = new VBox(3);
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #777777;");
        b.getChildren().addAll(val, lbl);
        return b;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RIGHT PANEL — role selector + sign-in form
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildRightPanel(Stage stage) {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: #0D0D0D;");
        panel.setPadding(new Insets(40, 64, 40, 64));

        // Inner content column (max width = 420)
        VBox container = new VBox(16);
        container.setMaxWidth(420);
        container.setAlignment(Pos.TOP_LEFT);

        // ── Header ────────────────────────────────────────────────────────────
        Label welcomeLabel = new Label("Welcome back");
        welcomeLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subLabel = new Label("Select your role, then sign in to continue.");
        subLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #777777;");

        // ── Role cards ────────────────────────────────────────────────────────
        String[]   roles     = { "Customer",                    "Restaurant",              "Rider"                   };
        String[]   roleKeys  = { "CUSTOMER",                    "RESTAURANT_ADMIN",        "DELIVERY_STAFF"          };
        String[]   icons     = { "🛒",                          "🏠",                      "🕐"                      };
        String[]   descs     = { "Order & Track your food", " Manage orders & menu",    " Pick up & deliver orders" };

        VBox[]  roleCards = new VBox[3];

        // Mutable references for lambdas
        Label[]  signingBarRef = new Label[1];
        Button[] signInBtnRef  = new Button[1];

        HBox roleRow = new HBox(10);
        roleRow.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < 3; i++) {
            final int idx = i;

            VBox card = new VBox(8);
            card.setAlignment(Pos.CENTER);
            card.setPrefWidth(128);
            card.setPrefHeight(94);
            card.setCursor(Cursor.HAND);
            applyRoleCardStyle(card, idx == 0); // Customer selected by default

            Label iconLbl = new Label(icons[i]);
            iconLbl.setStyle("-fx-font-size: 22px;");

            Label nameLbl = new Label(roles[i]);
            nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");

            Label descLbl = new Label(descs[i]);
            descLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: #777777; -fx-text-alignment: center;");
            descLbl.setWrapText(true);
            descLbl.setMaxWidth(116);

            card.getChildren().addAll(iconLbl, nameLbl, descLbl);
            roleCards[i] = card;

            card.setOnMouseClicked(e -> {
                selectedRole = roles[idx];
                selectedRoleKey = roleKeys[idx];
                for (int j = 0; j < 3; j++) {
                    applyRoleCardStyle(roleCards[j], j == idx);
                }
                if (signingBarRef[0] != null) {
                    signingBarRef[0].setText("● Signing in as  " + selectedRole);
                }
                if (signInBtnRef[0] != null) {
                    signInBtnRef[0].setText("Sign in as " + selectedRole);
                }
            });

            roleRow.getChildren().add(card);
        }

        // ── "Signing in as" bar ───────────────────────────────────────────────
        Label signingBar = new Label("● Signing in as  Customer");
        signingBar.setMaxWidth(Double.MAX_VALUE);
        signingBar.setStyle(
                "-fx-background-color: #2A1200;" +
                        "-fx-text-fill: #FF6B00;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 8px 14px;" +
                        "-fx-background-radius: 6px;"
        );
        signingBarRef[0] = signingBar;

        // ── Email field ───────────────────────────────────────────────────────
        Label emailLabel = new Label("Email address");
        emailLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #AAAAAA;");

        TextField txtEmail = new TextField();
        txtEmail.setPromptText("you@example.com");
        styleInput(txtEmail);

        // ── Password row ──────────────────────────────────────────────────────
        HBox passLabelRow = new HBox();
        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #AAAAAA;");
        Region lblSpacer = new Region();
        HBox.setHgrow(lblSpacer, Priority.ALWAYS);
        Label forgotLabel = new Label("Forgot password?");
        forgotLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #FF6B00; -fx-cursor: hand;");
        forgotLabel.setOnMouseClicked(e ->
                AlertUtil.showInfo("Forgot Password", "Please contact support or re-register with a new account.")
        );
        passLabelRow.getChildren().addAll(passLabel, lblSpacer, forgotLabel);

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("••••••••");
        styleInput(txtPassword);

        // ── Keep me signed in ─────────────────────────────────────────────────
        CheckBox keepSignedIn = new CheckBox("Keep me signed in for 30 days");
        keepSignedIn.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        // ── Sign in button ────────────────────────────────────────────────────
        Button btnSignIn = new Button("Sign in as Customer");
        btnSignIn.setMaxWidth(Double.MAX_VALUE);
        btnSignIn.setStyle(
                "-fx-background-color: #FF6B00;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 13px;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-cursor: hand;"
        );
        signInBtnRef[0] = btnSignIn;

        btnSignIn.setOnAction(e -> {
            try {
                // Role-checked login: fails if the account's real role doesn't
                // match the currently selected role card (selectedRoleKey).
                User user = authService.login(
                        txtEmail.getText().trim(),
                        txtPassword.getText(),
                        selectedRoleKey
                );
                // OOP Polymorphism: routes to Customer / RestaurantAdmin / DeliveryStaff dashboard
                user.showDashboard(stage);
            } catch (Exception ex) {
                AlertUtil.showError("Login Error", ex.getMessage());
            }
        });

        // ── Create account link ───────────────────────────────────────────────
        HBox createRow = new HBox(4);
        createRow.setAlignment(Pos.CENTER);
        Label noAccount = new Label("Don't have an account?");
        noAccount.setStyle("-fx-font-size: 11px; -fx-text-fill: #777777;");
        Label createLink = new Label("Create one");
        createLink.setStyle("-fx-font-size: 11px; -fx-text-fill: #FF6B00; -fx-cursor: hand; -fx-underline: true;");
        createLink.setOnMouseClicked(e -> showRegisterDialog(stage));
        createRow.getChildren().addAll(noAccount, createLink);

        // ── Divider ───────────────────────────────────────────────────────────
        HBox divider = new HBox(10);
        divider.setAlignment(Pos.CENTER);
        Separator sep1 = new Separator();
        HBox.setHgrow(sep1, Priority.ALWAYS);
        Label orLabel = new Label("or continue with");
        orLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #444444;");
        Separator sep2 = new Separator();
        HBox.setHgrow(sep2, Priority.ALWAYS);
        divider.getChildren().addAll(sep1, orLabel, sep2);

        // ── Social buttons ────────────────────────────────────────────────────
        HBox socialRow = new HBox(12);
        socialRow.setAlignment(Pos.CENTER);
        Button btnGoogle = socialBtn("🌐  Google");
        Button btnApple  = socialBtn("🍎  Apple");
        btnGoogle.setPrefWidth(188);
        btnApple.setPrefWidth(188);
        btnGoogle.setOnAction(e -> AlertUtil.showInfo("Google Login", "Future updates will ensure social login :("));
        btnApple.setOnAction(e  -> AlertUtil.showInfo("Apple Login",  "Future updates will ensure social login :("));
        socialRow.getChildren().addAll(btnGoogle, btnApple);

        // ── Assemble container ────────────────────────────────────────────────
        container.getChildren().addAll(
                welcomeLabel,
                subLabel,
                roleRow,
                signingBar,
                emailLabel,
                txtEmail,
                passLabelRow,
                txtPassword,
                keepSignedIn,
                btnSignIn,
                createRow,
                divider,
                socialRow
        );

        panel.getChildren().add(container);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Register dialog (opened from "Create one" link)
    // ─────────────────────────────────────────────────────────────────────────

    private void showRegisterDialog(Stage ownerStage) {
        Stage dialog = new Stage();
        dialog.initOwner(ownerStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Create Account");

        VBox form = new VBox(10);
        form.setPadding(new Insets(28));
        form.setStyle("-fx-background-color: #141414;");
        form.setPrefWidth(380);

        Label title = new Label("Create Account");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subLbl = new Label("Join QuickBite today");
        subLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #777777;");

        TextField regName     = new TextField(); regName.setPromptText("Full Name");              styleInput(regName);
        TextField regEmail    = new TextField(); regEmail.setPromptText("Email Address");          styleInput(regEmail);
        PasswordField regPass = new PasswordField(); regPass.setPromptText("Password (min 8 chars)"); styleInput(regPass);
        TextField regPhone    = new TextField(); regPhone.setPromptText("Phone Number");           styleInput(regPhone);
        TextField regAddress  = new TextField(); regAddress.setPromptText("Delivery Address");     styleInput(regAddress);

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("Customer", "Restaurant Admin", "Delivery Staff");
        roleCombo.setValue("Customer");
        roleCombo.setMaxWidth(Double.MAX_VALUE);
        roleCombo.setStyle(
                "-fx-background-color: #1A1A1A;" +
                        "-fx-border-color: #333333;" +
                        "-fx-border-radius: 8px;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-font-size: 13px;"
        );

        Button btnRegister = new Button("Create Account");
        btnRegister.setMaxWidth(Double.MAX_VALUE);
        btnRegister.setStyle(
                "-fx-background-color: #FF6B00;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 11px;" +
                        "-fx-background-radius: 8px;"
        );

        btnRegister.setOnAction(e -> {
            try {
                String roleKey = "CUSTOMER";
                if ("Restaurant Admin".equals(roleCombo.getValue())) roleKey = "RESTAURANT_ADMIN";
                else if ("Delivery Staff".equals(roleCombo.getValue()))   roleKey = "DELIVERY_STAFF";

                User newUser = authService.register(
                        regName.getText().trim(),
                        regEmail.getText().trim(),
                        regPass.getText(),
                        regPhone.getText().trim(),
                        regAddress.getText().trim(),
                        roleKey
                );
                AlertUtil.showInfo("Welcome!", "Account created! Welcome, " + newUser.getName() + ".");
                dialog.close();
                newUser.showDashboard(ownerStage);
            } catch (Exception ex) {
                AlertUtil.showError("Registration Error", ex.getMessage());
            }
        });

        // Labels for each field
        form.getChildren().addAll(
                title, subLbl, new Region() {{ setPrefHeight(8); }},
                fieldLabel("Full Name"),    regName,
                fieldLabel("Email"),        regEmail,
                fieldLabel("Password"),     regPass,
                fieldLabel("Phone"),        regPhone,
                fieldLabel("Address"),      regAddress,
                fieldLabel("Role"),         roleCombo,
                new Region() {{ setPrefHeight(8); }},
                btnRegister
        );

        Scene s = new Scene(form);
        dialog.setScene(s);
        dialog.setResizable(false);
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Applies active (orange border) or inactive style to a role card. */
    private void applyRoleCardStyle(VBox card, boolean active) {
        if (active) {
            card.setStyle(
                    "-fx-background-color: #1E0900;" +
                            "-fx-background-radius: 10px;" +
                            "-fx-border-color: #FF6B00;" +
                            "-fx-border-radius: 10px;" +
                            "-fx-border-width: 1.8px;" +
                            "-fx-padding: 10px;"
            );
        } else {
            card.setStyle(
                    "-fx-background-color: #1A1A1A;" +
                            "-fx-background-radius: 10px;" +
                            "-fx-border-color: #2E2E2E;" +
                            "-fx-border-radius: 10px;" +
                            "-fx-border-width: 1px;" +
                            "-fx-padding: 10px;"
            );
        }
    }

    /** Applies dark input field styling. */
    private void styleInput(TextField field) {
        field.setStyle(
                "-fx-background-color: #1A1A1A;" +
                        "-fx-text-fill: white;" +
                        "-fx-prompt-text-fill: #555555;" +
                        "-fx-border-color: #2E2E2E;" +
                        "-fx-border-radius: 8px;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-padding: 10px 13px;" +
                        "-fx-font-size: 13px;"
        );
        field.setMaxWidth(Double.MAX_VALUE);
    }

    /** Creates a dark social-login button (Google / Apple). */
    private Button socialBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: #1A1A1A;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 12px;" +
                        "-fx-padding: 10px 16px;" +
                        "-fx-background-radius: 8px;" +
                        "-fx-border-color: #2E2E2E;" +
                        "-fx-border-radius: 8px;" +
                        "-fx-cursor: hand;"
        );
        return btn;
    }

    /** Tiny helper to create a styled field label for the register dialog. */
    private Label fieldLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #AAAAAA;");
        return lbl;
    }
}