package com.clinicdesk.storage.local;

import com.clinicdesk.exception.DocumentStorageException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.storage.DocumentStorageService;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local filesystem implementation of {@link DocumentStorageService}.
 * Stores documents under {@code ~/.clinicdesk/documents/} when MinIO is unavailable,
 * enabling full offline operation on a standalone Windows laptop.
 */
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final Logger LOGGER = AppLogger.getLogger(LocalDocumentStorageService.class);

    private final Path storageRoot;

    /**
     * Constructs the service with the default local storage directory:
     * {@code <user.home>/.clinicdesk/documents}
     */
    public LocalDocumentStorageService() {
        this(Path.of(System.getProperty("user.home"), ".clinicdesk", "documents"));
    }

    /**
     * Constructs the service with a custom storage directory (for testing).
     */
    public LocalDocumentStorageService(Path storageRoot) {
        this.storageRoot = storageRoot;
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(storageRoot)) {
                Files.createDirectories(storageRoot);
                LOGGER.info("Created local document storage directory: " + storageRoot.toAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create local document storage directory: " + storageRoot, e);
        }
    }

    @Override
    public String upload(File file, String contentType) throws DocumentStorageException {
        if (file == null || !file.exists()) {
            throw new DocumentStorageException("Source file does not exist or is null.");
        }

        ensureDirectoryExists();

        String objectKey = UUID.randomUUID().toString() + "_" + file.getName();

        Path targetPath = storageRoot.resolve(objectKey);
        try {
            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Successfully stored document locally: " + objectKey + " (" + file.length() + " bytes) at " + targetPath.toAbsolutePath());
            return objectKey;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy file to local storage: " + file.getName(), e);
            throw new DocumentStorageException("Local storage upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) throws DocumentStorageException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new DocumentStorageException("Object key cannot be null or blank.");
        }

        Path filePath = storageRoot.resolve(objectKey);
        if (!Files.exists(filePath)) {
            throw new DocumentStorageException("Local file not found for key: " + objectKey);
        }

        try {
            return new FileInputStream(filePath.toFile());
        } catch (FileNotFoundException e) {
            throw new DocumentStorageException("Failed to open local file for key: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) throws DocumentStorageException {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        Path filePath = storageRoot.resolve(objectKey);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                LOGGER.info("Deleted local document: " + objectKey);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete local file: " + objectKey, e);
            throw new DocumentStorageException("Failed to delete local document: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, int expirySeconds) throws DocumentStorageException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new DocumentStorageException("Object key is required.");
        }

        Path filePath = storageRoot.resolve(objectKey);
        if (!Files.exists(filePath)) {
            throw new DocumentStorageException("Local file not found for key: " + objectKey);
        }

        // Return a local file:// URI for preview
        return filePath.toUri().toString();
    }

    @Override
    public boolean isAvailable() {
        try {
            if (!Files.exists(storageRoot)) {
                Files.createDirectories(storageRoot);
            }
            return Files.isWritable(storageRoot);
        } catch (IOException e) {
            return false;
        }
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
