package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AuthService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Administrator Staff Management screen.
 */
public class StaffManagementController {

    private static final Logger LOGGER = AppLogger.getLogger(StaffManagementController.class);

    @FXML private TableView<User> staffTable;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private TableColumn<User, Void> colActions;

    private final AuthService authService;
    private final ObservableList<User> tableData = FXCollections.observableArrayList();

    public StaffManagementController() {
        this.authService = new AuthService(new MySQLUserDAO());
    }

    public StaffManagementController(AuthService authService) {
        this.authService = authService;
    }

    @FXML
    public void initialize() {
        SessionContext.requireRole(Role.ADMIN);
        setupTableColumns();
        loadStaff();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        colEmail.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEmail()));
        colRole.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRole().getDisplayName()));

        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isActive() ? "Active" : "Deactivated"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("Active".equals(item)) {
                        badge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-padding: 2px 8px; -fx-background-radius: 8px; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else {
                        badge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #B91C1C; -fx-padding: 2px 8px; -fx-background-radius: 8px; -fx-font-size: 11px; -fx-font-weight: bold;");
                    }
                    setGraphic(badge);
                }
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnToggle = new Button();
            private final HBox pane = new HBox(6, btnToggle);

            {
                btnToggle.getStyleClass().add("btn-secondary");
                btnToggle.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    toggleStatus(u);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User u = getTableView().getItems().get(getIndex());
                    btnToggle.setText(u.isActive() ? "Deactivate" : "Activate");
                    setGraphic(pane);
                }
            }
        });

        staffTable.setItems(tableData);
    }

    public void loadStaff() {
        try {
            List<User> staff = authService.getAllStaff();
            tableData.setAll(staff);
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.SEVERE, "Failed to load staff list", e);
            showAlert("Failed to load staff members: " + e.getMessage());
        }
    }

    private void toggleStatus(User user) {
        try {
            if (user.isActive()) {
                authService.deactivateStaff(user.getId());
            } else {
                authService.activateStaff(user.getId());
            }
            loadStaff();
        } catch (ClinicDeskException e) {
            showAlert("Status change failed: " + e.getMessage());
        }
    }

    @FXML
    public void handleAddStaff(ActionEvent event) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Staff Account");
        dialog.setHeaderText("Enter staff credentials and assign an application role.");

        ButtonType createBtnType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Dr. Jane Smith");

        TextField emailField = new TextField();
        emailField.setPromptText("e.g. jsmith@clinicdesk.local");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Initial password");

        ComboBox<Role> roleCombo = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        roleCombo.setValue(Role.DOCTOR);

        grid.add(new Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(new Label("Role:"), 0, 3);
        grid.add(roleCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == createBtnType) {
                String name = nameField.getText();
                String email = emailField.getText();
                String password = passwordField.getText();
                Role role = roleCombo.getValue();

                try {
                    authService.createStaff(name, email, password, role);
                    loadStaff();
                    Alert success = new Alert(Alert.AlertType.INFORMATION, "Staff account created successfully.", ButtonType.OK);
                    success.showAndWait();
                } catch (ClinicDeskException | IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING, "Failed to create staff account", e);
                    showAlert("Account creation failed: " + e.getMessage());
                }
            }
        });
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
