package com.clinicdesk.service;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.AppointmentStatus;
import com.clinicdesk.model.DashboardStats;
import com.clinicdesk.model.DocumentMetadata;
import com.clinicdesk.model.Patient;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service aggregating clinic-wide statistics and recent activity for the overview dashboard.
 */
public class DashboardService {

    private static final Logger LOGGER = AppLogger.getLogger(DashboardService.class);

    private final PatientDAO patientDAO;
    private final AppointmentDAO appointmentDAO;
    private final DocumentDAO documentDAO;

    public DashboardService(PatientDAO patientDAO, AppointmentDAO appointmentDAO, DocumentDAO documentDAO) {
        this.patientDAO = patientDAO;
        this.appointmentDAO = appointmentDAO;
        this.documentDAO = documentDAO;
    }

    /**
     * Gathers aggregated dashboard statistics using database aggregate functions.
     */
    public DashboardStats getDashboardStatistics() {
        DashboardStats stats = new DashboardStats();
        LocalDate today = LocalDate.now();

        try {
            // Aggregate counts
            stats.setActivePatientsCount(patientDAO.countActive());
            stats.setTotalDocumentsCount(documentDAO.countTotal());

            // Today's appointment counts
            Map<AppointmentStatus, Integer> statusCounts = appointmentDAO.getStatusCountsForDate(today);
            int totalToday = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
            stats.setTodayTotalAppointments(totalToday);
            stats.setTodayCheckedIn(statusCounts.getOrDefault(AppointmentStatus.CHECKED_IN, 0));
            stats.setTodayCompleted(statusCounts.getOrDefault(AppointmentStatus.COMPLETED, 0));
            stats.setTodayCancelled(statusCounts.getOrDefault(AppointmentStatus.CANCELLED, 0));

            // Today's appointments list
            stats.setTodayAppointments(appointmentDAO.findByDate(today));

            // Recent activity lists
            stats.setRecentPatients(patientDAO.findAll(0, 5, false));
            stats.setRecentDocuments(documentDAO.findRecent(5));

            return stats;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load dashboard statistics", e);
            throw new DatabaseException("Failed to aggregate dashboard metrics: " + e.getMessage(), e);
        }
    }
}
