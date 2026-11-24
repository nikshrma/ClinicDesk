package com.clinicdesk.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entity representing a patient's clinical encounter record.
 * Preserves longitudinal medical history with chief complaint and diagnosis,
 * while detailed notes/prescriptions are attached as documents.
 */
public class MedicalRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String patientId;
    private String appointmentId;
    private String doctorId;
    private LocalDate visitDate;
    private String chiefComplaint;
    private String diagnosis;
    private LocalDate followUpDate;
    private Instant createdAt;
    private Instant updatedAt;

    // Display fields joined from other tables
    private String patientName;
    private String patientNumber;
    private String doctorName;

    public MedicalRecord() {
        this.visitDate = LocalDate.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public MedicalRecord(String id, String patientId, String appointmentId, String doctorId,
                         LocalDate visitDate, String chiefComplaint, String diagnosis, LocalDate followUpDate) {
        this.id = id;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.visitDate = visitDate != null ? visitDate : LocalDate.now();
        this.chiefComplaint = chiefComplaint;
        this.diagnosis = diagnosis;
        this.followUpDate = followUpDate;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public String getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(String appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
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
        if (!(o instanceof MedicalRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MedicalRecord{" +
                "id='" + id + '\'' +
                ", patientId='" + patientId + '\'' +
                ", appointmentId='" + appointmentId + '\'' +
                ", visitDate=" + visitDate +
                ", diagnosis='" + diagnosis + '\'' +
                '}';
    }
}
