package com.clinicdesk.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a scheduled clinical consultation or follow-up visit.
 */
public class Appointment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String patientId;
    private String doctorId;
    private LocalDateTime scheduledAt;
    private int durationMinutes;
    private String reason;
    private AppointmentStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // Joined fields for display in tables and schedules
    private String patientName;
    private String patientNumber;
    private String doctorName;

    public Appointment() {
        this.durationMinutes = 30;
        this.status = AppointmentStatus.SCHEDULED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Appointment(String id, String patientId, String doctorId, LocalDateTime scheduledAt,
                       int durationMinutes, String reason, AppointmentStatus status) {
        this.id = id;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
        this.reason = reason;
        this.status = status != null ? status : AppointmentStatus.SCHEDULED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public LocalDateTime getEndDateTime() {
        if (scheduledAt == null) {
            return null;
        }
        return scheduledAt.plusMinutes(durationMinutes);
    }

    public boolean overlapsWith(LocalDateTime otherStart, LocalDateTime otherEnd) {
        if (scheduledAt == null || otherStart == null || otherEnd == null) {
            return false;
        }
        LocalDateTime thisEnd = getEndDateTime();
        return scheduledAt.isBefore(otherEnd) && thisEnd.isAfter(otherStart);
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

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Appointment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Appointment{" +
                "id='" + id + '\'' +
                ", patientId='" + patientId + '\'' +
                ", doctorId='" + doctorId + '\'' +
                ", scheduledAt=" + scheduledAt +
                ", status=" + status +
                '}';
    }
}
