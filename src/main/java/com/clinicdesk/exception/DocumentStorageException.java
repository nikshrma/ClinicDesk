package com.clinicdesk.exception;

/**
 * Thrown when an object storage (MinIO) upload, download, or compensation action fails.
 */
public class DocumentStorageException extends ClinicDeskException {

    public DocumentStorageException(String message) {
        super(message);
    }

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
