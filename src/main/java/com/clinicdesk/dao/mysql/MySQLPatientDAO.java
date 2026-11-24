package com.clinicdesk.dao.mysql;

import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Gender;
import com.clinicdesk.model.Patient;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct JDBC implementation of PatientDAO.
 */
public class MySQLPatientDAO implements PatientDAO {

    private static final Logger LOGGER = AppLogger.getLogger(MySQLPatientDAO.class);

    private static final String SELECT_COLUMNS =
            "id, patient_number, full_name, date_of_birth, gender, phone, email, address, " +
            "emergency_contact, blood_group, allergies, archived, created_at, updated_at ";

    @Override
    public void save(Patient patient) throws SQLException {
        String sql = "INSERT INTO patients (" +
                "id, patient_number, full_name, date_of_birth, gender, phone, email, address, " +
                "emergency_contact, blood_group, allergies, archived, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindPatientParams(ps, patient);
            ps.executeUpdate();
            LOGGER.info("Saved patient: " + patient.getPatientNumber() + " (" + patient.getFullName() + ")");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save patient: " + patient.getPatientNumber(), e);
            throw e;
        }
    }

    @Override
    public Optional<Patient> findById(String id) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + "FROM patients WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find patient by id: " + id, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public Optional<Patient> findByPatientNumber(String patientNumber) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + "FROM patients WHERE patient_number = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, patientNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find patient by patientNumber: " + patientNumber, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public List<Patient> findAll(int offset, int limit, boolean includeArchived) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + "FROM patients " +
                (includeArchived ? "" : "WHERE archived = FALSE ") +
                "ORDER BY created_at DESC LIMIT ? OFFSET ?";

        List<Patient> list = new ArrayList<>();
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
            LOGGER.log(Level.SEVERE, "Failed to fetch all patients", e);
            throw e;
        }
        return list;
    }

    @Override
    public List<Patient> search(String query, boolean includeArchived) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + "FROM patients WHERE " +
                (includeArchived ? "" : "archived = FALSE AND ") +
                "(patient_number LIKE ? OR full_name LIKE ? OR phone LIKE ?) " +
                "ORDER BY full_name ASC LIMIT 50";

        List<Patient> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String pattern = "%" + query.trim() + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to search patients with query: " + query, e);
            throw e;
        }
        return list;
    }

    @Override
    public void update(Patient patient) throws SQLException {
        String sql = "UPDATE patients SET " +
                "full_name = ?, date_of_birth = ?, gender = ?, phone = ?, email = ?, " +
                "address = ?, emergency_contact = ?, blood_group = ?, allergies = ?, " +
                "archived = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, patient.getFullName());
            ps.setDate(2, Date.valueOf(patient.getDateOfBirth()));
            ps.setString(3, patient.getGender() != null ? patient.getGender().name() : null);
            ps.setString(4, patient.getPhone());
            ps.setString(5, patient.getEmail());
            ps.setString(6, patient.getAddress());
            ps.setString(7, patient.getEmergencyContact());
            ps.setString(8, patient.getBloodGroup());
            ps.setString(9, patient.getAllergies());
            ps.setBoolean(10, patient.isArchived());
            ps.setTimestamp(11, Timestamp.from(Instant.now()));
            ps.setString(12, patient.getId());

            ps.executeUpdate();
            LOGGER.info("Updated patient: " + patient.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update patient: " + patient.getId(), e);
            throw e;
        }
    }

    @Override
    public void archive(String id) throws SQLException {
        String sql = "UPDATE patients SET archived = TRUE, updated_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, id);
            ps.executeUpdate();
            LOGGER.info("Archived patient: " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to archive patient: " + id, e);
            throw e;
        }
    }

    @Override
    public void unarchive(String id) throws SQLException {
        String sql = "UPDATE patients SET archived = FALSE, updated_at = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, id);
            ps.executeUpdate();
            LOGGER.info("Unarchived patient: " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to unarchive patient: " + id, e);
            throw e;
        }
    }

    @Override
    public void delete(String id) throws SQLException {
        archive(id);
    }

    @Override
    public int countActive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients WHERE archived = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Override
    public int getNextSequenceNumber(int year) throws SQLException {
        String sql = "SELECT COUNT(*) FROM patients WHERE YEAR(created_at) = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
            }
        }
        return 1;
    }

    private void bindPatientParams(PreparedStatement ps, Patient patient) throws SQLException {
        ps.setString(1, patient.getId());
        ps.setString(2, patient.getPatientNumber());
        ps.setString(3, patient.getFullName());
        ps.setDate(4, Date.valueOf(patient.getDateOfBirth()));
        ps.setString(5, patient.getGender() != null ? patient.getGender().name() : null);
        ps.setString(6, patient.getPhone());
        ps.setString(7, patient.getEmail());
        ps.setString(8, patient.getAddress());
        ps.setString(9, patient.getEmergencyContact());
        ps.setString(10, patient.getBloodGroup());
        ps.setString(11, patient.getAllergies());
        ps.setBoolean(12, patient.isArchived());
        ps.setTimestamp(13, Timestamp.from(patient.getCreatedAt() != null ? patient.getCreatedAt() : Instant.now()));
        ps.setTimestamp(14, Timestamp.from(patient.getUpdatedAt() != null ? patient.getUpdatedAt() : Instant.now()));
    }

    private Patient mapRow(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getString("id"));
        p.setPatientNumber(rs.getString("patient_number"));
        p.setFullName(rs.getString("full_name"));

        Date dob = rs.getDate("date_of_birth");
        if (dob != null) {
            p.setDateOfBirth(dob.toLocalDate());
        }

        String genderStr = rs.getString("gender");
        if (genderStr != null) {
            p.setGender(Gender.fromString(genderStr));
        }

        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setAddress(rs.getString("address"));
        p.setEmergencyContact(rs.getString("emergency_contact"));
        p.setBloodGroup(rs.getString("blood_group"));
        p.setAllergies(rs.getString("allergies"));
        p.setArchived(rs.getBoolean("archived"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            p.setCreatedAt(created.toInstant());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            p.setUpdatedAt(updated.toInstant());
        }
        return p;
    }
}
