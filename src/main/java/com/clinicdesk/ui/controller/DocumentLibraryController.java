package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLDocumentDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.DocumentCategory;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.DocumentService;
import com.clinicdesk.service.PatientService;
import com.clinicdesk.config.StorageConfig;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Document Library screen.
 */
public class DocumentLibraryController {

    private static final Logger LOGGER = AppLogger.getLogger(DocumentLibraryController.class);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private Button btnCapturePrescription;

    @FXML private TableView<DocumentMetadata> documentsTable;
    @FXML private TableColumn<DocumentMetadata, String> colCategory;
    @FXML private TableColumn<DocumentMetadata, String> colFilename;
    @FXML private TableColumn<DocumentMetadata, String> colPatient;
    @FXML private TableColumn<DocumentMetadata, String> colSize;
    @FXML private TableColumn<DocumentMetadata, String> colDate;
    @FXML private TableColumn<DocumentMetadata, Void> colActions;

    private final DocumentService documentService;
    private final PatientService patientService;
    private final ObservableList<DocumentMetadata> tableData = FXCollections.observableArrayList();

    public DocumentLibraryController() {
        MySQLDocumentDAO docDao = new MySQLDocumentDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        this.patientService = new PatientService(patDao);
        this.documentService = new DocumentService(docDao, StorageConfig.getStorageService(), patientService);
    }

    public DocumentLibraryController(DocumentService documentService, PatientService patientService) {
        this.documentService = documentService;
        this.patientService = patientService;
    }

    @FXML
    public void initialize() {
        setupFilters();
        setupTableColumns();
        loadDocuments();

        boolean isClinical = SessionContext.hasRole(Role.ADMIN, Role.DOCTOR);
        btnCapturePrescription.setVisible(isClinical);
        btnCapturePrescription.setManaged(isClinical);
    }

    private void setupFilters() {
        ObservableList<String> categories = FXCollections.observableArrayList("All Categories");
        for (DocumentCategory c : DocumentCategory.values()) {
            categories.add(c.name());
        }
        categoryFilterCombo.setItems(categories);
        categoryFilterCombo.setValue("All Categories");
    }

