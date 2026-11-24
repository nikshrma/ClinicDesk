package com.clinicdesk.service;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.AppointmentConflictException;
import com.clinicdesk.exception.InvalidAppointmentStateException;
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

class AppointmentServiceTest {

    private InMemoryAppointmentDAO appointmentDAO;
    private InMemoryUserDAO userDAO;
    private PatientService patientService;
    private AppointmentService appointmentService;

    private Patient testPatient;
    private Patient archivedPatient;
    private User testDoctor;

    @BeforeEach
    void setup() {
        SessionContext.clear();
        appointmentDAO = new InMemoryAppointmentDAO();
        userDAO = new InMemoryUserDAO();
        InMemoryPatientDAO patientDAO = new InMemoryPatientDAO();
        patientService = new PatientService(patientDAO);
        appointmentService = new AppointmentService(appointmentDAO, patientService, userDAO);

        // Setup authenticated receptionist
        User receptionist = new User("r1", "Front Desk", "reception@clinic.local", "hash", Role.RECEPTIONIST, true);
        SessionContext.setCurrentUser(receptionist);

        // Setup Doctor
        testDoctor = new User("d1", "Dr. Gregory House", "house@clinic.local", "hash", Role.DOCTOR, true);
        userDAO.users.put(testDoctor.getId(), testDoctor);

        // Setup Active Patient
        testPatient = new Patient("p1", "CD-2026-0001", "Alice Smith", LocalDate.of(1990, 1, 1),
                Gender.FEMALE, "+15551234567", "alice@test.com", "Address", "Contact", "O+", "None");
        patientDAO.storage.put(testPatient.getId(), testPatient);

        // Setup Archived Patient
        archivedPatient = new Patient("p2", "CD-2026-0002", "Archived Bob", LocalDate.of(1980, 5, 5),
                Gender.MALE, "+15557654321", null, null, null, null, null);
        archivedPatient.setArchived(true);
        patientDAO.storage.put(archivedPatient.getId(), archivedPatient);
    }

    @Test
    void testScheduleAppointmentSuccess() {
        LocalDateTime tomorrowAtTen = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        Appointment app = new Appointment("a1", testPatient.getId(), testDoctor.getId(), tomorrowAtTen, 30, "Consultation", AppointmentStatus.SCHEDULED);

        Appointment created = appointmentService.scheduleAppointment(app);

        assertNotNull(created.getId());
        assertEquals(AppointmentStatus.SCHEDULED, created.getStatus());
        assertEquals(30, created.getDurationMinutes());
    }

    @Test
    void testScheduleRejectsPastDate() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        Appointment app = new Appointment("a1", testPatient.getId(), testDoctor.getId(), yesterday, 30, "Past Visit", AppointmentStatus.SCHEDULED);

