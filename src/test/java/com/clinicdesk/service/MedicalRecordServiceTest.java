package com.clinicdesk.service;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.MedicalRecordDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MedicalRecordServiceTest {

    private InMemoryMedicalRecordDAO medicalRecordDAO;
    private MedicalRecordService medicalRecordService;
    private AppointmentService appointmentService;
    private PatientService patientService;

    private User doctor;
    private User receptionist;
    private Patient patient;
    private Appointment appointment;

    @BeforeEach
    void setup() {
        SessionContext.clear();
        medicalRecordDAO = new InMemoryMedicalRecordDAO();
        InMemoryPatientDAO patientDAO = new InMemoryPatientDAO();
        InMemoryAppointmentDAO appointmentDAO = new InMemoryAppointmentDAO();
        InMemoryUserDAO userDAO = new InMemoryUserDAO();

        patientService = new PatientService(patientDAO);
        appointmentService = new AppointmentService(appointmentDAO, patientService, userDAO);
        medicalRecordService = new MedicalRecordService(medicalRecordDAO, patientService, appointmentService);

        doctor = new User("doc-1", "Dr. Sarah", "sarah@clinic.local", "hash", Role.DOCTOR, true);
        receptionist = new User("rec-1", "Reception Desk", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);
        userDAO.users.put(doctor.getId(), doctor);
        userDAO.users.put(receptionist.getId(), receptionist);

        patient = new Patient("pat-1", "CD-2026-0001", "John Patient", LocalDate.of(1988, 3, 10),
                Gender.MALE, "+15551234567", "john@test.com", null, null, "A+", "None");
        patientDAO.storage.put(patient.getId(), patient);

        appointment = new Appointment("app-1", patient.getId(), doctor.getId(),
                LocalDateTime.now().plusDays(1).withHour(10).withMinute(0), 30, "Persistent fever", AppointmentStatus.CHECKED_IN);
        appointmentDAO.storage.put(appointment.getId(), appointment);

        SessionContext.setCurrentUser(doctor);
    }

    @Test
    void testCreateMedicalRecordSuccess() {
        MedicalRecord record = new MedicalRecord("mr-1", patient.getId(), appointment.getId(), doctor.getId(),
                LocalDate.now(), "Fever and sore throat for 3 days", "Acute viral pharyngitis", LocalDate.now().plusDays(7));

        MedicalRecord created = medicalRecordService.createMedicalRecord(record);

        assertNotNull(created.getId());
        assertEquals("Acute viral pharyngitis", created.getDiagnosis());

        // Verify appointment transitioned to COMPLETED
        Appointment updatedApp = appointmentService.getAppointmentById(appointment.getId());
        assertEquals(AppointmentStatus.COMPLETED, updatedApp.getStatus());
    }

    @Test
    void testPreventDuplicateRecordForSameAppointment() {
        MedicalRecord record1 = new MedicalRecord("mr-1", patient.getId(), appointment.getId(), doctor.getId(),
                LocalDate.now(), "Fever", "Viral infection", null);
        medicalRecordService.createMedicalRecord(record1);

        // Attempting to create a second medical record for the same appointment must fail
        MedicalRecord record2 = new MedicalRecord("mr-2", patient.getId(), appointment.getId(), doctor.getId(),
                LocalDate.now(), "Second assessment", "Bacterial infection", null);

        assertThrows(IllegalArgumentException.class, () -> medicalRecordService.createMedicalRecord(record2));
    }

    @Test
    void testReceptionistCannotCreateOrViewMedicalRecords() {
        SessionContext.setCurrentUser(receptionist);

        MedicalRecord record = new MedicalRecord("mr-rec", patient.getId(), appointment.getId(), doctor.getId(),
                LocalDate.now(), "Complaint", "Diagnosis", null);

        // Receptionist cannot create medical record
        assertThrows(UnauthorizedAccessException.class, () -> medicalRecordService.createMedicalRecord(record));

        // Receptionist cannot view medical history
        assertThrows(UnauthorizedAccessException.class, () -> medicalRecordService.getPatientMedicalHistory(patient.getId()));
    }

    @Test
    void testChronologicalMedicalHistory() {
        MedicalRecord visit1 = new MedicalRecord("mr-v1", patient.getId(), appointment.getId(), doctor.getId(),
                LocalDate.of(2026, 1, 10), "Headache", "Tension headache", null);
        medicalRecordDAO.save(visit1);

        MedicalRecord visit2 = new MedicalRecord("mr-v2", patient.getId(), "app-2", doctor.getId(),
                LocalDate.of(2026, 3, 15), "Follow-up", "Resolved", null);
        medicalRecordDAO.save(visit2);

        List<MedicalRecord> history = medicalRecordService.getPatientMedicalHistory(patient.getId());
        assertEquals(2, history.size());
        assertEquals(LocalDate.of(2026, 3, 15), history.get(0).getVisitDate(), "Most recent visit should be first");
    }

    // In-memory test stubs
    private static class InMemoryMedicalRecordDAO implements MedicalRecordDAO {
        private final Map<String, MedicalRecord> storage = new LinkedHashMap<>();

        @Override public void save(MedicalRecord entity) { storage.put(entity.getId(), entity); }
        @Override public Optional<MedicalRecord> findById(String id) { return Optional.ofNullable(storage.get(id)); }
        @Override public Optional<MedicalRecord> findByAppointmentId(String appointmentId) {
            return storage.values().stream().filter(m -> m.getAppointmentId().equals(appointmentId)).findFirst();
        }
        @Override public List<MedicalRecord> findByPatientId(String patientId) {
            return storage.values().stream()
                    .filter(m -> m.getPatientId().equals(patientId))
                    .sorted(Comparator.comparing(MedicalRecord::getVisitDate).reversed())
                    .toList();
        }
        @Override public List<MedicalRecord> findAll(int offset, int limit) { return new ArrayList<>(storage.values()); }
        @Override public List<MedicalRecord> findRecent(int limit) { return new ArrayList<>(storage.values()); }
        @Override public void update(MedicalRecord entity) { storage.put(entity.getId(), entity); }
        @Override public void delete(String id) { storage.remove(id); }
        @Override public int countTotal() { return storage.size(); }
    }

    private static class InMemoryAppointmentDAO implements AppointmentDAO {
        final Map<String, Appointment> storage = new HashMap<>();
        @Override public void save(Appointment entity) { storage.put(entity.getId(), entity); }
        @Override public Optional<Appointment> findById(String id) { return Optional.ofNullable(storage.get(id)); }
        @Override public List<Appointment> findByPatientId(String p) { return storage.values().stream().filter(a -> a.getPatientId().equals(p)).toList(); }
        @Override public List<Appointment> findByDoctorAndDateRange(String d, LocalDateTime s, LocalDateTime e) { return new ArrayList<>(storage.values()); }
        @Override public List<Appointment> findByDate(LocalDate d) { return new ArrayList<>(storage.values()); }
        @Override public List<Appointment> findUpcoming(int l) { return new ArrayList<>(storage.values()); }
        @Override public boolean hasDoctorConflict(String d, LocalDateTime s, LocalDateTime e, String x) { return false; }
        @Override public void update(Appointment entity) { storage.put(entity.getId(), entity); }
        @Override public void updateStatus(String id, AppointmentStatus status) {
            Appointment a = storage.get(id);
            if (a != null) a.setStatus(status);
        }
        @Override public void delete(String id) { }
        @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) { return Map.of(); }
    }

    private static class InMemoryPatientDAO implements PatientDAO {
        final Map<String, Patient> storage = new HashMap<>();
        @Override public void save(Patient entity) { storage.put(entity.getId(), entity); }
        @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(storage.get(id)); }
        @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
        @Override public List<Patient> findAll(int o, int l, boolean i) { return new ArrayList<>(storage.values()); }
        @Override public List<Patient> search(String q, boolean i) { return new ArrayList<>(storage.values()); }
        @Override public void update(Patient entity) { storage.put(entity.getId(), entity); }
        @Override public void archive(String id) { }
        @Override public void unarchive(String id) { }
        @Override public void delete(String id) { }
        @Override public int countActive() { return storage.size(); }
        @Override public int getNextSequenceNumber(int y) { return storage.size() + 1; }
    }

    private static class InMemoryUserDAO implements UserDAO {
        final Map<String, User> users = new HashMap<>();
        @Override public void save(User entity) { users.put(entity.getId(), entity); }
        @Override public Optional<User> findById(String id) { return Optional.ofNullable(users.get(id)); }
        @Override public Optional<User> findByEmail(String email) { return Optional.empty(); }
        @Override public List<User> findAll(boolean inc) { return new ArrayList<>(users.values()); }
        @Override public List<User> findByRole(Role role) { return new ArrayList<>(users.values()); }
        @Override public void update(User entity) { users.put(entity.getId(), entity); }
        @Override public void updatePassword(String id, String p) { }
        @Override public void setActiveStatus(String id, boolean a) { }
        @Override public void delete(String id) { }
    }
}