    private void setupTableColumns() {
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().getDisplayName()));
        colCategory.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    DocumentMetadata doc = getTableView().getItems().get(getIndex());
                    Label badge = new Label(doc.getCategory().getDisplayName());
                    badge.setStyle("-fx-background-color: " + doc.getCategory().getBgColor() +
                            "; -fx-text-fill: " + doc.getCategory().getTextColor() +
                            "; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 11px;");
                    setGraphic(badge);
                }
            }
        });

        colFilename.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOriginalFilename()));

        colPatient.setCellValueFactory(data -> {
            DocumentMetadata doc = data.getValue();
            String name = doc.getPatientName() != null ? doc.getPatientName() : "ID: " + doc.getPatientId();
            String num = doc.getPatientNumber() != null ? " [" + doc.getPatientNumber() + "]" : "";
            return new SimpleStringProperty(name + num);
        });

        colSize.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedFileSize()));

        colDate.setCellValueFactory(data -> {
            DocumentMetadata doc = data.getValue();
            String d = doc.getCreatedAt() != null ? doc.getCreatedAt().toString().substring(0, 10) : "—";
            String u = doc.getUploaderName() != null ? " (" + doc.getUploaderName() + ")" : "";
            return new SimpleStringProperty(d + u);
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnPreview = new Button("Preview");
            private final Button btnDownload = new Button("Download");
            private final Button btnRename = new Button("Rename");
            private final HBox pane = new HBox(6, btnPreview, btnDownload, btnRename);

            {
                btnPreview.getStyleClass().add("btn-secondary");
                btnDownload.getStyleClass().add("btn-secondary");
                btnRename.getStyleClass().add("btn-secondary");

                btnPreview.setOnAction(e -> {
                    DocumentMetadata doc = getTableView().getItems().get(getIndex());
                    openPreview(doc);
                });

                btnDownload.setOnAction(e -> {
                    DocumentMetadata doc = getTableView().getItems().get(getIndex());
                    saveDocumentToDisk(doc);
                });

                btnRename.setOnAction(e -> {
                    DocumentMetadata doc = getTableView().getItems().get(getIndex());
                    editDocumentFilename(doc);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        documentsTable.setItems(tableData);
    }

    public void loadDocuments() {
        try {
            List<DocumentMetadata> allDocs = documentService.getRecentDocuments(50);
            String catFilter = categoryFilterCombo.getValue();
            String query = searchField.getText();

            List<DocumentMetadata> filtered = allDocs.stream()
                    .filter(d -> catFilter == null || catFilter.equals("All Categories") || d.getCategory().name().equals(catFilter))
                    .filter(d -> {
                        if (query == null || query.isBlank()) return true;
                        String l = query.toLowerCase().trim();
                        return (d.getOriginalFilename() != null && d.getOriginalFilename().toLowerCase().contains(l)) ||
                               (d.getPatientName() != null && d.getPatientName().toLowerCase().contains(l)) ||
                               (d.getPatientNumber() != null && d.getPatientNumber().toLowerCase().contains(l));
                    })
                    .toList();

            tableData.setAll(filtered);
        } catch (ClinicDeskException e) {
            LOGGER.log(Level.SEVERE, "Failed to load document library", e);
            showAlert("Failed to load documents: " + e.getMessage());
        }
    }

    @FXML
    public void handleSearch(KeyEvent event) {
        loadDocuments();
    }

    @FXML
    public void handleFilterChange(ActionEvent event) {
        loadDocuments();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadDocuments();
    }

    @FXML
    public void handleUploadDocument(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Medical Document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Medical Documents (*.jpg, *.png, *.webp, *.pdf)", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.pdf"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        Stage stage = (Stage) documentsTable.getScene().getWindow();
        File selectedFile = chooser.showOpenDialog(stage);

        if (selectedFile != null) {
            promptPatientAndCategoryAndUpload(selectedFile);
        }
    }

    @FXML
    public void handleCapturePrescription(ActionEvent event) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);

        // Prompt patient selection first
        ChoiceDialog<Patient> dialog = new ChoiceDialog<>();
        dialog.setTitle("Select Patient for Prescription Capture");
        dialog.setHeaderText("Choose a patient to attach the captured prescription photograph:");
        try {
            dialog.getItems().addAll(patientService.searchPatients("", false));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load patients for capture", e);
        }

        dialog.showAndWait().ifPresent(patient -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CameraCaptureDialog.fxml"));
                Parent root = loader.load();

                CameraCaptureController controller = loader.getController();
                controller.setContext(patient.getId(), null, DocumentCategory.PRESCRIPTION);

                Stage stage = new Stage();
                stage.setTitle("Capture Prescription — " + patient.getFullName());
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setScene(new Scene(root));
                stage.setOnCloseRequest(e -> controller.releaseCamera());
                stage.showAndWait();

                if (controller.isSaved()) {
                    loadDocuments();
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to open camera dialog", e);
                showAlert("Failed to open camera dialog: " + e.getMessage());
            }
        });
    }

    private void promptPatientAndCategoryAndUpload(File file) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Upload Document Details");
        dialog.setHeaderText("Associate document: " + file.getName());

        ButtonType uploadButtonType = new ButtonType("Upload", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(uploadButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        ComboBox<Patient> patientCombo = new ComboBox<>();
        patientCombo.setPromptText("Select Patient...");
        try {
            patientCombo.setItems(FXCollections.observableArrayList(patientService.searchPatients("", false)));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load patients for upload", e);
        }

        ComboBox<DocumentCategory> categoryCombo = new ComboBox<>(FXCollections.observableArrayList(DocumentCategory.values()));
        categoryCombo.setValue(DocumentCategory.CLINICAL_DOCUMENT);

        content.getChildren().addAll(
                new Label("Patient:"), patientCombo,
                new Label("Document Category:"), categoryCombo
        );

        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(response -> {
            if (response == uploadButtonType) {
                Patient p = patientCombo.getValue();
                DocumentCategory cat = categoryCombo.getValue();
                if (p == null) {
                    showAlert("Patient selection is required.");
                    return;
                }

                // Asynchronous upload
                Task<DocumentMetadata> uploadTask = new Task<>() {
                    @Override
                    protected DocumentMetadata call() {
                        return documentService.uploadDocument(file, p.getId(), null, cat, file.getName(), Instant.now());
                    }
                };

                uploadTask.setOnSucceeded(e -> {
                    loadDocuments();
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Document uploaded successfully to MinIO object storage.", ButtonType.OK);
                    alert.showAndWait();
                });

                uploadTask.setOnFailed(e -> {
                    LOGGER.log(Level.SEVERE, "Upload task failed", uploadTask.getException());
                    showAlert("Upload failed: " + uploadTask.getException().getMessage());
                });

                Thread thread = new Thread(uploadTask, "Doc-Upload-Thread");
                thread.setDaemon(true);
                thread.start();
            }
        });
    }

    private void openPreview(DocumentMetadata doc) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DocumentPreviewDialog.fxml"));
            Parent root = loader.load();

            DocumentPreviewController controller = loader.getController();
            controller.setDocument(doc, documentService);
            controller.setOnRenamedCallback(this::loadDocuments);

            Stage stage = new Stage();
            stage.setTitle("Preview Document — " + doc.getOriginalFilename());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open document preview dialog", e);
            showAlert("Failed to preview document: " + e.getMessage());
        }
    }

    public void editDocumentFilename(DocumentMetadata doc) {
        if (doc == null) return;

        TextInputDialog dialog = new TextInputDialog(doc.getOriginalFilename());
        dialog.setTitle("Edit File Name");
        dialog.setHeaderText("Rename Document: " + doc.getOriginalFilename());
        dialog.setContentText("Enter new file name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.isBlank() || newName.trim().equals(doc.getOriginalFilename())) {
                return;
            }

            Task<DocumentMetadata> task = new Task<>() {
                @Override
                protected DocumentMetadata call() {
                    return documentService.updateFilename(doc.getId(), newName);
                }
            };

            task.setOnSucceeded(e -> {
                loadDocuments();
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "File name updated to:\n" + task.getValue().getOriginalFilename(), ButtonType.OK);
                alert.showAndWait();
            });

            task.setOnFailed(e -> {
                LOGGER.log(Level.WARNING, "Failed to rename document", task.getException());
                showAlert("Could not rename file: " + task.getException().getMessage());
            });

            Thread thread = new Thread(task, "Doc-Rename-Thread");
            thread.setDaemon(true);
            thread.start();
        });
    }

    private void saveDocumentToDisk(DocumentMetadata doc) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Document");
        chooser.setInitialFileName(doc.getOriginalFilename());

        Stage stage = (Stage) documentsTable.getScene().getWindow();
        File targetFile = chooser.showSaveDialog(stage);

        if (targetFile != null) {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    try (InputStream in = documentService.downloadDocument(doc.getId());
                         FileOutputStream out = new FileOutputStream(targetFile)) {
                        in.transferTo(out);
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Saved file to: " + targetFile.getAbsolutePath(), ButtonType.OK);
                a.showAndWait();
            });
            task.setOnFailed(e -> showAlert("Download failed: " + task.getException().getMessage()));

            Thread t = new Thread(task, "Download-Thread");
            t.setDaemon(true);
            t.start();
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
