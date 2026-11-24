package com.clinicdesk.exception;

/**
 * Thrown when an illegal state transition is attempted on an appointment
 * (e.g., rescheduling a completed appointment or completing a cancelled appointment).
 */
public class InvalidAppointmentStateException extends ClinicDeskException {

    public InvalidAppointmentStateException(String message) {
        super(message);
    }

    public InvalidAppointmentStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
