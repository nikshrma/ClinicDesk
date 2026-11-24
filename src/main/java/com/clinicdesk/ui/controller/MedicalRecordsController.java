package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLMedicalRecordDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.MedicalRecord;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.MedicalRecordService;
import com.clinicdesk.service.PatientService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Medical Records longitudinal directory view.
 */
public class MedicalRecordsController {

    private static final Logger LOGGER = AppLogger.getLogger(MedicalRecordsController.class);

    @FXML private TextField searchField;
    @FXML private TableView<MedicalRecord> recordsTable;
    @FXML private TableColumn<MedicalRecord, String> colVisitDate;
    @FXML private TableColumn<MedicalRecord, String> colPatient;
    @FXML private TableColumn<MedicalRecord, String> colDoctor;
    @FXML private TableColumn<MedicalRecord, String> colChiefComplaint;
    @FXML private TableColumn<MedicalRecord, String> colDiagnosis;
    @FXML private TableColumn<MedicalRecord, String> colFollowUp;

    private final MedicalRecordService medicalRecordService;
    private final ObservableList<MedicalRecord> recordsData = FXCollections.observableArrayList();

    public MedicalRecordsController() {
        MySQLMedicalRecordDAO mrDao = new MySQLMedicalRecordDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLUserDAO userDao = new MySQLUserDAO();
        PatientService patService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patService, userDao);
        this.medicalRecordService = new MedicalRecordService(mrDao, patService, appService);
    }

    public MedicalRecordsController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @FXML
    public void initialize() {
        // Enforce RBAC
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);

        setupTableColumns();
        loadRecords();
    }

    private void setupTableColumns() {
        colVisitDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getVisitDate().toString()));

        colPatient.setCellValueFactory(data -> {
            MedicalRecord m = data.getValue();
            String name = m.getPatientName() != null ? m.getPatientName() : "ID: " + m.getPatientId();
            String num = m.getPatientNumber() != null ? " [" + m.getPatientNumber() + "]" : "";
            return new SimpleStringProperty(name + num);
        });

        colDoctor.setCellValueFactory(data -> {
            MedicalRecord m = data.getValue();
            return new SimpleStringProperty(m.getDoctorName() != null ? m.getDoctorName() : "Doctor ID: " + m.getDoctorId());
        });

        colChiefComplaint.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getChiefComplaint()));
        colDiagnosis.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDiagnosis()));

        colFollowUp.setCellValueFactory(data -> {
            MedicalRecord m = data.getValue();
            return new SimpleStringProperty(m.getFollowUpDate() != null ? m.getFollowUpDate().toString() : "—");
        });

        recordsTable.setItems(recordsData);
    }

    public void loadRecords() {
        try {
            List<MedicalRecord> recent = medicalRecordService.getAllRecentRecords(50);
            String q = searchField.getText();

            if (q != null && !q.isBlank()) {
                String lower = q.toLowerCase().trim();
                List<MedicalRecord> filtered = recent.stream()
                        .filter(r -> (r.getPatientName() != null && r.getPatientName().toLowerCase().contains(lower)) ||
                                     (r.getPatientNumber() != null && r.getPatientNumber().toLowerCase().contains(lower)) ||
                                     (r.getDiagnosis() != null && r.getDiagnosis().toLowerCase().contains(lower)) ||
                                     (r.getChiefComplaint() != null && r.getChiefComplaint().toLowerCase().contains(lower)))
                        .toList();
                recordsData.setAll(filtered);
            } else {
                recordsData.setAll(recent);
            }
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.SEVERE, "Failed to load medical records", e);
            showAlert("Error loading medical records: " + e.getMessage());
        }
    }

    @FXML
    public void handleSearch(KeyEvent event) {
        loadRecords();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadRecords();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
