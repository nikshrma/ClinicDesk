package com.clinicdesk.model;

import java.util.Set;

/**
 * Enumeration representing the lifecycle states of an appointment,
 * with state-transition validation rules.
 */
public enum AppointmentStatus {
    SCHEDULED("Scheduled", "#0284C7", "#E0F2FE"),
    CONFIRMED("Confirmed", "#0369A1", "#BAE6FD"),
    CHECKED_IN("Checked In", "#15803D", "#DCFCE7"),
    COMPLETED("Completed", "#475569", "#F1F5F9"),
    CANCELLED("Cancelled", "#DC2626", "#FEE2E2"),
    NO_SHOW("No Show", "#D97706", "#FEF3C7");

    private final String displayName;
    private final String textColor;
    private final String bgColor;

    AppointmentStatus(String displayName, String textColor, String bgColor) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.bgColor = bgColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTextColor() {
        return textColor;
    }

    public String getBgColor() {
        return bgColor;
    }

    public static AppointmentStatus fromString(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Status string cannot be null or blank");
        }
        return AppointmentStatus.valueOf(statusStr.trim().toUpperCase());
    }

    /**
     * Determines whether transitioning from this status to targetStatus is legally permissible.
     */
    public boolean canTransitionTo(AppointmentStatus targetStatus) {
        if (this == targetStatus) {
            return true;
        }

        return switch (this) {
            case SCHEDULED -> Set.of(CONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW).contains(targetStatus);
            case CONFIRMED -> Set.of(CHECKED_IN, CANCELLED, NO_SHOW).contains(targetStatus);
            case CHECKED_IN -> Set.of(COMPLETED, CANCELLED).contains(targetStatus);
            case COMPLETED, CANCELLED, NO_SHOW -> false; // Terminal states
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }
}
