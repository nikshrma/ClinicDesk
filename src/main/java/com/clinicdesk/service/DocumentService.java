package com.clinicdesk.service;

import com.clinicdesk.config.AppConfig;
import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.exception.DocumentStorageException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.storage.DocumentStorageService;
import com.clinicdesk.util.IdGenerator;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating secure document uploads, MIME validation,
 * MinIO object storage interactions, and MySQL-MinIO atomic compensation rollback.
 */
public class DocumentService {

    private static final Logger LOGGER = AppLogger.getLogger(DocumentService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    private final DocumentDAO documentDAO;
    private final DocumentStorageService storageService;
    private final PatientService patientService;

    public DocumentService(DocumentDAO documentDAO, DocumentStorageService storageService, PatientService patientService) {
        this.documentDAO = documentDAO;
        this.storageService = storageService;
        this.patientService = patientService;
    }

    /**
     * Uploads a medical document with MIME validation and compensation rollback.
     */
    public DocumentMetadata uploadDocument(File file, String patientId, String medicalRecordId,
                                           DocumentCategory category, String originalFilename,
                                           Instant captureTimestamp) {

        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);

        // Prescription category requires clinical privileges
        if (category == DocumentCategory.PRESCRIPTION) {
            SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        }

        // Validate patient existence
        Patient patient = patientService.getPatientById(patientId);

        // Validate file
        validateFile(file, originalFilename);

        String mimeType = determineMimeType(file, originalFilename);
        User uploader = SessionContext.getCurrentUser().orElseThrow();

        // Step 1: Upload to MinIO object storage
        String objectKey;
        try {
            objectKey = storageService.upload(file, mimeType);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Object storage upload failed for file: " + originalFilename, e);
            throw new DocumentStorageException("Failed to upload file to object storage: " + e.getMessage(), e);
        }

        // Step 2: Insert document metadata into MySQL within transaction
        try {
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setId(IdGenerator.nextId());
            metadata.setPatientId(patient.getId());
            metadata.setMedicalRecordId(medicalRecordId);
            metadata.setUploadedBy(uploader.getId());
            metadata.setOriginalFilename(originalFilename != null ? originalFilename : file.getName());
            metadata.setObjectKey(objectKey);
            metadata.setMimeType(mimeType);
            metadata.setFileSize(file.length());
            metadata.setCategory(category != null ? category : DocumentCategory.OTHER);
            metadata.setCaptureTimestamp(captureTimestamp);

            documentDAO.save(metadata);
            LOGGER.info("Document successfully uploaded and saved: " + metadata.getId() + " (" + originalFilename + ")");
            return metadata;
        } catch (Exception e) {
            // Step 3: COMPENSATION! Database failure -> immediately purge uploaded file from MinIO
            LOGGER.log(Level.SEVERE, "Database error saving document metadata. Initiating compensation rollback in MinIO...", e);
            try {
                storageService.delete(objectKey);
                LOGGER.info("Compensation rollback successful: purged object " + objectKey + " from MinIO.");
            } catch (Exception compEx) {
                LOGGER.log(Level.SEVERE, "Compensation cleanup failed for key: " + objectKey, compEx);
            }
            throw new DocumentStorageException("Failed to persist document metadata. Storage upload rolled back: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads document content stream by document ID.
     */
    public InputStream downloadDocument(String documentId) {
        DocumentMetadata metadata = getDocumentById(documentId);
        return storageService.download(metadata.getObjectKey());
    }

    /**
     * Generates a temporary authenticated presigned preview URL.
     */
    public String getPresignedUrl(String documentId, int expirySeconds) {
        DocumentMetadata metadata = getDocumentById(documentId);
        return storageService.generatePresignedUrl(metadata.getObjectKey(), expirySeconds);
    }

    public DocumentMetadata getDocumentById(String documentId) {
        try {
            Optional<DocumentMetadata> opt = documentDAO.findById(documentId);
            if (opt.isEmpty()) {
                throw new IllegalArgumentException("Document not found with ID: " + documentId);
            }
            return opt.get();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load document: " + documentId, e);
            throw new DatabaseException("Failed to load document metadata: " + e.getMessage(), e);
        }
    }

    public List<DocumentMetadata> getDocumentsForPatient(String patientId) {
        try {
            return documentDAO.findByPatientId(patientId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch documents for patient: " + patientId, e);
            throw new DatabaseException("Failed to load patient documents: " + e.getMessage(), e);
        }
    }

    public List<DocumentMetadata> getDocumentsForMedicalRecord(String recordId) {
        try {
            return documentDAO.findByMedicalRecordId(recordId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch documents for record: " + recordId, e);
            throw new DatabaseException("Failed to load encounter documents: " + e.getMessage(), e);
        }
    }

    public List<DocumentMetadata> getRecentDocuments(int limit) {
        try {
            return documentDAO.findRecent(limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load recent documents", e);
            throw new DatabaseException("Failed to load documents: " + e.getMessage(), e);
        }
    }

    public List<DocumentMetadata> getDocumentsByCategory(DocumentCategory category) {
        try {
            return documentDAO.findByCategory(category);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load documents by category: " + category, e);
            throw new DatabaseException("Failed to load documents: " + e.getMessage(), e);
        }
    }

    public void archiveDocument(String documentId) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        try {
            documentDAO.archive(documentId);
            LOGGER.info("Document archived: " + documentId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to archive document: " + documentId, e);
            throw new DatabaseException("Failed to archive document: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the user-facing original filename of an existing document.
     * Preserves the existing file extension if omitted by the user, and validates
     * that the extension is supported.
     *
     * @param documentId the ID of the document to rename
     * @param newFilename the new filename
     * @return the updated DocumentMetadata
     */
    public DocumentMetadata updateFilename(String documentId, String newFilename) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);

        if (newFilename == null || newFilename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty.");
        }

        String cleanName = newFilename.trim();
        if (cleanName.length() > 255) {
            throw new IllegalArgumentException("Filename exceeds maximum length of 255 characters.");
        }

        DocumentMetadata existing = getDocumentById(documentId);

        // If user omitted file extension, preserve existing extension
        if (!cleanName.contains(".") && existing.getOriginalFilename() != null && existing.getOriginalFilename().contains(".")) {
            String ext = existing.getOriginalFilename().substring(existing.getOriginalFilename().lastIndexOf('.'));
            cleanName = cleanName + ext;
        }

        // Validate extension
        int dot = cleanName.lastIndexOf('.');
        if (dot < 0 || dot == cleanName.length() - 1) {
            throw new IllegalArgumentException("Filename must have an extension (.jpg, .jpeg, .png, .webp, .pdf).");
        }
        String ext = cleanName.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file format ." + ext + ". Allowed formats: JPG, PNG, WebP, PDF.");
        }

        try {
            documentDAO.updateFilename(documentId, cleanName);
            existing.setOriginalFilename(cleanName);
            LOGGER.info("Document filename updated successfully: " + documentId + " -> " + cleanName);
            return existing;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update filename for document: " + documentId, e);
            throw new DatabaseException("Failed to update document filename: " + e.getMessage(), e);
        }
    }

    private void validateFile(File file, String filename) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Target file does not exist or is invalid.");
        }

        long maxBytes = (long) AppConfig.getInt("app.upload.maxFileSizeMb", 10) * 1024 * 1024;
        if (file.length() > maxBytes) {
            throw new IllegalArgumentException(String.format("File size (%d MB) exceeds maximum permitted limit (%d MB).",
                    file.length() / (1024 * 1024), maxBytes / (1024 * 1024)));
        }

        String name = filename != null ? filename : file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("File must have an extension (.jpg, .png, .webp, .pdf).");
        }

        String ext = name.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file format ." + ext + ". Allowed formats: JPG, PNG, WebP, PDF.");
        }
    }

    private String determineMimeType(File file, String filename) {
        String name = filename != null ? filename : file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase() : "";

        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
