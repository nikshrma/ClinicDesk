package com.clinicdesk.ui.controller;

import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the primary application shell and persistent navigation sidebar.
 */
public class MainShellController {

    private static final Logger LOGGER = AppLogger.getLogger(MainShellController.class);

    @FXML private Label userNameLabel;
    @FXML private Label roleBadge;
    @FXML private Label welcomeTitle;
    @FXML private StackPane contentArea;

    @FXML private Button navOverviewBtn;
    @FXML private Button navPatientsBtn;
    @FXML private Button navAppointmentsBtn;
    @FXML private Button navMedicalRecordsBtn;
    @FXML private Button navDocumentsBtn;
    @FXML private VBox adminSection;
    @FXML private Button navStaffBtn;
    @FXML private Button navSettingsBtn;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        currentActiveBtn = navOverviewBtn;
        applyRolePermissions();
        handleNavOverview(null);
    }

    private void applyRolePermissions() {
        Optional<User> currentUserOpt = SessionContext.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            LOGGER.warning("No authenticated user in SessionContext. Returning to login.");
            handleLogout(null);
            return;
        }

        User user = currentUserOpt.get();
        userNameLabel.setText(user.getFullName());
        roleBadge.setText(user.getRole().getDisplayName().toUpperCase());
        welcomeTitle.setText("Welcome back, " + user.getFullName());

        Role role = user.getRole();
        LOGGER.info("Applying navigation visibility for role: " + role);

        switch (role) {
            case RECEPTIONIST -> {
                // Receptionists cannot create or modify diagnoses/medical records
                navMedicalRecordsBtn.setVisible(false);
                navMedicalRecordsBtn.setManaged(false);
                adminSection.setVisible(false);
                adminSection.setManaged(false);
            }
            case DOCTOR -> {
                // Doctors have access to clinical modules, but not admin staff/system settings
                navMedicalRecordsBtn.setVisible(true);
                navMedicalRecordsBtn.setManaged(true);
                adminSection.setVisible(false);
                adminSection.setManaged(false);
            }
            case ADMIN -> {
                // Admins have access to all modules
                navMedicalRecordsBtn.setVisible(true);
                navMedicalRecordsBtn.setManaged(true);
                adminSection.setVisible(true);
                adminSection.setManaged(true);
            }
        }
    }

    private void setActiveButton(Button btn) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("nav-button-active");
        }
        btn.getStyleClass().add("nav-button-active");
        currentActiveBtn = btn;
    }

    @FXML
    public void handleNavOverview(ActionEvent event) {
        setActiveButton(navOverviewBtn);
        loadView("/fxml/DashboardView.fxml");
    }

    @FXML
    public void handleNavPatients(ActionEvent event) {
        setActiveButton(navPatientsBtn);
        loadView("/fxml/PatientDirectoryView.fxml");
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load view: " + fxmlPath, e);
        }
    }

    @FXML
    public void handleNavAppointments(ActionEvent event) {
        setActiveButton(navAppointmentsBtn);
        loadView("/fxml/AppointmentScheduleView.fxml");
    }

    @FXML
    public void handleNavMedicalRecords(ActionEvent event) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        setActiveButton(navMedicalRecordsBtn);
        loadView("/fxml/MedicalRecordsView.fxml");
    }

    @FXML
    public void handleNavDocuments(ActionEvent event) {
        setActiveButton(navDocumentsBtn);
        loadView("/fxml/DocumentLibraryView.fxml");
    }

    @FXML
    public void handleNavStaff(ActionEvent event) {
        SessionContext.requireRole(Role.ADMIN);
        setActiveButton(navStaffBtn);
        loadView("/fxml/StaffManagementView.fxml");
    }

    @FXML
    public void handleNavSettings(ActionEvent event) {
        SessionContext.requireRole(Role.ADMIN);
        setActiveButton(navSettingsBtn);
        loadView("/fxml/SettingsBackupView.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        LOGGER.info("User requested logout.");
        SessionContext.clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) userNameLabel.getScene().getWindow();
            Scene scene = new Scene(root, 900, 650);
            stage.setScene(scene);
            stage.setTitle("ClinicDesk — Sign In");
            stage.centerOnScreen();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load LoginView.fxml", e);
        }
    }
}
