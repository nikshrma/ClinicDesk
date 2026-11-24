package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.AppointmentStatus;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.AuthService;
import com.clinicdesk.service.PatientService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the daily and filtered Appointment Schedule screen.
 */
public class AppointmentScheduleController {

    private static final Logger LOGGER = AppLogger.getLogger(AppointmentScheduleController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<User> doctorFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;

    @FXML private Label statTotalLabel;
    @FXML private Label statScheduledLabel;
    @FXML private Label statCheckedInLabel;
    @FXML private Label statCompletedLabel;
    @FXML private Label statCancelledLabel;

    @FXML private TableView<Appointment> appointmentsTable;
    @FXML private TableColumn<Appointment, String> colTime;
    @FXML private TableColumn<Appointment, String> colPatient;
    @FXML private TableColumn<Appointment, String> colDoctor;
    @FXML private TableColumn<Appointment, String> colReason;
    @FXML private TableColumn<Appointment, String> colStatus;
    @FXML private TableColumn<Appointment, Void> colActions;

    private final AppointmentService appointmentService;
    private final AuthService authService;
    private final ObservableList<Appointment> tableData = FXCollections.observableArrayList();

    public AppointmentScheduleController() {
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLUserDAO userDao = new MySQLUserDAO();
        PatientService patientService = new PatientService(patDao);
        this.authService = new AuthService(userDao);
        this.appointmentService = new AppointmentService(appDao, patientService, userDao);
    }

    public AppointmentScheduleController(AppointmentService appointmentService, AuthService authService) {
        this.appointmentService = appointmentService;
        this.authService = authService;
    }

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());

