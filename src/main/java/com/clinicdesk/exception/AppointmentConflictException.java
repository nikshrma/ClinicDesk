package com.clinicdesk.exception;

/**
 * Thrown when an appointment overlaps with an existing appointment for the same doctor.
 */
public class AppointmentConflictException extends ClinicDeskException {

    public AppointmentConflictException(String message) {
        super(message);
    }

    public AppointmentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
