package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLDocumentDAO;
import com.clinicdesk.dao.mysql.MySQLMedicalRecordDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.DocumentCategory;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.model.MedicalRecord;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.DocumentService;
import com.clinicdesk.service.MedicalRecordService;
import com.clinicdesk.service.PatientService;
import com.clinicdesk.config.StorageConfig;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Patient Details view with longitudinal tabs.
 */
public class PatientDetailController {

    private static final Logger LOGGER = AppLogger.getLogger(PatientDetailController.class);

    @FXML private Label nameLabel;
    @FXML private Label numberBadge;
    @FXML private Label archivedBadge;
    @FXML private Label demographicsLabel;
    @FXML private Label phoneLabel;
    @FXML private Label emailLabel;
    @FXML private Label bloodGroupLabel;
    @FXML private Label allergiesBanner;

    @FXML private Label addressValue;
    @FXML private Label emergencyContactValue;
    @FXML private Label registeredDateValue;

    @FXML private Button btnEditPatient;
    @FXML private Button btnScheduleApp;
    @FXML private Button btnUploadDoc;
    @FXML private Button btnCapturePrescription;

    @FXML private TableView<MedicalRecord> medicalHistoryTable;
    @FXML private TableColumn<MedicalRecord, String> colVisitDate;
    @FXML private TableColumn<MedicalRecord, String> colVisitDoctor;
    @FXML private TableColumn<MedicalRecord, String> colChiefComplaint;
    @FXML private TableColumn<MedicalRecord, String> colDiagnosis;
    @FXML private TableColumn<MedicalRecord, String> colFollowUp;
    @FXML private TableColumn<MedicalRecord, Void> colHistoryActions;

    @FXML private TableView<DocumentMetadata> documentsTable;
    @FXML private TableColumn<DocumentMetadata, String> colDocCategory;
    @FXML private TableColumn<DocumentMetadata, String> colDocFilename;
    @FXML private TableColumn<DocumentMetadata, String> colDocSize;
    @FXML private TableColumn<DocumentMetadata, String> colDocDate;
    @FXML private TableColumn<DocumentMetadata, Void> colDocActions;

    private Patient currentPatient;
    private final PatientService patientService;
    private final MedicalRecordService medicalRecordService;
    private final DocumentService documentService;

