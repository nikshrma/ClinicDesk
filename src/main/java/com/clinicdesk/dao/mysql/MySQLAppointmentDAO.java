package com.clinicdesk.dao.mysql;

import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.AppointmentStatus;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct JDBC implementation of AppointmentDAO with conflict detection and join queries.
 */
public class MySQLAppointmentDAO implements AppointmentDAO {

    private static final Logger LOGGER = AppLogger.getLogger(MySQLAppointmentDAO.class);

    private static final String SELECT_BASE =
            "SELECT a.id, a.patient_id, a.doctor_id, a.scheduled_at, a.duration_minutes, " +
            "       a.reason, a.status, a.created_at, a.updated_at, " +
            "       p.full_name AS patient_name, p.patient_number, " +
            "       u.full_name AS doctor_name " +
            "FROM appointments a " +
            "JOIN patients p ON a.patient_id = p.id " +
            "JOIN users u ON a.doctor_id = u.id ";

    @Override
    public void save(Appointment appointment) throws SQLException {
        String sql = "INSERT INTO appointments (" +
                "id, patient_id, doctor_id, scheduled_at, duration_minutes, reason, status, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, appointment.getId());
            ps.setString(2, appointment.getPatientId());
            ps.setString(3, appointment.getDoctorId());
            ps.setTimestamp(4, Timestamp.valueOf(appointment.getScheduledAt()));
            ps.setInt(5, appointment.getDurationMinutes());
            ps.setString(6, appointment.getReason());
            ps.setString(7, appointment.getStatus().name());
            ps.setTimestamp(8, Timestamp.from(appointment.getCreatedAt() != null ? appointment.getCreatedAt() : Instant.now()));
            ps.setTimestamp(9, Timestamp.from(appointment.getUpdatedAt() != null ? appointment.getUpdatedAt() : Instant.now()));

            ps.executeUpdate();
            LOGGER.info("Saved appointment: " + appointment.getId() + " for doctor " + appointment.getDoctorId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save appointment", e);
            throw e;
        }
    }

    @Override
    public Optional<Appointment> findById(String id) throws SQLException {
        String sql = SELECT_BASE + "WHERE a.id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find appointment by id: " + id, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public List<Appointment> findByPatientId(String patientId) throws SQLException {
        String sql = SELECT_BASE + "WHERE a.patient_id = ? ORDER BY a.scheduled_at DESC";
        List<Appointment> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find appointments for patient: " + patientId, e);
            throw e;
        }
        return list;
    }

    @Override
    public List<Appointment> findByDoctorAndDateRange(String doctorId, LocalDateTime start, LocalDateTime end) throws SQLException {
        String sql = SELECT_BASE +
                "WHERE a.doctor_id = ? AND a.scheduled_at >= ? AND a.scheduled_at <= ? " +
                "ORDER BY a.scheduled_at ASC";
        List<Appointment> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, doctorId);
            ps.setTimestamp(2, Timestamp.valueOf(start));
            ps.setTimestamp(3, Timestamp.valueOf(end));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find appointments for doctor range", e);
            throw e;
        }
        return list;
    }

    @Override
    public List<Appointment> findByDate(LocalDate date) throws SQLException {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);

        String sql = SELECT_BASE +
                "WHERE a.scheduled_at >= ? AND a.scheduled_at <= ? " +
                "ORDER BY a.scheduled_at ASC";
        List<Appointment> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(dayStart));
            ps.setTimestamp(2, Timestamp.valueOf(dayEnd));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find appointments for date: " + date, e);
            throw e;
        }
        return list;
    }

    @Override
    public List<Appointment> findUpcoming(int limit) throws SQLException {
        String sql = SELECT_BASE +
                "WHERE a.scheduled_at >= ? AND a.status IN ('SCHEDULED', 'CONFIRMED') " +
                "ORDER BY a.scheduled_at ASC LIMIT ?";
        List<Appointment> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find upcoming appointments", e);
            throw e;
        }
        return list;
    }

    @Override
    public boolean hasDoctorConflict(String doctorId, LocalDateTime start, LocalDateTime end, String excludeAppointmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM appointments " +
                "WHERE doctor_id = ? " +
                "  AND status NOT IN ('CANCELLED', 'NO_SHOW') " +
                "  AND (? IS NULL OR id != ?) " +
                "  AND scheduled_at < ? " +
                "  AND DATE_ADD(scheduled_at, INTERVAL duration_minutes MINUTE) > ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, doctorId);
            ps.setString(2, excludeAppointmentId);
            ps.setString(3, excludeAppointmentId);
            ps.setTimestamp(4, Timestamp.valueOf(end));
            ps.setTimestamp(5, Timestamp.valueOf(start));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to check doctor appointment conflict", e);
            throw e;
        }
        return false;
    }

    @Override
    public void update(Appointment appointment) throws SQLException {
        String sql = "UPDATE appointments SET " +
                "patient_id = ?, doctor_id = ?, scheduled_at = ?, duration_minutes = ?, " +
                "reason = ?, status = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, appointment.getPatientId());
            ps.setString(2, appointment.getDoctorId());
            ps.setTimestamp(3, Timestamp.valueOf(appointment.getScheduledAt()));
            ps.setInt(4, appointment.getDurationMinutes());
            ps.setString(5, appointment.getReason());
            ps.setString(6, appointment.getStatus().name());
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.setString(8, appointment.getId());

            ps.executeUpdate();
            LOGGER.info("Updated appointment: " + appointment.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update appointment: " + appointment.getId(), e);
            throw e;
        }
    }

    @Override
    public void updateStatus(String appointmentId, AppointmentStatus status) throws SQLException {
        String sql = "UPDATE appointments SET status = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, appointmentId);

            ps.executeUpdate();
            LOGGER.info("Updated status of appointment " + appointmentId + " to " + status);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update appointment status: " + appointmentId, e);
            throw e;
        }
    }

    @Override
    public void delete(String id) throws SQLException {
        updateStatus(id, AppointmentStatus.CANCELLED);
    }

    @Override
    public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) throws SQLException {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);

        String sql = "SELECT status, COUNT(*) FROM appointments " +
                "WHERE scheduled_at >= ? AND scheduled_at <= ? GROUP BY status";

        Map<AppointmentStatus, Integer> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus s : AppointmentStatus.values()) {
            counts.put(s, 0);
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AppointmentStatus status = AppointmentStatus.fromString(rs.getString(1));
                    counts.put(status, rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get status counts for date: " + date, e);
            throw e;
        }
        return counts;
    }

    private Appointment mapRow(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getString("id"));
        a.setPatientId(rs.getString("patient_id"));
        a.setDoctorId(rs.getString("doctor_id"));

        Timestamp ts = rs.getTimestamp("scheduled_at");
        if (ts != null) {
            a.setScheduledAt(ts.toLocalDateTime());
        }

        a.setDurationMinutes(rs.getInt("duration_minutes"));
        a.setReason(rs.getString("reason"));
        a.setStatus(AppointmentStatus.fromString(rs.getString("status")));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            a.setCreatedAt(created.toInstant());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            a.setUpdatedAt(updated.toInstant());
        }

        // Joined columns
        a.setPatientName(rs.getString("patient_name"));
        a.setPatientNumber(rs.getString("patient_number"));
        a.setDoctorName(rs.getString("doctor_name"));

        return a;
    }
}
