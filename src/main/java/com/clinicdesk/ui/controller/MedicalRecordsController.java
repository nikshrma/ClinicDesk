package com.clinicdesk.ui.controller;

import com.clinicdesk.config.StorageConfig;
import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLDocumentDAO;
import com.clinicdesk.dao.mysql.MySQLMedicalRecordDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.DocumentCategory;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.model.MedicalRecord;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.DocumentService;
import com.clinicdesk.service.MedicalRecordService;
import com.clinicdesk.service.PatientService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Medical Records longitudinal directory view.
 * Supports end-to-end Doctor Consultation & Prescription Linkage.
 */
public class MedicalRecordsController {

    private static final Logger LOGGER = AppLogger.getLogger(MedicalRecordsController.class);

    @FXML private TextField searchField;
    @FXML private Button btnCapturePrescription;
    @FXML private TableView<MedicalRecord> recordsTable;
    @FXML private TableColumn<MedicalRecord, String> colVisitDate;
    @FXML private TableColumn<MedicalRecord, String> colPatient;
    @FXML private TableColumn<MedicalRecord, String> colDoctor;
    @FXML private TableColumn<MedicalRecord, String> colChiefComplaint;
    @FXML private TableColumn<MedicalRecord, String> colDiagnosis;
    @FXML private TableColumn<MedicalRecord, String> colFollowUp;
    @FXML private TableColumn<MedicalRecord, Void> colActions;

    private final MedicalRecordService medicalRecordService;
    private final DocumentService documentService;
    private final ObservableList<MedicalRecord> recordsData = FXCollections.observableArrayList();

    public MedicalRecordsController() {
        MySQLMedicalRecordDAO mrDao = new MySQLMedicalRecordDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLUserDAO userDao = new MySQLUserDAO();
        MySQLDocumentDAO docDao = new MySQLDocumentDAO();
        PatientService patService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patService, userDao);
        this.medicalRecordService = new MedicalRecordService(mrDao, patService, appService);
        this.documentService = new DocumentService(docDao, StorageConfig.getStorageService(), patService);
    }

    public MedicalRecordsController(MedicalRecordService medicalRecordService, DocumentService documentService) {
        this.medicalRecordService = medicalRecordService;
        this.documentService = documentService;
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

        if (colActions != null) {
            colActions.setCellFactory(col -> new TableCell<>() {
                private final Button btnCapture = new Button("📷 Rx");
                private final Button btnView = new Button("👁 View");
                private final HBox pane = new HBox(6, btnCapture, btnView);

                {
                    btnCapture.getStyleClass().add("btn-primary");
                    btnView.getStyleClass().add("btn-secondary");

                    btnCapture.setOnAction(e -> {
                        MedicalRecord record = getTableView().getItems().get(getIndex());
                        openCameraCaptureForRecord(record);
                    });

                    btnView.setOnAction(e -> {
                        MedicalRecord record = getTableView().getItems().get(getIndex());
                        openPrescriptionPreviewForRecord(record);
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : pane);
                }
            });
        }

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

    @FXML
    public void handleCapturePrescription(ActionEvent event) {
        MedicalRecord selected = recordsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Please select a medical record from the table to capture and link a prescription photograph.");
            return;
        }
        openCameraCaptureForRecord(selected);
    }

    public void openCameraCaptureForRecord(MedicalRecord record) {
        if (record == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CameraCaptureDialog.fxml"));
            Parent root = loader.load();

            CameraCaptureController controller = loader.getController();
            controller.setContext(record.getPatientId(), record.getId(), DocumentCategory.PRESCRIPTION);
            controller.setOnSavedCallback(() -> Platform.runLater(this::loadRecords));

            Stage stage = new Stage();
            String patientName = record.getPatientName() != null ? record.getPatientName() : "Patient";
            stage.setTitle("Capture Prescription Photograph — " + patientName + " (Encounter: " + record.getId() + ")");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setOnCloseRequest(e -> controller.releaseCamera());
            stage.showAndWait();

            if (controller.isSaved()) {
                loadRecords();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open camera capture dialog", e);
            showAlert("Could not open camera dialog: " + e.getMessage());
        }
    }

    public void openPrescriptionPreviewForRecord(MedicalRecord record) {
        if (record == null) return;
        try {
            List<DocumentMetadata> docs = documentService.getDocumentsForMedicalRecord(record.getId());
            if (docs == null || docs.isEmpty()) {
                Alert info = new Alert(Alert.AlertType.INFORMATION,
                        "No prescription photograph or document has been linked to this medical record yet.\nUse '📷 Rx' to capture one.",
                        ButtonType.OK);
                info.setHeaderText("No Document Linked");
                info.showAndWait();
                return;
            }

            // Pick the latest prescription or document
            DocumentMetadata doc = docs.getFirst();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DocumentPreviewDialog.fxml"));
            Parent root = loader.load();

            DocumentPreviewController controller = loader.getController();
            controller.setDocument(doc, documentService);

            Stage stage = new Stage();
            stage.setTitle("Prescription Document — " + doc.getOriginalFilename());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to open document preview", e);
            showAlert("Could not preview document: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
