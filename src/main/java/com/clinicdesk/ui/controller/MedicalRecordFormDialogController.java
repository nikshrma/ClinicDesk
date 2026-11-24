package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLMedicalRecordDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.MedicalRecord;
import com.clinicdesk.model.Patient;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.MedicalRecordService;
import com.clinicdesk.service.PatientService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Medical Record encounter form dialog.
 */
public class MedicalRecordFormDialogController {

    private static final Logger LOGGER = AppLogger.getLogger(MedicalRecordFormDialogController.class);

    @FXML private Label dialogTitle;
    @FXML private Label formErrorLabel;
    @FXML private Label patientInfoLabel;
    @FXML private Label doctorInfoLabel;
    @FXML private Label appointmentReasonLabel;

    @FXML private DatePicker visitDatePicker;
    @FXML private DatePicker followUpDatePicker;
    @FXML private TextArea chiefComplaintArea;
    @FXML private TextArea diagnosisArea;
    @FXML private Button saveButton;

    private final MedicalRecordService medicalRecordService;
    private final PatientService patientService;
    private Appointment appointment;
    private boolean saved = false;

    public MedicalRecordFormDialogController() {
        MySQLMedicalRecordDAO mrDao = new MySQLMedicalRecordDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLUserDAO userDao = new MySQLUserDAO();
        this.patientService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patientService, userDao);
        this.medicalRecordService = new MedicalRecordService(mrDao, patientService, appService);
    }

    public MedicalRecordFormDialogController(MedicalRecordService medicalRecordService, PatientService patientService) {
        this.medicalRecordService = medicalRecordService;
        this.patientService = patientService;
    }

    @FXML
    public void initialize() {
        visitDatePicker.setValue(LocalDate.now());
        hideError();
    }

    public void setAppointment(Appointment app) {
        this.appointment = app;
        if (app != null) {
            String patName = app.getPatientName() != null ? app.getPatientName() : "ID: " + app.getPatientId();
            String patNum = app.getPatientNumber() != null ? " (" + app.getPatientNumber() + ")" : "";
            patientInfoLabel.setText("Patient: " + patName + patNum);

            doctorInfoLabel.setText("Doctor: " + (app.getDoctorName() != null ? app.getDoctorName() : "—"));
            appointmentReasonLabel.setText("Encounter Scheduled: " + app.getScheduledAt().toString().replace('T', ' ') +
                    " (Reason: " + app.getReason() + ")");

            if (app.getReason() != null && !app.getReason().isBlank()) {
                chiefComplaintArea.setText(app.getReason());
            }
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    public void handleSave(ActionEvent event) {
        hideError();

        if (appointment == null) {
            showError("No associated appointment context found.");
            return;
        }

        LocalDate visitDate = visitDatePicker.getValue();
        LocalDate followUpDate = followUpDatePicker.getValue();
        String complaint = chiefComplaintArea.getText();
        String diagnosis = diagnosisArea.getText();

        if (visitDate == null || visitDate.isAfter(LocalDate.now())) {
            showError("Valid visit date (not in the future) is required.");
            return;
        }
        if (complaint == null || complaint.trim().length() < 3) {
            showError("Chief Complaint is required (minimum 3 characters).");
            return;
        }
        if (diagnosis == null || diagnosis.trim().length() < 3) {
            showError("Diagnosis is required (minimum 3 characters).");
            return;
        }
        if (followUpDate != null && followUpDate.isBefore(visitDate)) {
            showError("Follow-up date cannot be earlier than the visit date.");
            return;
        }

        try {
            MedicalRecord record = new MedicalRecord();
            record.setPatientId(appointment.getPatientId());
            record.setAppointmentId(appointment.getId());
            record.setDoctorId(appointment.getDoctorId());
            record.setVisitDate(visitDate);
            record.setChiefComplaint(complaint.trim());
            record.setDiagnosis(diagnosis.trim());
            record.setFollowUpDate(followUpDate);

            medicalRecordService.createMedicalRecord(record);
            saved = true;
            closeDialog();
        } catch (ClinicDeskException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to create medical record", e);
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
