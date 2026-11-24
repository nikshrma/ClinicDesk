package com.clinicdesk.storage;

import com.clinicdesk.exception.DocumentStorageException;

import java.io.File;
import java.io.InputStream;

/**
 * Abstraction interface for object storage interactions (MinIO / S3).
 * Storage-specific mechanics remain fully decoupled from application business logic.
 */
public interface DocumentStorageService {

    /**
     * Uploads a file to object storage under a unique key.
     *
     * @param file        the local source file
     * @param contentType MIME content type (e.g. image/jpeg, application/pdf)
     * @return the unique object storage key generated for this file
     * @throws DocumentStorageException if upload fails
     */
    String upload(File file, String contentType) throws DocumentStorageException;

    /**
     * Downloads an object as an input stream.
     *
     * @param objectKey the unique storage key
     * @return an InputStream to read the binary content
     * @throws DocumentStorageException if retrieval fails
     */
    InputStream download(String objectKey) throws DocumentStorageException;

    /**
     * Deletes an object from storage (used also in compensation logic).
     *
     * @param objectKey the unique storage key
     * @throws DocumentStorageException if deletion fails
     */
    void delete(String objectKey) throws DocumentStorageException;

    /**
     * Generates a short-lived presigned URL for secure authenticated document previewing.
     *
     * @param objectKey     the unique storage key
     * @param expirySeconds duration in seconds for which the URL remains valid
     * @return presigned URL string
     * @throws DocumentStorageException if generation fails
     */
    String generatePresignedUrl(String objectKey, int expirySeconds) throws DocumentStorageException;

    /**
     * Checks whether the underlying storage system is reachable and operational.
     */
    boolean isAvailable();
}
