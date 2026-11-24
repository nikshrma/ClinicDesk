package com.clinicdesk.backup;

import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.model.MedicalRecord;
import com.clinicdesk.model.Patient;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable Data Transfer Object encapsulating a complete application data export.
 * Follows Java Object Serialization specification guidelines.
 */
public class BackupData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String version = "1.0.0";
    private Instant exportTimestamp;
    private String exportedBy;
    private String checksum;

    private List<Patient> patients = new ArrayList<>();
    private List<Appointment> appointments = new ArrayList<>();
    private List<MedicalRecord> medicalRecords = new ArrayList<>();
    private List<DocumentMetadata> documents = new ArrayList<>();

    public BackupData() {
        this.exportTimestamp = Instant.now();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getExportTimestamp() {
        return exportTimestamp;
    }

    public void setExportTimestamp(Instant exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    public String getExportedBy() {
        return exportedBy;
    }

    public void setExportedBy(String exportedBy) {
        this.exportedBy = exportedBy;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public List<Patient> getPatients() {
        return patients;
    }

    public void setPatients(List<Patient> patients) {
        this.patients = patients;
    }

    public List<Appointment> getAppointments() {
        return appointments;
    }

    public void setAppointments(List<Appointment> appointments) {
        this.appointments = appointments;
    }

    public List<MedicalRecord> getMedicalRecords() {
        return medicalRecords;
    }

    public void setMedicalRecords(List<MedicalRecord> medicalRecords) {
        this.medicalRecords = medicalRecords;
    }

    public List<DocumentMetadata> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentMetadata> documents) {
        this.documents = documents;
    }
}
