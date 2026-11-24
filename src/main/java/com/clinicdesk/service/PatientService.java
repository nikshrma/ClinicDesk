package com.clinicdesk.service;

import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.exception.PatientNotFoundException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.util.IdGenerator;
import com.clinicdesk.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service encapsulating business rules, validation, and permissions for Patient management.
 */
public class PatientService {

    private static final Logger LOGGER = AppLogger.getLogger(PatientService.class);
    private final PatientDAO patientDAO;

    public PatientService(PatientDAO patientDAO) {
        this.patientDAO = patientDAO;
    }

    /**
     * Registers a new patient with validation and sequential patient number generation.
     */
    public Patient registerPatient(Patient patient) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);

        validatePatientInput(patient);

        try {
            if (patient.getId() == null || patient.getId().isBlank()) {
                patient.setId(IdGenerator.nextId());
            }

            int currentYear = LocalDate.now().getYear();
            int seq = patientDAO.getNextSequenceNumber(currentYear);
            String patientNumber = String.format("CD-%04d-%04d", currentYear, seq);
            patient.setPatientNumber(patientNumber);

            patientDAO.save(patient);
            LOGGER.info("Patient registered successfully: " + patient.getPatientNumber() + " (" + patient.getFullName() + ")");
            return patient;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to register patient: " + patient.getFullName(), e);
            throw new DatabaseException("Failed to register patient: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a patient by unique ID.
     */
    public Patient getPatientById(String id) {
        try {
            Optional<Patient> opt = patientDAO.findById(id);
            if (opt.isEmpty()) {
                throw new PatientNotFoundException("Patient not found with ID: " + id);
            }
            return opt.get();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve patient: " + id, e);
            throw new DatabaseException("Database error retrieving patient: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a patient by formatted patient number (e.g. CD-2026-0001).
     */
    public Patient getPatientByNumber(String patientNumber) {
        try {
            Optional<Patient> opt = patientDAO.findByPatientNumber(patientNumber);
            if (opt.isEmpty()) {
                throw new PatientNotFoundException("Patient not found with number: " + patientNumber);
            }
            return opt.get();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve patient by number: " + patientNumber, e);
            throw new DatabaseException("Database error retrieving patient: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing patient record.
     */
    public void updatePatient(Patient patient) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);

        if (patient.getId() == null || patient.getId().isBlank()) {
            throw new IllegalArgumentException("Patient ID is required for update.");
        }

        validatePatientInput(patient);

        try {
            // Check existence
            Patient existing = getPatientById(patient.getId());

            // If user is receptionist, ensure they cannot modify clinical fields (allergies) or archived status
            if (SessionContext.hasRole(Role.RECEPTIONIST) && !SessionContext.hasRole(Role.ADMIN, Role.DOCTOR)) {
                patient.setAllergies(existing.getAllergies());
                patient.setArchived(existing.isArchived());
            }

            patientDAO.update(patient);
            LOGGER.info("Patient record updated: " + patient.getPatientNumber());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update patient: " + patient.getId(), e);
            throw new DatabaseException("Failed to update patient: " + e.getMessage(), e);
        }
    }

    /**
     * Archives a patient (soft delete). Historical medical records remain intact.
     */
    public void archivePatient(String id) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        try {
            getPatientById(id); // Ensure exists
            patientDAO.archive(id);
            LOGGER.info("Patient archived: " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to archive patient: " + id, e);
            throw new DatabaseException("Failed to archive patient: " + e.getMessage(), e);
        }
    }

    /**
     * Restores an archived patient.
     */
    public void unarchivePatient(String id) {
        SessionContext.requireRole(Role.ADMIN);
        try {
            getPatientById(id);
            patientDAO.unarchive(id);
            LOGGER.info("Patient unarchived: " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to unarchive patient: " + id, e);
            throw new DatabaseException("Failed to unarchive patient: " + e.getMessage(), e);
        }
    }

    /**
     * Searches patients by number, name, or phone.
     */
    public List<Patient> searchPatients(String query, boolean includeArchived) {
        try {
            if (query == null || query.isBlank()) {
                return patientDAO.findAll(0, 50, includeArchived);
            }
            return patientDAO.search(query.trim(), includeArchived);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to search patients with query: " + query, e);
            throw new DatabaseException("Failed to search patients: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves paginated list of patients.
     */
    public List<Patient> getPatients(int page, int pageSize, boolean includeArchived) {
        try {
            int offset = Math.max(0, page) * pageSize;
            return patientDAO.findAll(offset, pageSize, includeArchived);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch paginated patients", e);
            throw new DatabaseException("Failed to fetch patients: " + e.getMessage(), e);
        }
    }

    public int getActivePatientCount() {
        try {
            return patientDAO.countActive();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count active patients", e);
            throw new DatabaseException("Failed to count active patients: " + e.getMessage(), e);
        }
    }

    private void validatePatientInput(Patient patient) {
        if (patient.getFullName() == null || patient.getFullName().trim().length() < 2) {
            throw new IllegalArgumentException("Patient full name is required and must be at least 2 characters.");
        }
        if (!ValidationUtil.isValidDateOfBirth(patient.getDateOfBirth())) {
            throw new IllegalArgumentException("Invalid date of birth. Must be a past date after 1900.");
        }
        if (!ValidationUtil.isValidPhone(patient.getPhone())) {
            throw new IllegalArgumentException("Invalid phone number format.");
        }
        if (patient.getEmail() != null && !patient.getEmail().isBlank() && !ValidationUtil.isValidEmail(patient.getEmail())) {
            throw new IllegalArgumentException("Invalid email address format.");
        }
    }
}
