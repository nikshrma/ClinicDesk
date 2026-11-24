package com.clinicdesk.dao;

import com.clinicdesk.model.MedicalRecord;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for longitudinal MedicalRecord entities.
 */
public interface MedicalRecordDAO extends BaseDAO<MedicalRecord, String> {

    Optional<MedicalRecord> findByAppointmentId(String appointmentId) throws SQLException;

    List<MedicalRecord> findByPatientId(String patientId) throws SQLException;

    List<MedicalRecord> findAll(int offset, int limit) throws SQLException;

    List<MedicalRecord> findRecent(int limit) throws SQLException;

    int countTotal() throws SQLException;
}
