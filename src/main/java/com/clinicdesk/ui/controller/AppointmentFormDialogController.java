package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.AuthService;
import com.clinicdesk.service.PatientService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Appointment Scheduling dialog.
 */
public class AppointmentFormDialogController {

    private static final Logger LOGGER = AppLogger.getLogger(AppointmentFormDialogController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Label dialogTitle;
    @FXML private Label formErrorLabel;
    @FXML private ComboBox<Patient> patientComboBox;
    @FXML private ComboBox<User> doctorComboBox;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> timeComboBox;
    @FXML private ComboBox<Integer> durationComboBox;
    @FXML private TextArea reasonArea;
    @FXML private Button saveButton;

    private final AppointmentService appointmentService;
    private final PatientService patientService;
    private final AuthService authService;

    private boolean saved = false;

    public AppointmentFormDialogController() {
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLUserDAO userDao = new MySQLUserDAO();
        this.patientService = new PatientService(patDao);
        this.authService = new AuthService(userDao);
        this.appointmentService = new AppointmentService(appDao, patientService, userDao);
    }

    public AppointmentFormDialogController(AppointmentService appointmentService, PatientService patientService, AuthService authService) {
        this.appointmentService = appointmentService;
        this.patientService = patientService;
        this.authService = authService;
    }

    @FXML
    public void initialize() {
        hideError();
        setupDropdowns();
    }

    private void setupDropdowns() {
        // Patient converter
        patientComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Patient p) {
                return p != null ? p.getFullName() + " (" + p.getPatientNumber() + ")" : "";
            }
            @Override public Patient fromString(String string) { return null; }
        });

        // Doctor converter
        doctorComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(User u) {
                return u != null ? u.getFullName() : "";
            }
            @Override public User fromString(String string) { return null; }
        });

        // Populate Times from 08:00 to 18:00 in 30m steps
        List<String> times = new ArrayList<>();
        LocalTime t = LocalTime.of(8, 0);
        while (!t.isAfter(LocalTime.of(18, 0))) {
            times.add(t.format(TIME_FMT));
            t = t.plusMinutes(30);
        }
        timeComboBox.setItems(FXCollections.observableArrayList(times));

        // Durations
        durationComboBox.setItems(FXCollections.observableArrayList(15, 30, 45, 60));
        durationComboBox.setValue(30);

        // Date default to tomorrow or today
        datePicker.setValue(LocalDate.now().plusDays(1));

        // Load data
        try {
            List<Patient> patients = patientService.searchPatients("", false);
            patientComboBox.setItems(FXCollections.observableArrayList(patients));

            List<User> doctors = authService.getActiveDoctors();
            doctorComboBox.setItems(FXCollections.observableArrayList(doctors));
            if (!doctors.isEmpty()) {
                doctorComboBox.setValue(doctors.get(0));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load dropdown options", e);
        }
    }

    public void preSelectPatient(Patient patient) {
        if (patient != null) {
            patientComboBox.setValue(patient);
            patientComboBox.setDisable(true); // Lock patient if opened from their profile
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    public void handleSave(ActionEvent event) {
        hideError();

        Patient patient = patientComboBox.getValue();
        User doctor = doctorComboBox.getValue();
        LocalDate date = datePicker.getValue();
        String timeStr = timeComboBox.getValue();
        Integer duration = durationComboBox.getValue();
        String reason = reasonArea.getText();

        if (patient == null) {
            showError("Please select a patient.");
            return;
        }
        if (doctor == null) {
            showError("Please select an attending physician.");
            return;
        }
        if (date == null || timeStr == null) {
            showError("Please choose an appointment date and time.");
            return;
        }
        if (reason == null || reason.trim().length() < 3) {
            showError("Reason for visit is required (minimum 3 characters).");
            return;
        }

        LocalTime time = LocalTime.parse(timeStr, TIME_FMT);
        LocalDateTime scheduledAt = LocalDateTime.of(date, time);

        if (scheduledAt.isBefore(LocalDateTime.now())) {
            showError("Appointment time cannot be in the past.");
            return;
        }

        try {
            Appointment app = new Appointment();
            app.setPatientId(patient.getId());
            app.setDoctorId(doctor.getId());
            app.setScheduledAt(scheduledAt);
            app.setDurationMinutes(duration != null ? duration : 30);
            app.setReason(reason.trim());

            appointmentService.scheduleAppointment(app);
            saved = true;
            closeDialog();
        } catch (ClinicDeskException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to schedule appointment", e);
            showError(e.getMessage());
        }
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        formErrorLabel.setText(message);
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }

    private void hideError() {
        formErrorLabel.setText("");
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
    }
}
