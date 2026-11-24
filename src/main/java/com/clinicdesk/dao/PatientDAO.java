package com.clinicdesk.dao;

import com.clinicdesk.model.Patient;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Patient entities.
 */
public interface PatientDAO extends BaseDAO<Patient, String> {

    Optional<Patient> findByPatientNumber(String patientNumber) throws SQLException;

    List<Patient> findAll(int offset, int limit, boolean includeArchived) throws SQLException;

    List<Patient> search(String query, boolean includeArchived) throws SQLException;

    void archive(String id) throws SQLException;

    void unarchive(String id) throws SQLException;

    int countActive() throws SQLException;

    int getNextSequenceNumber(int year) throws SQLException;
}
