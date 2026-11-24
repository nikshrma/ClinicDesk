package com.clinicdesk.storage.minio;

import com.clinicdesk.config.StorageConfig;
import com.clinicdesk.exception.DocumentStorageException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.storage.DocumentStorageService;
import com.clinicdesk.util.IdGenerator;
import io.minio.*;
import io.minio.http.Method;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MinIO S3-compatible implementation of DocumentStorageService.
 */
public class MinioDocumentStorageService implements DocumentStorageService {

    private static final Logger LOGGER = AppLogger.getLogger(MinioDocumentStorageService.class);

    private final MinioClient minioClient;
    private final String bucketName;
    private boolean bucketVerified = false;

    public MinioDocumentStorageService() {
        this.minioClient = StorageConfig.getClient();
        this.bucketName = StorageConfig.getBucketName();
    }

    public MinioDocumentStorageService(MinioClient minioClient, String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    private synchronized void ensureBucketExists() {
        if (bucketVerified) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                LOGGER.info("Creating private MinIO bucket: " + bucketName);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
            bucketVerified = true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to verify or create MinIO bucket: " + bucketName, e);
            throw new DocumentStorageException("Object storage bucket verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(File file, String contentType) throws DocumentStorageException {
        if (file == null || !file.exists()) {
            throw new DocumentStorageException("Source file does not exist or is null.");
        }

        ensureBucketExists();

        String extension = getFileExtension(file.getName());
        String objectKey = IdGenerator.nextId() + (extension.isEmpty() ? "" : "." + extension);

        try {
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .filename(file.getAbsolutePath())
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            LOGGER.info("Successfully uploaded object to MinIO: " + objectKey + " (" + file.length() + " bytes)");
            return objectKey;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to upload file to MinIO: " + file.getName(), e);
            throw new DocumentStorageException("Object storage upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) throws DocumentStorageException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new DocumentStorageException("Object key cannot be null or blank.");
        }

        ensureBucketExists();

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to download object from MinIO: " + objectKey, e);
            throw new DocumentStorageException("Object storage download failed for key: " + objectKey + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    public void delete(String objectKey) throws DocumentStorageException {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            LOGGER.info("Deleted object from MinIO: " + objectKey);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete object from MinIO: " + objectKey, e);
            throw new DocumentStorageException("Failed to delete object from storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey, int expirySeconds) throws DocumentStorageException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new DocumentStorageException("Object key is required.");
        }

        ensureBucketExists();

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(Math.max(60, expirySeconds), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate presigned URL for: " + objectKey, e);
            throw new DocumentStorageException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            return false;
        }
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
