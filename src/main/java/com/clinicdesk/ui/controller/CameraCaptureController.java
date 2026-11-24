package com.clinicdesk.ui.controller;

import com.clinicdesk.camera.CameraService;
import com.clinicdesk.camera.ImageProcessingService;
import com.clinicdesk.camera.OpenCVCameraService;
import com.clinicdesk.dao.mysql.MySQLDocumentDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.DocumentCategory;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.service.DocumentService;
import com.clinicdesk.service.PatientService;
import com.clinicdesk.storage.minio.MinioDocumentStorageService;
import com.clinicdesk.ui.util.FxImageUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the live laptop camera prescription and document capture interface.
 */
public class CameraCaptureController {

    private static final Logger LOGGER = AppLogger.getLogger(CameraCaptureController.class);

    @FXML private Label dialogTitle;
    @FXML private Label dialogSubtitle;
    @FXML private Label statusBadge;
    @FXML private ImageView viewportImage;
    @FXML private HBox adjustmentBar;

    @FXML private Button btnCancel;
    @FXML private Button btnCapture;
    @FXML private Button btnRetake;
    @FXML private Button btnConfirm;

    private final CameraService cameraService;
    private final ImageProcessingService imageProcessingService;
    private final DocumentService documentService;

    private String patientId;
    private String medicalRecordId;
    private DocumentCategory category = DocumentCategory.PRESCRIPTION;

    private BufferedImage capturedPhoto;
    private boolean saved = false;

    public CameraCaptureController() {
        this.cameraService = new OpenCVCameraService();
        this.imageProcessingService = new ImageProcessingService();
        MySQLDocumentDAO docDao = new MySQLDocumentDAO();
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        PatientService patService = new PatientService(patDao);
        this.documentService = new DocumentService(docDao, new MinioDocumentStorageService(), patService);
    }

    public CameraCaptureController(CameraService cameraService, ImageProcessingService imageProcessingService, DocumentService documentService) {
        this.cameraService = cameraService;
        this.imageProcessingService = imageProcessingService;
        this.documentService = documentService;
    }

    public void setContext(String patientId, String medicalRecordId, DocumentCategory category) {
        this.patientId = patientId;
        this.medicalRecordId = medicalRecordId;
        if (category != null) {
            this.category = category;
        }
        dialogTitle.setText(this.category.getDisplayName() + " — Camera Capture");
    }

    @FXML
    public void initialize() {
        startCameraStream();
    }

    private void startCameraStream() {
        try {
            cameraService.initialize(0);
            cameraService.startLiveStream(frame -> {
                Image fxImage = FxImageUtils.toFXImage(frame);
                Platform.runLater(() -> {
                    if (capturedPhoto == null) {
                        viewportImage.setImage(fxImage);
                    }
                });
            });
            setLiveState();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start camera live stream", e);
            showAlert("Could not access laptop camera: " + e.getMessage());
        }
    }

    @FXML
    public void handleCapture(ActionEvent event) {
        try {
            capturedPhoto = cameraService.captureFrame();
            cameraService.stopLiveStream();
            setReviewState();
            updateViewportWithCapturedPhoto();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to capture snapshot frame", e);
            showAlert("Capture failed: " + e.getMessage());
        }
    }

    @FXML
    public void handleRotateClockwise(ActionEvent event) {
        if (capturedPhoto != null) {
            capturedPhoto = imageProcessingService.rotate(capturedPhoto, 90);
            updateViewportWithCapturedPhoto();
        }
    }

    @FXML
    public void handleRotateCounterClockwise(ActionEvent event) {
        if (capturedPhoto != null) {
            capturedPhoto = imageProcessingService.rotate(capturedPhoto, 270);
            updateViewportWithCapturedPhoto();
        }
    }

    @FXML
    public void handleRotate180(ActionEvent event) {
        if (capturedPhoto != null) {
            capturedPhoto = imageProcessingService.rotate(capturedPhoto, 180);
            updateViewportWithCapturedPhoto();
        }
    }

    @FXML
    public void handleFlipHorizontal(ActionEvent event) {
        if (capturedPhoto != null) {
            capturedPhoto = imageProcessingService.flipHorizontal(capturedPhoto);
            updateViewportWithCapturedPhoto();
        }
    }

    @FXML
    public void handleFlipVertical(ActionEvent event) {
        if (capturedPhoto != null) {
            capturedPhoto = imageProcessingService.flipVertical(capturedPhoto);
            updateViewportWithCapturedPhoto();
        }
    }

    @FXML
    public void handleRetake(ActionEvent event) {
        capturedPhoto = null;
        startCameraStream();
    }

    @FXML
    public void handleConfirm(ActionEvent event) {
        if (capturedPhoto == null) {
            return;
        }

        btnConfirm.setDisable(true);
        btnRetake.setDisable(true);

        Task<DocumentMetadata> saveTask = new Task<>() {
            @Override
            protected DocumentMetadata call() throws Exception {
                // Save captured photograph to temporary JPEG file
                String timestampStr = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
                String filename = category.name().toLowerCase() + "_" + timestampStr + ".jpg";
                File tempFile = File.createTempFile("capture_", ".jpg");
                tempFile.deleteOnExit();

                imageProcessingService.saveAsJpeg(capturedPhoto, tempFile, 0.95f);

                // Release camera hardware locks immediately
                cameraService.release();

                // Upload to MinIO & save metadata in MySQL
                return documentService.uploadDocument(tempFile, patientId, medicalRecordId, category, filename, Instant.now());
            }
        };

        saveTask.setOnSucceeded(e -> {
            saved = true;
            LOGGER.info("Captured photograph saved successfully.");
            closeDialog();
        });

        saveTask.setOnFailed(e -> {
            btnConfirm.setDisable(false);
            btnRetake.setDisable(false);
            LOGGER.log(Level.SEVERE, "Failed to save captured photograph", saveTask.getException());
            showAlert("Failed to save photograph: " + saveTask.getException().getMessage());
        });

        Thread t = new Thread(saveTask, "Camera-Save-Thread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        releaseCamera();
        closeDialog();
    }

    public void releaseCamera() {
        try {
            cameraService.release();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error releasing camera resources", e);
        }
    }

    public boolean isSaved() {
        return saved;
    }

    private void updateViewportWithCapturedPhoto() {
        if (capturedPhoto != null) {
            Image fxImg = FxImageUtils.toFXImage(capturedPhoto);
            viewportImage.setImage(fxImg);
        }
    }

    private void setLiveState() {
        statusBadge.setText("LIVE FEED (30 FPS)");
        statusBadge.setStyle("-fx-background-color: #064E3B; -fx-text-fill: #34D399; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-background-radius: 12px;");

        adjustmentBar.setVisible(false);
        adjustmentBar.setManaged(false);

        btnCapture.setVisible(true);
        btnCapture.setManaged(true);
        btnRetake.setVisible(false);
        btnRetake.setManaged(false);
        btnConfirm.setVisible(false);
        btnConfirm.setManaged(false);
    }

    private void setReviewState() {
        statusBadge.setText("SNAPSHOT READY — ADJUST AS NEEDED");
        statusBadge.setStyle("-fx-background-color: #1E3A8A; -fx-text-fill: #93C5FD; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-background-radius: 12px;");

        adjustmentBar.setVisible(true);
        adjustmentBar.setManaged(true);

        btnCapture.setVisible(false);
        btnCapture.setManaged(false);
        btnRetake.setVisible(true);
        btnRetake.setManaged(true);
        btnConfirm.setVisible(true);
        btnConfirm.setManaged(true);
    }

    private void closeDialog() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
