package com.clinicdesk.model;

/**
 * Enumeration representing user access roles within ClinicDesk.
 */
public enum Role {
    ADMIN("Administrator"),
    DOCTOR("Doctor"),
    RECEPTIONIST("Receptionist");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Role fromString(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            throw new IllegalArgumentException("Role string cannot be null or blank");
        }
        return Role.valueOf(roleStr.trim().toUpperCase());
    }
}
