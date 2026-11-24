package com.clinicdesk.exception;

/**
 * Thrown when the laptop camera device cannot be detected, opened, or initialized.
 */
public class CameraUnavailableException extends ClinicDeskException {

    public CameraUnavailableException(String message) {
        super(message);
    }

    public CameraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
