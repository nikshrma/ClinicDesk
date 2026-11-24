package com.clinicdesk.dao;

import com.clinicdesk.model.Appointment;
import com.clinicdesk.model.AppointmentStatus;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object interface for Appointment entities.
 */
public interface AppointmentDAO extends BaseDAO<Appointment, String> {

    List<Appointment> findByPatientId(String patientId) throws SQLException;

    List<Appointment> findByDoctorAndDateRange(String doctorId, LocalDateTime start, LocalDateTime end) throws SQLException;

    List<Appointment> findByDate(LocalDate date) throws SQLException;

    List<Appointment> findUpcoming(int limit) throws SQLException;

    boolean hasDoctorConflict(String doctorId, LocalDateTime start, LocalDateTime end, String excludeAppointmentId) throws SQLException;

    void updateStatus(String appointmentId, AppointmentStatus status) throws SQLException;

    Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) throws SQLException;
}
