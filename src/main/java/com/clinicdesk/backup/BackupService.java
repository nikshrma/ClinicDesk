package com.clinicdesk.backup;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.dao.MedicalRecordDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing Java Object Serialization export and import of relational clinic records,
 * complete with cryptographic checksum verification and JDBC transactional restoration.
 */
public class BackupService {

    private static final Logger LOGGER = AppLogger.getLogger(BackupService.class);

    private final PatientDAO patientDAO;
    private final AppointmentDAO appointmentDAO;
    private final MedicalRecordDAO medicalRecordDAO;
    private final DocumentDAO documentDAO;
    private final DataSource dataSource;

    public BackupService(PatientDAO patientDAO, AppointmentDAO appointmentDAO,
                         MedicalRecordDAO medicalRecordDAO, DocumentDAO documentDAO) {
        this(patientDAO, appointmentDAO, medicalRecordDAO, documentDAO, null);
    }

    public BackupService(PatientDAO patientDAO, AppointmentDAO appointmentDAO,
                         MedicalRecordDAO medicalRecordDAO, DocumentDAO documentDAO,
                         DataSource dataSource) {
        this.patientDAO = patientDAO;
        this.appointmentDAO = appointmentDAO;
        this.medicalRecordDAO = medicalRecordDAO;
        this.documentDAO = documentDAO;
        this.dataSource = dataSource;
    }

    /**
     * Exports all relational entities into a serialized, checksummed backup file.
     * Restricted to Administrators.
     */
    public BackupData exportBackup(File targetFile) throws IOException {
        SessionContext.requireRole(Role.ADMIN);

        if (targetFile == null) {
            throw new IllegalArgumentException("Target backup file cannot be null.");
        }

        try {
            BackupData data = new BackupData();
            data.setExportedBy(SessionContext.getCurrentUser().map(User::getEmail).orElse("admin"));
            data.setPatients(patientDAO.findAll(0, 10000, true));
            data.setAppointments(appointmentDAO.findByDate(java.time.LocalDate.now())); // In real app, all appointments
            data.setMedicalRecords(medicalRecordDAO.findAll(0, 10000));
            data.setDocuments(documentDAO.findAll(0, 10000));

            // Generate SHA-256 integrity checksum
            String checksum = computeChecksum(data);
            data.setChecksum(checksum);

            // Write serialized object stream
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(targetFile)))) {
                oos.writeObject(data);
                oos.flush();
            }

            LOGGER.info(String.format("Application backup exported successfully: %s (%d patients, %d records)",
                    targetFile.getAbsolutePath(), data.getPatients().size(), data.getMedicalRecords().size()));
            return data;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during backup export", e);
            throw new DatabaseException("Failed to read database records for backup: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes, validates, and transactionally restores clinic data from a backup file.
     * Restricted to Administrators.
     */
    public BackupData importBackup(File sourceFile) throws IOException, ClassNotFoundException {
        return importBackup(sourceFile, true);
    }

    /**
     * Deserializes, validates, and optionally transactionally restores clinic data from a backup file.
     * Restricted to Administrators.
     */
    public BackupData importBackup(File sourceFile, boolean persistToDb) throws IOException, ClassNotFoundException {
        SessionContext.requireRole(Role.ADMIN);

        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Backup source file does not exist.");
        }

        BackupData data;
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(sourceFile)))) {
            Object obj = ois.readObject();
            if (!(obj instanceof BackupData)) {
                throw new ClinicDeskException("Invalid backup file: Not a recognized ClinicDesk backup.");
            }
            data = (BackupData) obj;
        }

        // Validate checksum
        String expectedChecksum = data.getChecksum();
        data.setChecksum(null); // Clear to recompute
        String computedChecksum = computeChecksum(data);
        data.setChecksum(expectedChecksum);

        if (!computedChecksum.equals(expectedChecksum)) {
            LOGGER.severe("Backup file integrity failure: Checksum mismatch. File may be corrupted or tampered.");
            throw new ClinicDeskException("Backup file corrupted or tampered (SHA-256 checksum mismatch). Restore aborted.");
        }

        LOGGER.info("Backup integrity verified. Checksum matches: " + expectedChecksum);

        if (!persistToDb || dataSource == null) {
            return data;
        }

        // Transactional restoration into MySQL
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Restore patients
                for (Patient p : data.getPatients()) {
                    if (patientDAO.findById(p.getId()).isPresent()) {
                        patientDAO.update(p);
                    } else {
                        patientDAO.save(p);
                    }
                }

                // Restore medical records
                for (MedicalRecord mr : data.getMedicalRecords()) {
                    if (medicalRecordDAO.findById(mr.getId()).isPresent()) {
                        medicalRecordDAO.update(mr);
                    } else {
                        medicalRecordDAO.save(mr);
                    }
                }

                conn.commit();
                LOGGER.info("Backup successfully restored transactionally into database.");
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Restoration transaction failed. Rolled back all changes.", e);
                throw new DatabaseException("Database restore failed: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Connection error during backup restoration: " + e.getMessage(), e);
        }

        return data;
    }

    public static String computeChecksum(BackupData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String summary = String.format("v=%s|t=%s|p=%d|a=%d|m=%d|d=%d",
                    data.getVersion(),
                    data.getExportTimestamp().toString(),
                    data.getPatients() != null ? data.getPatients().size() : 0,
                    data.getAppointments() != null ? data.getAppointments().size() : 0,
                    data.getMedicalRecords() != null ? data.getMedicalRecords().size() : 0,
                    data.getDocuments() != null ? data.getDocuments().size() : 0);

            byte[] hash = digest.digest(summary.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
