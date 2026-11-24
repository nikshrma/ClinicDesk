package com.clinicdesk.ui.controller;

import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.service.DocumentService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Document Preview modal window.
 */
public class DocumentPreviewController {

    private static final Logger LOGGER = AppLogger.getLogger(DocumentPreviewController.class);

    @FXML private Label filenameLabel;
    @FXML private Label categoryBadge;
    @FXML private Label metaInfoLabel;
    @FXML private ImageView previewImageView;
    @FXML private VBox nonImagePreviewPane;

    private DocumentMetadata document;
    private DocumentService documentService;

    public void setDocument(DocumentMetadata doc, DocumentService docService) {
        this.document = doc;
        this.documentService = docService;

        filenameLabel.setText(doc.getOriginalFilename());
        categoryBadge.setText(doc.getCategory().getDisplayName().toUpperCase());
        categoryBadge.setStyle("-fx-background-color: " + doc.getCategory().getBgColor() +
                "; -fx-text-fill: " + doc.getCategory().getTextColor() +
                "; -fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 11px;");

        String dateStr = doc.getCreatedAt() != null ? doc.getCreatedAt().toString().substring(0, 10) : "—";
        metaInfoLabel.setText("Size: " + doc.getFormattedFileSize() + " | Uploaded: " + dateStr +
                (doc.getUploaderName() != null ? " by " + doc.getUploaderName() : ""));

        loadPreviewContent();
    }

    private void loadPreviewContent() {
        if (document.isImage()) {
            previewImageView.setVisible(true);
            previewImageView.setManaged(true);
            nonImagePreviewPane.setVisible(false);
            nonImagePreviewPane.setManaged(false);

            // Asynchronously fetch image stream
            Task<Image> loadTask = new Task<>() {
                @Override
                protected Image call() throws Exception {
                    try (InputStream in = documentService.downloadDocument(document.getId())) {
                        return new Image(in);
                    }
                }
            };

            loadTask.setOnSucceeded(e -> previewImageView.setImage(loadTask.getValue()));
            loadTask.setOnFailed(e -> {
                LOGGER.log(Level.WARNING, "Failed to render image preview", loadTask.getException());
                showAlert("Failed to load image preview: " + loadTask.getException().getMessage());
            });

            Thread thread = new Thread(loadTask, "Preview-Load-Thread");
            thread.setDaemon(true);
            thread.start();
        } else {
            previewImageView.setVisible(false);
            previewImageView.setManaged(false);
            nonImagePreviewPane.setVisible(true);
            nonImagePreviewPane.setManaged(true);
        }
    }

    @FXML
    public void handleDownload(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Document");
        chooser.setInitialFileName(document.getOriginalFilename());

        Stage stage = (Stage) filenameLabel.getScene().getWindow();
        File targetFile = chooser.showSaveDialog(stage);

        if (targetFile != null) {
            Task<Void> downloadTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    try (InputStream in = documentService.downloadDocument(document.getId());
                         FileOutputStream out = new FileOutputStream(targetFile)) {
                        in.transferTo(out);
                    }
                    return null;
                }
            };

            downloadTask.setOnSucceeded(e -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "File downloaded successfully:\n" + targetFile.getAbsolutePath(), ButtonType.OK);
                alert.showAndWait();
            });

            downloadTask.setOnFailed(e -> {
                LOGGER.log(Level.SEVERE, "File download failed", downloadTask.getException());
                showAlert("File download failed: " + downloadTask.getException().getMessage());
            });

            Thread thread = new Thread(downloadTask, "Download-Thread");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @FXML
    public void handleClose(ActionEvent event) {
        Stage stage = (Stage) filenameLabel.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