        assertThrows(IllegalArgumentException.class, () -> appointmentService.scheduleAppointment(app));
    }

    @Test
    void testScheduleRejectsArchivedPatient() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0);
        Appointment app = new Appointment("a1", archivedPatient.getId(), testDoctor.getId(), tomorrow, 30, "Checkup", AppointmentStatus.SCHEDULED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> appointmentService.scheduleAppointment(app));
        assertTrue(ex.getMessage().contains("archived"));
    }

    @Test
    void testDoctorScheduleConflictDetected() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0); // 14:00 - 14:30
        Appointment app1 = new Appointment("app-1", testPatient.getId(), testDoctor.getId(), startTime, 30, "First Booking", AppointmentStatus.SCHEDULED);
        appointmentService.scheduleAppointment(app1);

        // Overlapping appointment: 14:15 - 14:45
        LocalDateTime overlapTime = startTime.plusMinutes(15);
        Appointment app2 = new Appointment("app-2", testPatient.getId(), testDoctor.getId(), overlapTime, 30, "Double Booking", AppointmentStatus.SCHEDULED);

        assertThrows(AppointmentConflictException.class, () -> appointmentService.scheduleAppointment(app2));

        // Non-overlapping appointment: 14:30 - 15:00 should succeed
        LocalDateTime nonOverlapTime = startTime.plusMinutes(30);
        Appointment app3 = new Appointment("app-3", testPatient.getId(), testDoctor.getId(), nonOverlapTime, 30, "Valid Next Slot", AppointmentStatus.SCHEDULED);
        assertDoesNotThrow(() -> appointmentService.scheduleAppointment(app3));
    }

    @Test
    void testAppointmentStateTransitions() {
        LocalDateTime slot = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        Appointment app = new Appointment("app-trans", testPatient.getId(), testDoctor.getId(), slot, 30, "Checkup", AppointmentStatus.SCHEDULED);
        appointmentService.scheduleAppointment(app);

        // SCHEDULED -> CONFIRMED
        appointmentService.transitionStatus(app.getId(), AppointmentStatus.CONFIRMED);
        assertEquals(AppointmentStatus.CONFIRMED, appointmentService.getAppointmentById(app.getId()).getStatus());

        // CONFIRMED -> CHECKED_IN
        appointmentService.transitionStatus(app.getId(), AppointmentStatus.CHECKED_IN);
        assertEquals(AppointmentStatus.CHECKED_IN, appointmentService.getAppointmentById(app.getId()).getStatus());

        // Receptionist cannot complete appointment (only doctor/admin)
        assertThrows(UnauthorizedAccessException.class, () ->
                appointmentService.transitionStatus(app.getId(), AppointmentStatus.COMPLETED)
        );

        // Switch to Doctor
        SessionContext.setCurrentUser(testDoctor);
        appointmentService.transitionStatus(app.getId(), AppointmentStatus.COMPLETED);
        assertEquals(AppointmentStatus.COMPLETED, appointmentService.getAppointmentById(app.getId()).getStatus());

        // COMPLETED is terminal; cannot transition to CANCELLED
        assertThrows(InvalidAppointmentStateException.class, () ->
                appointmentService.transitionStatus(app.getId(), AppointmentStatus.CANCELLED)
        );
    }

    // In-memory test stubs
    private static class InMemoryAppointmentDAO implements AppointmentDAO {
        private final Map<String, Appointment> storage = new LinkedHashMap<>();

        @Override
        public void save(Appointment entity) {
            storage.put(entity.getId(), entity);
        }

        @Override
        public Optional<Appointment> findById(String id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public List<Appointment> findByPatientId(String patientId) {
            return storage.values().stream().filter(a -> a.getPatientId().equals(patientId)).toList();
        }

        @Override
        public List<Appointment> findByDoctorAndDateRange(String doctorId, LocalDateTime start, LocalDateTime end) {
            return storage.values().stream()
                    .filter(a -> a.getDoctorId().equals(doctorId) && !a.getScheduledAt().isBefore(start) && !a.getScheduledAt().isAfter(end))
                    .toList();
        }

        @Override
        public List<Appointment> findByDate(LocalDate date) {
            return storage.values().stream()
                    .filter(a -> a.getScheduledAt().toLocalDate().equals(date))
                    .toList();
        }

        @Override
        public List<Appointment> findUpcoming(int limit) {
            return storage.values().stream()
                    .filter(a -> a.getScheduledAt().isAfter(LocalDateTime.now()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean hasDoctorConflict(String doctorId, LocalDateTime start, LocalDateTime end, String excludeId) {
            return storage.values().stream()
                    .filter(a -> a.getDoctorId().equals(doctorId))
                    .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.NO_SHOW)
                    .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                    .anyMatch(a -> a.overlapsWith(start, end));
        }

        @Override
        public void update(Appointment entity) {
            storage.put(entity.getId(), entity);
        }

        @Override
        public void updateStatus(String appointmentId, AppointmentStatus status) {
            Appointment a = storage.get(appointmentId);
            if (a != null) {
                a.setStatus(status);
            }
        }

        @Override
        public void delete(String id) {
            updateStatus(id, AppointmentStatus.CANCELLED);
        }

        @Override
        public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) {
            Map<AppointmentStatus, Integer> map = new EnumMap<>(AppointmentStatus.class);
            for (Appointment a : findByDate(date)) {
                map.put(a.getStatus(), map.getOrDefault(a.getStatus(), 0) + 1);
            }
            return map;
        }
    }

    private static class InMemoryUserDAO implements UserDAO {
        final Map<String, User> users = new HashMap<>();
        @Override public void save(User entity) { users.put(entity.getId(), entity); }
        @Override public Optional<User> findById(String id) { return Optional.ofNullable(users.get(id)); }
        @Override public Optional<User> findByEmail(String email) { return users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst(); }
        @Override public List<User> findAll(boolean inc) { return new ArrayList<>(users.values()); }
        @Override public List<User> findByRole(Role r) { return users.values().stream().filter(u -> u.getRole() == r).toList(); }
        @Override public void update(User entity) { users.put(entity.getId(), entity); }
        @Override public void updatePassword(String id, String h) { users.get(id).setPasswordHash(h); }
        @Override public void setActiveStatus(String id, boolean a) { users.get(id).setActive(a); }
        @Override public void delete(String id) { users.remove(id); }
    }

    private static class InMemoryPatientDAO implements com.clinicdesk.dao.PatientDAO {
        final Map<String, Patient> storage = new HashMap<>();
        @Override public void save(Patient entity) { storage.put(entity.getId(), entity); }
        @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(storage.get(id)); }
        @Override public Optional<Patient> findByPatientNumber(String n) { return storage.values().stream().filter(p -> p.getPatientNumber().equalsIgnoreCase(n)).findFirst(); }
        @Override public List<Patient> findAll(int o, int l, boolean i) { return new ArrayList<>(storage.values()); }
        @Override public List<Patient> search(String q, boolean i) { return new ArrayList<>(storage.values()); }
        @Override public void update(Patient entity) { storage.put(entity.getId(), entity); }
        @Override public void archive(String id) { storage.get(id).setArchived(true); }
        @Override public void unarchive(String id) { storage.get(id).setArchived(false); }
        @Override public void delete(String id) { archive(id); }
        @Override public int countActive() { return storage.size(); }
        @Override public int getNextSequenceNumber(int y) { return storage.size() + 1; }
    }
}
