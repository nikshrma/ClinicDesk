package com.clinicdesk.ui.controller;

import com.clinicdesk.backup.BackupData;
import com.clinicdesk.backup.BackupService;
import com.clinicdesk.config.AppConfig;
import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.config.StorageConfig;
import com.clinicdesk.dao.mysql.MySQLAppointmentDAO;
import com.clinicdesk.dao.mysql.MySQLDocumentDAO;
import com.clinicdesk.dao.mysql.MySQLMedicalRecordDAO;
import com.clinicdesk.dao.mysql.MySQLPatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the System Settings and Serialization Backup/Restore screen.
 */
public class SettingsBackupController {

    private static final Logger LOGGER = AppLogger.getLogger(SettingsBackupController.class);

    @FXML private Label dbStatusLabel;
    @FXML private Label minioStatusLabel;
    @FXML private Label javaVersionLabel;

    private final BackupService backupService;

    public SettingsBackupController() {
        MySQLPatientDAO patDao = new MySQLPatientDAO();
        MySQLAppointmentDAO appDao = new MySQLAppointmentDAO();
        MySQLMedicalRecordDAO mrDao = new MySQLMedicalRecordDAO();
        MySQLDocumentDAO docDao = new MySQLDocumentDAO();
        this.backupService = new BackupService(patDao, appDao, mrDao, docDao, DatabaseConfig.getDataSource());
    }

    public SettingsBackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @FXML
    public void initialize() {
        SessionContext.requireRole(Role.ADMIN);

        javaVersionLabel.setText("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        dbStatusLabel.setText("MySQL 8.0 — " + AppConfig.get("db.url", "jdbc:mysql://localhost:3306/clinicdesk"));
        minioStatusLabel.setText("MinIO S3 — Endpoint: " + AppConfig.get("minio.endpoint", "http://localhost:9000") +
                " (Bucket: " + StorageConfig.getBucketName() + ")");
    }

    @FXML
    public void handleExportBackup(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Application Backup");
        String defaultName = "clinicdesk_backup_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".cdbak";
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ClinicDesk Backup Files (*.cdbak)", "*.cdbak"));

        Stage stage = (Stage) dbStatusLabel.getScene().getWindow();
        File targetFile = chooser.showSaveDialog(stage);

        if (targetFile != null) {
            Task<BackupData> exportTask = new Task<>() {
                @Override
                protected BackupData call() throws Exception {
                    return backupService.exportBackup(targetFile);
                }
            };

            exportTask.setOnSucceeded(e -> {
                BackupData data = exportTask.getValue();
                String msg = String.format("Backup exported successfully to:\n%s\n\nStatistics:\n• Patients: %d\n• Medical Records: %d\n• Documents: %d\n• SHA-256 Checksum: %s",
                        targetFile.getAbsolutePath(),
                        data.getPatients().size(),
                        data.getMedicalRecords().size(),
                        data.getDocuments().size(),
                        data.getChecksum().substring(0, 16) + "...");

                Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
                alert.setHeaderText("Backup Export Succeeded");
                alert.showAndWait();
            });

            exportTask.setOnFailed(e -> {
                LOGGER.log(Level.SEVERE, "Backup export failed", exportTask.getException());
                showAlert("Backup export failed: " + exportTask.getException().getMessage());
            });

            Thread t = new Thread(exportTask, "Backup-Export-Thread");
            t.setDaemon(true);
            t.start();
        }
    }

    @FXML
    public void handleImportBackup(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File to Restore");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ClinicDesk Backup Files (*.cdbak)", "*.cdbak"));

        Stage stage = (Stage) dbStatusLabel.getScene().getWindow();
        File sourceFile = chooser.showOpenDialog(stage);

        if (sourceFile != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Restoring from backup will transactionally import and update patient and clinical records from:\n" +
                    sourceFile.getName() + "\n\nAre you sure you want to proceed?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Confirm Database Restoration");
            confirm.showAndWait();

            if (confirm.getResult() == ButtonType.YES) {
                Task<BackupData> importTask = new Task<>() {
                    @Override
                    protected BackupData call() throws Exception {
                        return backupService.importBackup(sourceFile);
                    }
                };

                importTask.setOnSucceeded(e -> {
                    BackupData data = importTask.getValue();
                    String msg = String.format("Restoration completed successfully!\n\nImported Records:\n• Patients: %d\n• Medical Records: %d\n• Documents: %d\n• Checksum Verified: OK",
                            data.getPatients().size(),
                            data.getMedicalRecords().size(),
                            data.getDocuments().size());

                    Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
                    alert.setHeaderText("Restoration Successful");
                    alert.showAndWait();
                });

                importTask.setOnFailed(e -> {
                    LOGGER.log(Level.SEVERE, "Backup restoration failed", importTask.getException());
                    showAlert("Restoration failed: " + importTask.getException().getMessage());
                });

                Thread t = new Thread(importTask, "Backup-Import-Thread");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}
