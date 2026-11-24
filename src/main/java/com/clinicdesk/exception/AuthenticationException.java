package com.clinicdesk.exception;

/**
 * Thrown when credentials fail or an account is inactive during authentication.
 */
public class AuthenticationException extends ClinicDeskException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
