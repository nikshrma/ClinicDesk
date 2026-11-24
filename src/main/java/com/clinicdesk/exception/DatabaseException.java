package com.clinicdesk.exception;

/**
 * Thrown when an unrecoverable database or JDBC operation fails.
 */
public class DatabaseException extends ClinicDeskException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
