package com.clinicdesk.model;

/**
 * Enumeration representing gender options for patient records.
 */
public enum Gender {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other");

    private final String displayName;

    Gender(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Gender fromString(String genderStr) {
        if (genderStr == null || genderStr.isBlank()) {
            return null;
        }
        return Gender.valueOf(genderStr.trim().toUpperCase());
    }
}
