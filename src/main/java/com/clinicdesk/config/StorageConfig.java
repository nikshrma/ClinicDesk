package com.clinicdesk.config;

import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.storage.DocumentStorageService;
import com.clinicdesk.storage.local.LocalDocumentStorageService;
import com.clinicdesk.storage.minio.MinioDocumentStorageService;
import io.minio.MinioClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages MinIO S3-compatible client configuration and lifecycle.
 * Also provides a resilient {@link DocumentStorageService} factory that
 * falls back to local filesystem storage when MinIO is unavailable.
 */
public final class StorageConfig {

    private static final Logger LOGGER = AppLogger.getLogger(StorageConfig.class);
    private static MinioClient minioClient;
    private static boolean initialized = false;

    /** Lazily-resolved active storage service (MinIO or local fallback) */
    private static volatile DocumentStorageService activeStorageService;

    private StorageConfig() {
        // Utility singleton
    }

    public static synchronized void initialize() {
        if (initialized && minioClient != null) {
            return;
        }

        try {
            String endpoint = AppConfig.get("minio.endpoint", "http://localhost:9000");
            String accessKey = AppConfig.get("minio.accessKey", "clinicadmin");
            String secretKey = AppConfig.get("minio.secretKey", "clinicpass123");
            String region = AppConfig.get("minio.region", "us-east-1");

            minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .region(region)
                    .build();

            initialized = true;
            LOGGER.info("MinIO client configured successfully for endpoint: " + endpoint);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize MinIO client", e);
        }
    }

    public static MinioClient getClient() {
        if (!initialized || minioClient == null) {
            initialize();
        }
        return minioClient;
    }

    public static String getBucketName() {
        return AppConfig.get("minio.bucketName", "clinicdesk-documents");
    }

    /**
     * Returns the best-available {@link DocumentStorageService}:
     * <ul>
     *   <li>MinIO if reachable</li>
     *   <li>Local filesystem fallback otherwise</li>
     * </ul>
     * The result is cached for the lifetime of the application.
     */
    public static DocumentStorageService getStorageService() {
        if (activeStorageService != null) {
            return activeStorageService;
        }

        synchronized (StorageConfig.class) {
            if (activeStorageService != null) {
                return activeStorageService;
            }

            // Try MinIO first
            try {
                MinioDocumentStorageService minioService = new MinioDocumentStorageService();
                if (minioService.isAvailable()) {
                    LOGGER.info("MinIO storage is reachable. Using MinIO as document storage backend.");
                    activeStorageService = minioService;
                    return activeStorageService;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "MinIO availability check failed: " + e.getMessage());
            }

            // Fall back to local storage
            LocalDocumentStorageService localService = new LocalDocumentStorageService();
            LOGGER.info("MinIO unavailable. Storing documents locally at: " +
                    System.getProperty("user.home") + java.io.File.separator + ".clinicdesk" +
                    java.io.File.separator + "documents");
            activeStorageService = localService;
            return activeStorageService;
        }
    }

    /**
     * Resets the cached storage service (useful for testing or reconnection attempts).
     */
    public static synchronized void resetStorageService() {
        activeStorageService = null;
    }
}
