package com.clinicdesk.service;

import com.clinicdesk.dao.MedicalRecordDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.util.IdGenerator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service encapsulating business rules, longitudinal record integrity,
 * and permissions for clinical Medical Records.
 */
public class MedicalRecordService {

    private static final Logger LOGGER = AppLogger.getLogger(MedicalRecordService.class);

    private final MedicalRecordDAO medicalRecordDAO;
    private final PatientService patientService;
    private final AppointmentService appointmentService;

    public MedicalRecordService(MedicalRecordDAO medicalRecordDAO, PatientService patientService, AppointmentService appointmentService) {
        this.medicalRecordDAO = medicalRecordDAO;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
    }

    /**
     * Creates a medical record for a patient encounter and marks the appointment COMPLETED.
     * Strictly restricted to Doctors and Administrators.
     */
    public MedicalRecord createMedicalRecord(MedicalRecord record) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);

        validateRecordInput(record);

        // Verify patient existence
        Patient patient = patientService.getPatientById(record.getPatientId());

        // Verify appointment existence and ensure it does not already have a medical record
        Appointment appointment = appointmentService.getAppointmentById(record.getAppointmentId());

        try {
            Optional<MedicalRecord> existing = medicalRecordDAO.findByAppointmentId(record.getAppointmentId());
            if (existing.isPresent()) {
                throw new IllegalArgumentException("A medical record has already been recorded for this appointment (" +
                        record.getAppointmentId() + ").");
            }

            if (record.getId() == null || record.getId().isBlank()) {
                record.setId(IdGenerator.nextId());
            }

            // Assign current doctor if not set
            if (record.getDoctorId() == null || record.getDoctorId().isBlank()) {
                User current = SessionContext.getCurrentUser().orElseThrow();
                record.setDoctorId(current.getId());
            }

            if (record.getVisitDate() == null) {
                record.setVisitDate(LocalDate.now());
            }

            // Save medical record
            medicalRecordDAO.save(record);

            // Transition appointment to COMPLETED if not already completed
            if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
                if (appointment.getStatus() == AppointmentStatus.SCHEDULED || appointment.getStatus() == AppointmentStatus.CONFIRMED) {
                    appointmentService.transitionStatus(appointment.getId(), AppointmentStatus.CHECKED_IN);
                }
                appointmentService.transitionStatus(appointment.getId(), AppointmentStatus.COMPLETED);
            }

            LOGGER.info("Medical record created successfully: " + record.getId() + " for patient " + patient.getPatientNumber());
            return record;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error saving medical record", e);
            throw new DatabaseException("Failed to save medical record: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the chronological longitudinal medical history for a patient.
     */
    public List<MedicalRecord> getPatientMedicalHistory(String patientId) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        try {
            return medicalRecordDAO.findByPatientId(patientId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch medical history for patient: " + patientId, e);
            throw new DatabaseException("Failed to load medical history: " + e.getMessage(), e);
        }
    }

    public MedicalRecord getRecordById(String id) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        try {
            Optional<MedicalRecord> opt = medicalRecordDAO.findById(id);
            if (opt.isEmpty()) {
                throw new IllegalArgumentException("Medical record not found with ID: " + id);
            }
            return opt.get();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load medical record: " + id, e);
            throw new DatabaseException("Database error loading record: " + e.getMessage(), e);
        }
    }

    public List<MedicalRecord> getAllRecentRecords(int limit) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        try {
            return medicalRecordDAO.findRecent(limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load recent medical records", e);
            throw new DatabaseException("Failed to load recent records: " + e.getMessage(), e);
        }
    }

    public void updateMedicalRecord(MedicalRecord record) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        validateRecordInput(record);
        try {
            medicalRecordDAO.update(record);
            LOGGER.info("Updated medical record: " + record.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update medical record: " + record.getId(), e);
            throw new DatabaseException("Failed to update medical record: " + e.getMessage(), e);
        }
    }

    private void validateRecordInput(MedicalRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Medical record cannot be null.");
        }
        if (record.getPatientId() == null || record.getPatientId().isBlank()) {
            throw new IllegalArgumentException("Patient is required.");
        }
        if (record.getAppointmentId() == null || record.getAppointmentId().isBlank()) {
            throw new IllegalArgumentException("Associated appointment is required.");
        }
        if (record.getChiefComplaint() == null || record.getChiefComplaint().trim().length() < 3) {
            throw new IllegalArgumentException("Chief complaint is required (minimum 3 characters).");
        }
        if (record.getDiagnosis() == null || record.getDiagnosis().trim().length() < 3) {
            throw new IllegalArgumentException("Diagnosis is required (minimum 3 characters).");
        }
        if (record.getVisitDate() != null && record.getVisitDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Visit date cannot be in the future.");
        }
        if (record.getFollowUpDate() != null && record.getVisitDate() != null &&
                record.getFollowUpDate().isBefore(record.getVisitDate())) {
            throw new IllegalArgumentException("Follow-up date cannot be earlier than the visit date.");
        }
    }
}
