package com.clinicdesk.dao.mysql;

import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.dao.MedicalRecordDAO;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.MedicalRecord;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct JDBC implementation of MedicalRecordDAO.
 */
public class MySQLMedicalRecordDAO implements MedicalRecordDAO {

    private static final Logger LOGGER = AppLogger.getLogger(MySQLMedicalRecordDAO.class);

    private static final String SELECT_BASE =
            "SELECT m.id, m.patient_id, m.appointment_id, m.doctor_id, m.visit_date, " +
            "       m.chief_complaint, m.diagnosis, m.follow_up_date, m.created_at, m.updated_at, " +
            "       p.full_name AS patient_name, p.patient_number, " +
            "       u.full_name AS doctor_name " +
            "FROM medical_records m " +
            "JOIN patients p ON m.patient_id = p.id " +
            "JOIN users u ON m.doctor_id = u.id ";

    @Override
    public void save(MedicalRecord record) throws SQLException {
        String sql = "INSERT INTO medical_records (" +
                "id, patient_id, appointment_id, doctor_id, visit_date, chief_complaint, " +
                "diagnosis, follow_up_date, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindRecordParams(ps, record);
            ps.executeUpdate();
            LOGGER.info("Medical record created: " + record.getId() + " for patient " + record.getPatientId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to insert medical record", e);
            throw e;
        }
    }

    @Override
    public Optional<MedicalRecord> findById(String id) throws SQLException {
        String sql = SELECT_BASE + "WHERE m.id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find medical record by id: " + id, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public Optional<MedicalRecord> findByAppointmentId(String appointmentId) throws SQLException {
        String sql = SELECT_BASE + "WHERE m.appointment_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find medical record by appointment: " + appointmentId, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public List<MedicalRecord> findByPatientId(String patientId) throws SQLException {
        String sql = SELECT_BASE + "WHERE m.patient_id = ? ORDER BY m.visit_date DESC, m.created_at DESC";
        List<MedicalRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch medical records for patient: " + patientId, e);
            throw e;
        }
        return list;
    }

    @Override
    public List<MedicalRecord> findAll(int offset, int limit) throws SQLException {
        String sql = SELECT_BASE + "ORDER BY m.visit_date DESC, m.created_at DESC LIMIT ? OFFSET ?";
        List<MedicalRecord> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch all medical records", e);
            throw e;
        }
        return list;
    }

    @Override
    public List<MedicalRecord> findRecent(int limit) throws SQLException {
        return findAll(0, limit);
    }

    @Override
    public void update(MedicalRecord record) throws SQLException {
        String sql = "UPDATE medical_records SET " +
                "visit_date = ?, chief_complaint = ?, diagnosis = ?, follow_up_date = ?, updated_at = ? " +
                "WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(record.getVisitDate()));
            ps.setString(2, record.getChiefComplaint());
            ps.setString(3, record.getDiagnosis());
            if (record.getFollowUpDate() != null) {
                ps.setDate(4, Date.valueOf(record.getFollowUpDate()));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.setString(6, record.getId());

            ps.executeUpdate();
            LOGGER.info("Updated medical record: " + record.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update medical record: " + record.getId(), e);
            throw e;
        }
    }

    @Override
    public void delete(String id) throws SQLException {
        // Historical medical records should not be deleted destructively
        LOGGER.warning("Deletion of medical record requested: " + id + ". Rejected to preserve audit integrity.");
        throw new UnsupportedOperationException("Medical records cannot be deleted.");
    }

    @Override
    public int countTotal() throws SQLException {
        String sql = "SELECT COUNT(*) FROM medical_records";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void bindRecordParams(PreparedStatement ps, MedicalRecord record) throws SQLException {
        ps.setString(1, record.getId());
        ps.setString(2, record.getPatientId());
        ps.setString(3, record.getAppointmentId());
        ps.setString(4, record.getDoctorId());
        ps.setDate(5, Date.valueOf(record.getVisitDate()));
        ps.setString(6, record.getChiefComplaint());
        ps.setString(7, record.getDiagnosis());
        if (record.getFollowUpDate() != null) {
            ps.setDate(8, Date.valueOf(record.getFollowUpDate()));
        } else {
            ps.setNull(8, Types.DATE);
        }
        ps.setTimestamp(9, Timestamp.from(record.getCreatedAt() != null ? record.getCreatedAt() : Instant.now()));
        ps.setTimestamp(10, Timestamp.from(record.getUpdatedAt() != null ? record.getUpdatedAt() : Instant.now()));
    }

    private MedicalRecord mapRow(ResultSet rs) throws SQLException {
        MedicalRecord mr = new MedicalRecord();
        mr.setId(rs.getString("id"));
        mr.setPatientId(rs.getString("patient_id"));
        mr.setAppointmentId(rs.getString("appointment_id"));
        mr.setDoctorId(rs.getString("doctor_id"));

        Date vd = rs.getDate("visit_date");
        if (vd != null) {
            mr.setVisitDate(vd.toLocalDate());
        }

        mr.setChiefComplaint(rs.getString("chief_complaint"));
        mr.setDiagnosis(rs.getString("diagnosis"));

        Date fud = rs.getDate("follow_up_date");
        if (fud != null) {
            mr.setFollowUpDate(fud.toLocalDate());
        }

        Timestamp cr = rs.getTimestamp("created_at");
        if (cr != null) {
            mr.setCreatedAt(cr.toInstant());
        }
        Timestamp up = rs.getTimestamp("updated_at");
        if (up != null) {
            mr.setUpdatedAt(up.toInstant());
        }

        mr.setPatientName(rs.getString("patient_name"));
        mr.setPatientNumber(rs.getString("patient_number"));
        mr.setDoctorName(rs.getString("doctor_name"));

        return mr;
    }
}
