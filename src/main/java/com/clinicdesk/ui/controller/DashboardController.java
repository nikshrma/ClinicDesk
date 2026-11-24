package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLDocumentDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.DashboardService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Overview Dashboard screen.
 */
public class DashboardController {

    private static final Logger LOGGER = AppLogger.getLogger(DashboardController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Label welcomeLabel;
    @FXML private Label cardActivePatients;
    @FXML private Label cardTodayVisits;
    @FXML private Label cardCheckedIn;
    @FXML private Label cardDocumentsCount;
    @FXML private Button btnQuickCapture;

    @FXML private TableView<Appointment> todayTable;
    @FXML private TableColumn<Appointment, String> colTime;
    @FXML private TableColumn<Appointment, String> colPatient;
    @FXML private TableColumn<Appointment, String> colDoctor;
    @FXML private TableColumn<Appointment, String> colReason;
    @FXML private TableColumn<Appointment, String> colStatus;

    @FXML private ListView<String> recentPatientsList;
    @FXML private ListView<String> recentDocsList;

    private final DashboardService dashboardService;

    public DashboardController() {
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLDocumentDAO docDao = new MySQLDocumentDAO();
        this.dashboardService = new DashboardService(patDao, appDao, docDao);
    }

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @FXML
    public void initialize() {
        SessionContext.getCurrentUser().ifPresent(u ->
                welcomeLabel.setText("Welcome back, " + u.getFullName() + " (" + u.getRole().getDisplayName() + ")")
        );

        boolean isClinical = SessionContext.hasRole(Role.ADMIN, Role.DOCTOR);
        btnQuickCapture.setVisible(isClinical);
        btnQuickCapture.setManaged(isClinical);

        setupTableColumns();
        loadDashboardData();
    }

    private void setupTableColumns() {
        colTime.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getScheduledAt().format(TIME_FMT)));
        colPatient.setCellValueFactory(data -> {
            Appointment a = data.getValue();
            return new SimpleStringProperty((a.getPatientName() != null ? a.getPatientName() : "ID: " + a.getPatientId()) +
                    (a.getPatientNumber() != null ? " [" + a.getPatientNumber() + "]" : ""));
        });
        colDoctor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDoctorName() != null ? data.getValue().getDoctorName() : "Doctor"));
        colReason.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReason()));

        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().getDisplayName()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Appointment a = getTableView().getItems().get(getIndex());
                    Label badge = new Label(a.getStatus().getDisplayName());
                    badge.setStyle("-fx-background-color: " + a.getStatus().getBgColor() +
                            "; -fx-text-fill: " + a.getStatus().getTextColor() +
                            "; -fx-padding: 2px 8px; -fx-background-radius: 8px; -fx-font-weight: bold; -fx-font-size: 11px;");
                    setGraphic(badge);
                }
            }
        });
    }

    public void loadDashboardData() {
        Task<DashboardStats> loadTask = new Task<>() {
            @Override
            protected DashboardStats call() {
                return dashboardService.getDashboardStatistics();
            }
        };

        loadTask.setOnSucceeded(e -> {
            DashboardStats stats = loadTask.getValue();
            cardActivePatients.setText(String.valueOf(stats.getActivePatientsCount()));
            cardTodayVisits.setText(String.valueOf(stats.getTodayTotalAppointments()));
            cardCheckedIn.setText(String.valueOf(stats.getTodayCheckedIn()));
            cardDocumentsCount.setText(String.valueOf(stats.getTotalDocumentsCount()));

            todayTable.setItems(FXCollections.observableArrayList(stats.getTodayAppointments()));

            if (stats.getRecentPatients() != null) {
                recentPatientsList.setItems(FXCollections.observableArrayList(
                        stats.getRecentPatients().stream()
                                .map(p -> p.getFullName() + " (" + p.getPatientNumber() + ") — " + p.getPhone())
                                .toList()
                ));
            }

            if (stats.getRecentDocuments() != null) {
                recentDocsList.setItems(FXCollections.observableArrayList(
                        stats.getRecentDocuments().stream()
                                .map(d -> d.getOriginalFilename() + " [" + d.getCategory().getDisplayName() + "]")
                                .toList()
                ));
            }
        });

        loadTask.setOnFailed(e -> {
            LOGGER.log(Level.WARNING, "Failed to load dashboard statistics", loadTask.getException());
        });

        Thread thread = new Thread(loadTask, "Dashboard-Loader-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadDashboardData();
    }

    @FXML
    public void handleNewPatient(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PatientFormDialog.fxml"));
            Parent root = loader.load();

            PatientFormDialogController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Register New Patient");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                loadDashboardData();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open patient registration form", e);
        }
    }

    @FXML
    public void handleNewAppointment(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AppointmentFormDialog.fxml"));
            Parent root = loader.load();

            AppointmentFormDialogController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Schedule Appointment");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                loadDashboardData();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open appointment scheduling form", e);
        }
    }

    @FXML
    public void handleQuickCapture(ActionEvent event) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        // Delegate to document library or camera flow
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DocumentLibraryView.fxml"));
            Parent root = loader.load();

            StackPane contentArea = (StackPane) welcomeLabel.getScene().lookup("#contentArea");
            if (contentArea != null) {
                contentArea.getChildren().setAll(root);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open document library", e);
        }
    }
}
