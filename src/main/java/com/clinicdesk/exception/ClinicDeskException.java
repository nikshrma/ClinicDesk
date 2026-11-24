package com.clinicdesk.exception;

/**
 * Root domain runtime exception for the ClinicDesk application.
 */
public class ClinicDeskException extends RuntimeException {

    public ClinicDeskException(String message) {
        super(message);
    }

    public ClinicDeskException(String message, Throwable cause) {
        super(message, cause);
    }
}
