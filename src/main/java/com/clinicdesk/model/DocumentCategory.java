package com.clinicdesk.model;

/**
 * Enumeration representing medical document categories within ClinicDesk.
 */
public enum DocumentCategory {
    PRESCRIPTION("Prescription Photograph", "#0284C7", "#E0F2FE"),
    LAB_REPORT("Laboratory Report", "#7C3AED", "#EDE9FE"),
    MEDICAL_IMAGE("Medical Image / Scan", "#059669", "#D1FAE5"),
    DISCHARGE_SUMMARY("Discharge Summary", "#D97706", "#FEF3C7"),
    CLINICAL_DOCUMENT("Clinical Document", "#0369A1", "#BAE6FD"),
    OTHER("Other Document", "#475569", "#F1F5F9");

    private final String displayName;
    private final String textColor;
    private final String bgColor;

    DocumentCategory(String displayName, String textColor, String bgColor) {
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

    public static DocumentCategory fromString(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) {
            return OTHER;
        }
        try {
            return DocumentCategory.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
