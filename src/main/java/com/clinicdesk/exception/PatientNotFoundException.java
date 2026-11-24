package com.clinicdesk.exception;

/**
 * Thrown when a queried patient ID or patient number does not exist or is inaccessible.
 */
public class PatientNotFoundException extends ClinicDeskException {

    public PatientNotFoundException(String message) {
        super(message);
    }

    public PatientNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
