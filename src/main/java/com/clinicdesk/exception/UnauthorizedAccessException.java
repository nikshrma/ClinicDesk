package com.clinicdesk.exception;

/**
 * Thrown when an authenticated user attempts an operation outside their assigned role permissions.
 */
public class UnauthorizedAccessException extends ClinicDeskException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
