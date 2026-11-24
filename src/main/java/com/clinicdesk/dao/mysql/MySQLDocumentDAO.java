package com.clinicdesk.dao.mysql;

import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.DocumentCategory;
import com.clinicdesk.model.DocumentMetadata;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct JDBC implementation of DocumentDAO.
 */
public class MySQLDocumentDAO implements DocumentDAO {

    private static final Logger LOGGER = AppLogger.getLogger(MySQLDocumentDAO.class);

    private static final String SELECT_BASE =
            "SELECT d.id, d.patient_id, d.medical_record_id, d.uploaded_by, d.original_filename, " +
            "       d.object_key, d.mime_type, d.file_size, d.category, d.capture_timestamp, " +
            "       d.archived, d.created_at, " +
            "       p.full_name AS patient_name, p.patient_number, " +
            "       u.full_name AS uploader_name " +
            "FROM documents d " +
            "JOIN patients p ON d.patient_id = p.id " +
            "JOIN users u ON d.uploaded_by = u.id ";

    @Override
    public void save(DocumentMetadata doc) throws SQLException {
        String sql = "INSERT INTO documents (" +
                "id, patient_id, medical_record_id, uploaded_by, original_filename, " +
                "object_key, mime_type, file_size, category, capture_timestamp, archived, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindDocParams(ps, doc);
            ps.executeUpdate();
            LOGGER.info("Saved document metadata: " + doc.getId() + " (" + doc.getOriginalFilename() + ")");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to insert document metadata: " + doc.getObjectKey(), e);
            throw e;
        }
    }

    @Override
    public Optional<DocumentMetadata> findById(String id) throws SQLException {
        String sql = SELECT_BASE + "WHERE d.id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find document by id: " + id, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public Optional<DocumentMetadata> findByObjectKey(String objectKey) throws SQLException {
        String sql = SELECT_BASE + "WHERE d.object_key = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, objectKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find document by objectKey: " + objectKey, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public List<DocumentMetadata> findByPatientId(String patientId) throws SQLException {
        String sql = SELECT_BASE + "WHERE d.patient_id = ? AND d.archived = FALSE ORDER BY d.created_at DESC";
        List<DocumentMetadata> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch documents for patient: " + patientId, e);
            throw e;
        }
        return list;
    }

    @Override
    public List<DocumentMetadata> findByMedicalRecordId(String medicalRecordId) throws SQLException {
        String sql = SELECT_BASE + "WHERE d.medical_record_id = ? AND d.archived = FALSE ORDER BY d.created_at DESC";
        List<DocumentMetadata> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, medicalRecordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch documents for record: " + medicalRecordId, e);
            throw e;
        }
        return list;
    }

    @Override
    public List<DocumentMetadata> findByCategory(DocumentCategory category) throws SQLException {
        String sql = SELECT_BASE + "WHERE d.category = ? AND d.archived = FALSE ORDER BY d.created_at DESC";
        List<DocumentMetadata> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch documents by category: " + category, e);
            throw e;
        }
        return list;
    }

    @Override
    public List<DocumentMetadata> findAll(int offset, int limit) throws SQLException {
        String sql = SELECT_BASE + "WHERE d.archived = FALSE ORDER BY d.created_at DESC LIMIT ? OFFSET ?";
        List<DocumentMetadata> list = new ArrayList<>();

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
            LOGGER.log(Level.SEVERE, "Failed to fetch all documents", e);
            throw e;
        }
        return list;
    }

    @Override
    public List<DocumentMetadata> findRecent(int limit) throws SQLException {
        return findAll(0, limit);
    }

    @Override
    public void archive(String id) throws SQLException {
        String sql = "UPDATE documents SET archived = TRUE WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            ps.executeUpdate();
            LOGGER.info("Archived document: " + id);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to archive document: " + id, e);
            throw e;
        }
    }

    @Override
    public void update(DocumentMetadata doc) throws SQLException {
        String sql = "UPDATE documents SET category = ?, archived = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, doc.getCategory().name());
            ps.setBoolean(2, doc.isArchived());
            ps.setString(3, doc.getId());
            ps.executeUpdate();
            LOGGER.info("Updated document: " + doc.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update document: " + doc.getId(), e);
            throw e;
        }
    }

    @Override
    public void delete(String id) throws SQLException {
        archive(id);
    }

    @Override
    public int countTotal() throws SQLException {
        String sql = "SELECT COUNT(*) FROM documents WHERE archived = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void bindDocParams(PreparedStatement ps, DocumentMetadata doc) throws SQLException {
        ps.setString(1, doc.getId());
        ps.setString(2, doc.getPatientId());
        ps.setString(3, doc.getMedicalRecordId());
        ps.setString(4, doc.getUploadedBy());
        ps.setString(5, doc.getOriginalFilename());
        ps.setString(6, doc.getObjectKey());
        ps.setString(7, doc.getMimeType());
        ps.setLong(8, doc.getFileSize());
        ps.setString(9, doc.getCategory().name());

        if (doc.getCaptureTimestamp() != null) {
            ps.setTimestamp(10, Timestamp.from(doc.getCaptureTimestamp()));
        } else {
            ps.setNull(10, Types.TIMESTAMP);
        }

        ps.setBoolean(11, doc.isArchived());
        ps.setTimestamp(12, Timestamp.from(doc.getCreatedAt() != null ? doc.getCreatedAt() : Instant.now()));
    }

    private DocumentMetadata mapRow(ResultSet rs) throws SQLException {
        DocumentMetadata d = new DocumentMetadata();
        d.setId(rs.getString("id"));
        d.setPatientId(rs.getString("patient_id"));
        d.setMedicalRecordId(rs.getString("medical_record_id"));
        d.setUploadedBy(rs.getString("uploaded_by"));
        d.setOriginalFilename(rs.getString("original_filename"));
        d.setObjectKey(rs.getString("object_key"));
        d.setMimeType(rs.getString("mime_type"));
        d.setFileSize(rs.getLong("file_size"));
        d.setCategory(DocumentCategory.fromString(rs.getString("category")));

        Timestamp cap = rs.getTimestamp("capture_timestamp");
        if (cap != null) {
            d.setCaptureTimestamp(cap.toInstant());
        }
        d.setArchived(rs.getBoolean("archived"));

        Timestamp cr = rs.getTimestamp("created_at");
        if (cr != null) {
            d.setCreatedAt(cr.toInstant());
        }

        d.setPatientName(rs.getString("patient_name"));
        d.setPatientNumber(rs.getString("patient_number"));
        d.setUploaderName(rs.getString("uploader_name"));

        return d;
    }
}