        setupDropdowns();
        setupTableColumns();
        loadAppointments();
    }

    private void setupDropdowns() {
        // Status dropdown
        ObservableList<String> statuses = FXCollections.observableArrayList("All Statuses");
        for (AppointmentStatus s : AppointmentStatus.values()) {
            statuses.add(s.name());
        }
        statusFilterCombo.setItems(statuses);
        statusFilterCombo.setValue("All Statuses");

        // Doctor dropdown
        try {
            List<User> doctors = authService.getActiveDoctors();
            doctorFilterCombo.setItems(FXCollections.observableArrayList(doctors));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load doctors for filter", e);
        }
    }

    private void setupTableColumns() {
        colTime.setCellValueFactory(data -> {
            Appointment a = data.getValue();
            String start = a.getScheduledAt().format(TIME_FMT);
            String end = a.getEndDateTime().format(TIME_FMT);
            return new SimpleStringProperty(start + " - " + end + " (" + a.getDurationMinutes() + "m)");
        });

        colPatient.setCellValueFactory(data -> {
            Appointment a = data.getValue();
            String name = a.getPatientName() != null ? a.getPatientName() : "ID: " + a.getPatientId();
            String num = a.getPatientNumber() != null ? " [" + a.getPatientNumber() + "]" : "";
            return new SimpleStringProperty(name + num);
        });

        colDoctor.setCellValueFactory(data -> {
            Appointment a = data.getValue();
            return new SimpleStringProperty(a.getDoctorName() != null ? a.getDoctorName() : "Doctor ID: " + a.getDoctorId());
        });

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
                            "; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 11px;");
                    setGraphic(badge);
                }
            }
        });

        // Contextual Actions Column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnCheckIn = new Button("Check In");
            private final Button btnConfirm = new Button("Confirm");
            private final Button btnCancel = new Button("Cancel");
            private final HBox pane = new HBox(6);

            {
                btnCheckIn.getStyleClass().add("btn-primary");
                btnConfirm.getStyleClass().add("btn-secondary");
                btnCancel.getStyleClass().add("btn-secondary");

                btnCheckIn.setOnAction(e -> {
                    Appointment a = getTableView().getItems().get(getIndex());
                    transitionStatus(a, AppointmentStatus.CHECKED_IN);
                });

                btnConfirm.setOnAction(e -> {
                    Appointment a = getTableView().getItems().get(getIndex());
                    transitionStatus(a, AppointmentStatus.CONFIRMED);
                });

                btnCancel.setOnAction(e -> {
                    Appointment a = getTableView().getItems().get(getIndex());
                    transitionStatus(a, AppointmentStatus.CANCELLED);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                pane.getChildren().clear();

                if (empty) {
                    setGraphic(null);
                } else {
                    Appointment a = getTableView().getItems().get(getIndex());
                    switch (a.getStatus()) {
                        case SCHEDULED -> pane.getChildren().addAll(btnConfirm, btnCheckIn, btnCancel);
                        case CONFIRMED -> pane.getChildren().addAll(btnCheckIn, btnCancel);
                        case CHECKED_IN -> {
                            // If user is doctor or admin, can mark completed
                            if (SessionContext.hasRole(Role.ADMIN, Role.DOCTOR)) {
                                Button btnComplete = new Button("Complete Visit");
                                btnComplete.getStyleClass().add("btn-primary");
                                btnComplete.setOnAction(ev -> openEncounterDialog(a));
                                pane.getChildren().addAll(btnComplete, btnCancel);
                            } else {
                                pane.getChildren().add(btnCancel);
                            }
                        }
                        case COMPLETED, CANCELLED, NO_SHOW -> {
                            Label lbl = new Label("Closed");
                            lbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
                            pane.getChildren().add(lbl);
                        }
                    }
                    setGraphic(pane);
                }
            }
        });

        appointmentsTable.setItems(tableData);
    }

    public void loadAppointments() {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            date = LocalDate.now();
        }

        try {
            List<Appointment> allDay = appointmentService.getAppointmentsForDate(date);

            // Filter by Doctor if chosen
            User docFilter = doctorFilterCombo.getValue();
            String statusFilter = statusFilterCombo.getValue();

            List<Appointment> filtered = allDay.stream()
                    .filter(a -> docFilter == null || a.getDoctorId().equals(docFilter.getId()))
                    .filter(a -> statusFilter == null || statusFilter.equals("All Statuses") || a.getStatus().name().equals(statusFilter))
                    .toList();

            tableData.setAll(filtered);

            // Update stats
            Map<AppointmentStatus, Integer> counts = appointmentService.getDailyStatusSummary(date);
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            statTotalLabel.setText("Total: " + total);
            statScheduledLabel.setText("Scheduled: " + counts.getOrDefault(AppointmentStatus.SCHEDULED, 0));
            statCheckedInLabel.setText("Checked In: " + counts.getOrDefault(AppointmentStatus.CHECKED_IN, 0));
            statCompletedLabel.setText("Completed: " + counts.getOrDefault(AppointmentStatus.COMPLETED, 0));
            statCancelledLabel.setText("Cancelled: " + counts.getOrDefault(AppointmentStatus.CANCELLED, 0));
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.SEVERE, "Failed to load appointments", e);
            showAlert("Error loading appointments: " + e.getMessage());
        }
    }

    private void transitionStatus(Appointment app, AppointmentStatus target) {
        try {
            appointmentService.transitionStatus(app.getId(), target);
            loadAppointments();
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.WARNING, "Failed to transition status", e);
            showAlert(e.getMessage());
        }
    }

    @FXML
    public void handleDateChange(ActionEvent event) {
        loadAppointments();
    }

    @FXML
    public void handlePrevDay(ActionEvent event) {
        datePicker.setValue(datePicker.getValue().minusDays(1));
        loadAppointments();
    }

    @FXML
    public void handleNextDay(ActionEvent event) {
        datePicker.setValue(datePicker.getValue().plusDays(1));
        loadAppointments();
    }

    @FXML
    public void handleToday(ActionEvent event) {
        datePicker.setValue(LocalDate.now());
        loadAppointments();
    }

    @FXML
    public void handleFilterChange(ActionEvent event) {
        loadAppointments();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadAppointments();
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
                loadAppointments();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open appointment form dialog", e);
            showAlert("Failed to open appointment form: " + e.getMessage());
        }
    }

    private void openEncounterDialog(Appointment appointment) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MedicalRecordFormDialog.fxml"));
            Parent root = loader.load();

            MedicalRecordFormDialogController controller = loader.getController();
            controller.setAppointment(appointment);

            Stage stage = new Stage();
            stage.setTitle("Clinical Encounter — " + appointment.getPatientName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                loadAppointments();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open clinical encounter dialog", e);
            showAlert("Failed to open encounter dialog: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