    public PatientDetailController() {
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLMedicalRecordDAO mrDao = new MySQLMedicalRecordDAO();
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLUserDAO userDao = new MySQLUserDAO();
        MySQLDocumentDAO docDao = new MySQLDocumentDAO();
        this.patientService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patientService, userDao);
        this.medicalRecordService = new MedicalRecordService(mrDao, patientService, appService);
        this.documentService = new DocumentService(docDao, StorageConfig.getStorageService(), patientService);
    }

    public PatientDetailController(PatientService patientService, MedicalRecordService medicalRecordService, DocumentService documentService) {
        this.patientService = patientService;
        this.medicalRecordService = medicalRecordService;
        this.documentService = documentService;
    }

    public void setPatient(Patient patient) {
        this.currentPatient = patient;
        refreshUI();
    }

    private void refreshUI() {
        if (currentPatient == null) {
            return;
        }

        nameLabel.setText(currentPatient.getFullName());
        numberBadge.setText(currentPatient.getPatientNumber());

        boolean isArchived = currentPatient.isArchived();
        archivedBadge.setVisible(isArchived);
        archivedBadge.setManaged(isArchived);

        btnScheduleApp.setDisable(isArchived);

        String genderStr = currentPatient.getGender() != null ? currentPatient.getGender().getDisplayName() : "Unspecified";
        demographicsLabel.setText("Age: " + currentPatient.getAge() + " (DOB: " + currentPatient.getDateOfBirth() + ") | " + genderStr);
        phoneLabel.setText("Phone: " + (currentPatient.getPhone() != null ? currentPatient.getPhone() : "—"));
        emailLabel.setText("Email: " + (currentPatient.getEmail() != null ? currentPatient.getEmail() : "—"));
        bloodGroupLabel.setText("Blood: " + (currentPatient.getBloodGroup() != null ? currentPatient.getBloodGroup() : "—"));

        if (currentPatient.getAllergies() != null && !currentPatient.getAllergies().isBlank()) {
            allergiesBanner.setText("⚠️ Known Allergies: " + currentPatient.getAllergies());
            allergiesBanner.setVisible(true);
            allergiesBanner.setManaged(true);
        } else {
            allergiesBanner.setVisible(false);
            allergiesBanner.setManaged(false);
        }

        addressValue.setText(currentPatient.getAddress() != null && !currentPatient.getAddress().isBlank() ? currentPatient.getAddress() : "None provided");
        emergencyContactValue.setText(currentPatient.getEmergencyContact() != null && !currentPatient.getEmergencyContact().isBlank() ? currentPatient.getEmergencyContact() : "None provided");
        registeredDateValue.setText(currentPatient.getCreatedAt() != null ? currentPatient.getCreatedAt().toString().substring(0, 10) : "—");

        boolean isClinicalStaff = SessionContext.hasRole(Role.ADMIN, Role.DOCTOR);
        btnCapturePrescription.setVisible(isClinicalStaff);
        btnCapturePrescription.setManaged(isClinicalStaff);

        // Populate medical history if authorized
        if (isClinicalStaff && medicalHistoryTable != null) {
            setupMedicalHistoryTable();
            try {
                List<MedicalRecord> history = medicalRecordService.getPatientMedicalHistory(currentPatient.getId());
                medicalHistoryTable.setItems(FXCollections.observableArrayList(history));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load patient medical history", e);
            }
        }

        // Populate documents
        if (documentsTable != null) {
            setupDocumentsTable();
            try {
                List<DocumentMetadata> docs = documentService.getDocumentsForPatient(currentPatient.getId());
                documentsTable.setItems(FXCollections.observableArrayList(docs));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load patient documents", e);
            }
        }
    }

    private void setupMedicalHistoryTable() {
        if (colVisitDate.getCellValueFactory() == null) {
            colVisitDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getVisitDate().toString()));
            colVisitDoctor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDoctorName() != null ? data.getValue().getDoctorName() : "Doctor"));
            colChiefComplaint.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getChiefComplaint()));
            colDiagnosis.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDiagnosis()));
            colFollowUp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFollowUpDate() != null ? data.getValue().getFollowUpDate().toString() : "—"));

            if (colHistoryActions != null) {
                colHistoryActions.setCellFactory(col -> new TableCell<>() {
                    private final Button btnCapture = new Button("📷 Rx");
                    private final Button btnView = new Button("👁 View");
                    private final HBox pane = new HBox(6, btnCapture, btnView);

                    {
                        btnCapture.getStyleClass().add("btn-primary");
                        btnView.getStyleClass().add("btn-secondary");

                        btnCapture.setOnAction(e -> {
                            MedicalRecord record = getTableView().getItems().get(getIndex());
                            openPrescriptionCaptureForRecord(record);
                        });

                        btnView.setOnAction(e -> {
                            MedicalRecord record = getTableView().getItems().get(getIndex());
                            viewPrescriptionForRecord(record);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : pane);
                    }
                });
            }
        }
    }

    private void openPrescriptionCaptureForRecord(MedicalRecord record) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CameraCaptureDialog.fxml"));
            Parent root = loader.load();

            CameraCaptureController controller = loader.getController();
            controller.setContext(currentPatient.getId(), record != null ? record.getId() : null, DocumentCategory.PRESCRIPTION);
            controller.setOnSavedCallback(() -> Platform.runLater(this::refreshUI));

            Stage stage = new Stage();
            String titleSuffix = record != null ? " (Visit: " + record.getVisitDate() + ")" : "";
            stage.setTitle("Capture Prescription Photograph — " + currentPatient.getFullName() + titleSuffix);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setOnCloseRequest(e -> controller.releaseCamera());
            stage.showAndWait();

            if (controller.isSaved()) {
                refreshUI();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open camera capture dialog", e);
        }
    }

    private void viewPrescriptionForRecord(MedicalRecord record) {
        try {
            List<DocumentMetadata> docs = documentService.getDocumentsForMedicalRecord(record.getId());
            if (docs == null || docs.isEmpty()) {
                Alert info = new Alert(Alert.AlertType.INFORMATION,
                        "No prescription photograph or document has been linked to this visit record yet.\nUse '📷 Rx' to capture one.",
                        ButtonType.OK);
                info.setHeaderText("No Document Linked");
                info.showAndWait();
                return;
            }

            openDocumentPreview(docs.getFirst());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to view prescription for record: " + record.getId(), e);
        }
    }

    private void setupDocumentsTable() {
        if (colDocCategory.getCellValueFactory() == null) {
            colDocCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().getDisplayName()));
            colDocFilename.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOriginalFilename()));
            colDocSize.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedFileSize()));
            colDocDate.setCellValueFactory(data -> {
                DocumentMetadata d = data.getValue();
                return new SimpleStringProperty(d.getCreatedAt() != null ? d.getCreatedAt().toString().substring(0, 10) : "—");
            });

            colDocActions.setCellFactory(col -> new TableCell<>() {
                private final Button btnPrev = new Button("Preview");
                private final HBox p = new HBox(6, btnPrev);
                {
                    btnPrev.getStyleClass().add("btn-secondary");
                    btnPrev.setOnAction(e -> {
                        DocumentMetadata d = getTableView().getItems().get(getIndex());
                        openDocumentPreview(d);
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : p);
                }
            });
        }
    }

    private void openDocumentPreview(DocumentMetadata doc) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DocumentPreviewDialog.fxml"));
            Parent root = loader.load();

            DocumentPreviewController controller = loader.getController();
            controller.setDocument(doc, documentService);

            Stage stage = new Stage();
            stage.setTitle("Preview Document — " + doc.getOriginalFilename());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open preview dialog", e);
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PatientDirectoryView.fxml"));
            Parent root = loader.load();

            StackPane contentArea = (StackPane) nameLabel.getScene().lookup("#contentArea");
            if (contentArea != null) {
                contentArea.getChildren().setAll(root);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to navigate back to directory", e);
        }
    }

    @FXML
    public void handleEditPatient(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PatientFormDialog.fxml"));
            Parent root = loader.load();

            PatientFormDialogController controller = loader.getController();
            controller.setPatientToEdit(currentPatient);

            Stage stage = new Stage();
            stage.setTitle("Edit Patient Profile");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaved()) {
                this.currentPatient = patientService.getPatientById(currentPatient.getId());
                refreshUI();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to open edit dialog", e);
        }
    }

    @FXML
    public void handleScheduleAppointment(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AppointmentFormDialog.fxml"));
            Parent root = loader.load();

            AppointmentFormDialogController controller = loader.getController();
            controller.preSelectPatient(currentPatient);

            Stage stage = new Stage();
            stage.setTitle("Schedule Appointment — " + currentPatient.getFullName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open appointment form dialog", e);
        }
    }

    @FXML
    public void handleUploadDoc(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload Document for " + currentPatient.getFullName());
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Medical Documents (*.jpg, *.png, *.webp, *.pdf)", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.pdf"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        Stage stage = (Stage) nameLabel.getScene().getWindow();
        File selectedFile = chooser.showOpenDialog(stage);

        if (selectedFile != null) {
            promptCategoryAndUpload(selectedFile);
        }
    }

    private void promptCategoryAndUpload(File file) {
        ChoiceDialog<DocumentCategory> dialog = new ChoiceDialog<>(DocumentCategory.CLINICAL_DOCUMENT, DocumentCategory.values());
        dialog.setTitle("Select Document Category");
        dialog.setHeaderText("Categorize: " + file.getName());
        dialog.setContentText("Category:");

        dialog.showAndWait().ifPresent(cat -> {
            Task<DocumentMetadata> task = new Task<>() {
                @Override
                protected DocumentMetadata call() {
                    return documentService.uploadDocument(file, currentPatient.getId(), null, cat, file.getName(), Instant.now());
                }
            };
            task.setOnSucceeded(e -> {
                refreshUI();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Document successfully uploaded to patient record.", ButtonType.OK);
                alert.showAndWait();
            });
            task.setOnFailed(e -> {
                LOGGER.log(Level.SEVERE, "Upload failed", task.getException());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Upload failed: " + task.getException().getMessage(), ButtonType.OK);
                alert.showAndWait();
            });
            Thread t = new Thread(task, "Doc-Upload-Thread");
            t.setDaemon(true);
            t.start();
        });
    }

    @FXML
    public void handleCapturePrescription(ActionEvent event) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        MedicalRecord selectedRecord = (medicalHistoryTable != null)
                ? medicalHistoryTable.getSelectionModel().getSelectedItem()
                : null;
        String recId = selectedRecord != null ? selectedRecord.getId() : null;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CameraCaptureDialog.fxml"));
            Parent root = loader.load();

            CameraCaptureController controller = loader.getController();
            controller.setContext(currentPatient.getId(), recId, DocumentCategory.PRESCRIPTION);
            controller.setOnSavedCallback(() -> Platform.runLater(this::refreshUI));

            Stage stage = new Stage();
            String titleSuffix = selectedRecord != null ? " (Visit: " + selectedRecord.getVisitDate() + ")" : "";
            stage.setTitle("Capture Prescription Photograph — " + currentPatient.getFullName() + titleSuffix);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.setOnCloseRequest(e -> controller.releaseCamera());
            stage.showAndWait();

            if (controller.isSaved()) {
                refreshUI();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open camera capture dialog", e);
        }
    }
}
