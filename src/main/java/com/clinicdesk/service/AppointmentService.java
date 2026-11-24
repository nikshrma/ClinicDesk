package com.clinicdesk.service;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.AppointmentConflictException;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.exception.InvalidAppointmentStateException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.util.IdGenerator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service encapsulating appointment scheduling, doctor conflict detection,
 * and lifecycle state transitions.
 */
public class AppointmentService {

    private static final Logger LOGGER = AppLogger.getLogger(AppointmentService.class);

    private final AppointmentDAO appointmentDAO;
    private final PatientService patientService;
    private final UserDAO userDAO;

    public AppointmentService(AppointmentDAO appointmentDAO, PatientService patientService, UserDAO userDAO) {
        this.appointmentDAO = appointmentDAO;
        this.patientService = patientService;
        this.userDAO = userDAO;
    }

    /**
     * Schedules a new appointment, ensuring non-overlapping doctor availability
     * and active patient status.
     */
    public Appointment scheduleAppointment(Appointment appointment) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);

        validateAppointment(appointment);

        // Verify Patient exists and is active (not archived)
        Patient patient = patientService.getPatientById(appointment.getPatientId());
        if (patient.isArchived()) {
            throw new IllegalArgumentException("Cannot schedule appointments for an archived patient.");
        }

        // Verify Doctor exists and is active
        try {
            Optional<User> doctorOpt = userDAO.findById(appointment.getDoctorId());
            if (doctorOpt.isEmpty() || !doctorOpt.get().isActive() || doctorOpt.get().getRole() != Role.DOCTOR) {
                throw new IllegalArgumentException("Invalid or inactive doctor selected.");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify doctor existence: " + e.getMessage(), e);
        }

        // Detect scheduling conflicts
        LocalDateTime start = appointment.getScheduledAt();
        LocalDateTime end = appointment.getEndDateTime();
        checkForConflicts(appointment.getDoctorId(), start, end, null);

        try {
            if (appointment.getId() == null || appointment.getId().isBlank()) {
                appointment.setId(IdGenerator.nextId());
            }
            if (appointment.getStatus() == null) {
                appointment.setStatus(AppointmentStatus.SCHEDULED);
            }

            appointmentDAO.save(appointment);
            LOGGER.info("Appointment scheduled: " + appointment.getId() + " at " + appointment.getScheduledAt());
            return appointment;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save scheduled appointment", e);
            throw new DatabaseException("Database error saving appointment: " + e.getMessage(), e);
        }
    }

    /**
     * Reschedules an existing appointment to a new date/time.
     */
    public void rescheduleAppointment(String appointmentId, LocalDateTime newScheduledAt, int newDurationMinutes) {
        SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);

        Appointment existing = getAppointmentById(appointmentId);
        if (existing.getStatus().isTerminal()) {
            throw new InvalidAppointmentStateException("Cannot reschedule an appointment in terminal state: " + existing.getStatus());
        }

        if (newScheduledAt == null || newScheduledAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Rescheduled time cannot be in the past.");
        }
        if (newDurationMinutes <= 0 || newDurationMinutes > 240) {
            throw new IllegalArgumentException("Duration must be between 1 and 240 minutes.");
        }

        LocalDateTime end = newScheduledAt.plusMinutes(newDurationMinutes);
        checkForConflicts(existing.getDoctorId(), newScheduledAt, end, appointmentId);

        try {
            existing.setScheduledAt(newScheduledAt);
            existing.setDurationMinutes(newDurationMinutes);
            // If it was cancelled or checked in, reset to SCHEDULED or preserve
            if (existing.getStatus() != AppointmentStatus.SCHEDULED) {
                existing.setStatus(AppointmentStatus.SCHEDULED);
            }
            appointmentDAO.update(existing);
            LOGGER.info("Appointment rescheduled: " + appointmentId + " to " + newScheduledAt);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to reschedule appointment: " + appointmentId, e);
            throw new DatabaseException("Database error rescheduling appointment: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a legal lifecycle transition for an appointment.
     */
    public void transitionStatus(String appointmentId, AppointmentStatus targetStatus) {
        Appointment existing = getAppointmentById(appointmentId);

        if (!existing.getStatus().canTransitionTo(targetStatus)) {
            throw new InvalidAppointmentStateException("Illegal state transition from " +
                    existing.getStatus() + " to " + targetStatus);
        }

        // Role check: Only Doctor or Admin can mark COMPLETED
        if (targetStatus == AppointmentStatus.COMPLETED) {
            SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
        } else {
            SessionContext.requireRole(Role.ADMIN, Role.DOCTOR, Role.RECEPTIONIST);
        }

        try {
            appointmentDAO.updateStatus(appointmentId, targetStatus);
            LOGGER.info("Appointment " + appointmentId + " transitioned from " + existing.getStatus() + " to " + targetStatus);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to transition appointment status: " + appointmentId, e);
            throw new DatabaseException("Failed to update appointment status: " + e.getMessage(), e);
        }
    }

    public Appointment getAppointmentById(String id) {
        try {
            Optional<Appointment> opt = appointmentDAO.findById(id);
            if (opt.isEmpty()) {
                throw new IllegalArgumentException("Appointment not found with ID: " + id);
            }
            return opt.get();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch appointment: " + id, e);
            throw new DatabaseException("Database error loading appointment: " + e.getMessage(), e);
        }
    }

    public List<Appointment> getAppointmentsForDate(LocalDate date) {
        try {
            return appointmentDAO.findByDate(date);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch appointments for date: " + date, e);
            throw new DatabaseException("Failed to load appointments: " + e.getMessage(), e);
        }
    }

    public List<Appointment> getAppointmentsForPatient(String patientId) {
        try {
            return appointmentDAO.findByPatientId(patientId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch appointments for patient: " + patientId, e);
            throw new DatabaseException("Failed to load patient appointments: " + e.getMessage(), e);
        }
    }

    public List<Appointment> getUpcomingAppointments(int limit) {
        try {
            return appointmentDAO.findUpcoming(limit);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch upcoming appointments", e);
            throw new DatabaseException("Failed to load upcoming appointments: " + e.getMessage(), e);
        }
    }

    public Map<AppointmentStatus, Integer> getDailyStatusSummary(LocalDate date) {
        try {
            return appointmentDAO.getStatusCountsForDate(date);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch status counts", e);
            throw new DatabaseException("Failed to load daily appointment summary: " + e.getMessage(), e);
        }
    }

    private void checkForConflicts(String doctorId, LocalDateTime start, LocalDateTime end, String excludeId) {
        try {
            boolean hasConflict = appointmentDAO.hasDoctorConflict(doctorId, start, end, excludeId);
            if (hasConflict) {
                LOGGER.warning("Doctor schedule conflict detected for doctor " + doctorId + " between " + start + " and " + end);
                throw new AppointmentConflictException("The selected doctor already has a scheduled appointment overlapping with this time window (" +
                        start.toLocalTime() + " - " + end.toLocalTime() + ").");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to execute conflict detection query", e);
            throw new DatabaseException("Failed to check appointment conflicts: " + e.getMessage(), e);
        }
    }

    private void validateAppointment(Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("Appointment cannot be null.");
        }
        if (appointment.getPatientId() == null || appointment.getPatientId().isBlank()) {
            throw new IllegalArgumentException("Patient is required.");
        }
        if (appointment.getDoctorId() == null || appointment.getDoctorId().isBlank()) {
            throw new IllegalArgumentException("Doctor is required.");
        }
        if (appointment.getScheduledAt() == null || appointment.getScheduledAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Appointment date and time must be in the future.");
        }
        if (appointment.getDurationMinutes() <= 0 || appointment.getDurationMinutes() > 240) {
            throw new IllegalArgumentException("Duration must be between 1 and 240 minutes.");
        }
        if (appointment.getReason() == null || appointment.getReason().trim().length() < 3) {
            throw new IllegalArgumentException("Reason for visit is required (minimum 3 characters).");
        }
    }
}
