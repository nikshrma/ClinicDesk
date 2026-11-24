package com.clinicdesk.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Entity representing metadata for documents and prescription photographs
 * whose binary content is stored securely in MinIO object storage.
 */
public class DocumentMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String patientId;
    private String medicalRecordId;
    private String uploadedBy;
    private String originalFilename;
    private String objectKey;
    private String mimeType;
    private long fileSize;
    private DocumentCategory category;
    private Instant captureTimestamp;
    private boolean archived;
    private Instant createdAt;

    // Joined fields for table display
    private String patientName;
    private String patientNumber;
    private String uploaderName;

    public DocumentMetadata() {
        this.category = DocumentCategory.OTHER;
        this.archived = false;
        this.createdAt = Instant.now();
    }

    public DocumentMetadata(String id, String patientId, String medicalRecordId, String uploadedBy,
                            String originalFilename, String objectKey, String mimeType, long fileSize,
                            DocumentCategory category, Instant captureTimestamp) {
        this.id = id;
        this.patientId = patientId;
        this.medicalRecordId = medicalRecordId;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.objectKey = objectKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.category = category != null ? category : DocumentCategory.OTHER;
        this.captureTimestamp = captureTimestamp;
        this.archived = false;
        this.createdAt = Instant.now();
    }

    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public boolean isImage() {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    public boolean isPdf() {
        return mimeType != null && mimeType.equalsIgnoreCase("application/pdf");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getMedicalRecordId() {
        return medicalRecordId;
    }

    public void setMedicalRecordId(String medicalRecordId) {
        this.medicalRecordId = medicalRecordId;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public void setCategory(DocumentCategory category) {
        this.category = category;
    }

    public Instant getCaptureTimestamp() {
        return captureTimestamp;
    }

    public void setCaptureTimestamp(Instant captureTimestamp) {
        this.captureTimestamp = captureTimestamp;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientNumber() {
        return patientNumber;
    }

    public void setPatientNumber(String patientNumber) {
        this.patientNumber = patientNumber;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentMetadata that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(objectKey, that.objectKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, objectKey);
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "id='" + id + '\'' +
                ", patientId='" + patientId + '\'' +
                ", filename='" + originalFilename + '\'' +
                ", category=" + category +
                ", size=" + getFormattedFileSize() +
                '}';
    }
}
