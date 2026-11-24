package com.clinicdesk.config;

import com.clinicdesk.logging.AppLogger;
import io.minio.MinioClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages MinIO S3-compatible client configuration and lifecycle.
 */
public final class StorageConfig {

    private static final Logger LOGGER = AppLogger.getLogger(StorageConfig.class);
    private static MinioClient minioClient;
    private static boolean initialized = false;

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
}
