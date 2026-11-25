package com.clinicdesk.dao;

import com.clinicdesk.model.DocumentCategory;
import com.clinicdesk.model.DocumentMetadata;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for DocumentMetadata entities.
 */
public interface DocumentDAO extends BaseDAO<DocumentMetadata, String> {

    Optional<DocumentMetadata> findByObjectKey(String objectKey) throws SQLException;

    List<DocumentMetadata> findByPatientId(String patientId) throws SQLException;

    List<DocumentMetadata> findByMedicalRecordId(String medicalRecordId) throws SQLException;

    List<DocumentMetadata> findByCategory(DocumentCategory category) throws SQLException;

    List<DocumentMetadata> findAll(int offset, int limit) throws SQLException;

    List<DocumentMetadata> findRecent(int limit) throws SQLException;

    void archive(String id) throws SQLException;

    default void updateFilename(String id, String newFilename) throws SQLException {
        Optional<DocumentMetadata> doc = findById(id);
        if (doc.isPresent()) {
            DocumentMetadata d = doc.get();
            d.setOriginalFilename(newFilename);
            update(d);
        }
    }

    int countTotal() throws SQLException;
}
