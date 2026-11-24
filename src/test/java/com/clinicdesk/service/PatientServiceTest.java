package com.clinicdesk.service;

import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.PatientNotFoundException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.Gender;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PatientServiceTest {

    private InMemoryPatientDAO patientDAO;
    private PatientService patientService;

    @BeforeEach
    void setup() {
        SessionContext.clear();
        patientDAO = new InMemoryPatientDAO();
        patientService = new PatientService(patientDAO);

        // Set default authenticated user as Receptionist
        User receptionist = new User("rec-1", "Front Desk Staff", "reception@clinic.local", "hash", Role.RECEPTIONIST, true);
        SessionContext.setCurrentUser(receptionist);
    }

    @Test
    void testRegisterPatientSuccess() {
        Patient patient = new Patient();
        patient.setFullName("Alice Wonderland");
        patient.setDateOfBirth(LocalDate.of(1995, 4, 15));
        patient.setGender(Gender.FEMALE);
        patient.setPhone("+1 555 987 6543");
        patient.setEmail("alice@example.com");
        patient.setBloodGroup("A+");

        Patient registered = patientService.registerPatient(patient);

        assertNotNull(registered.getId());
        assertNotNull(registered.getPatientNumber());
        assertTrue(registered.getPatientNumber().startsWith("CD-" + LocalDate.now().getYear()));
        assertEquals(31, registered.getAge()); // As of 2026
        assertFalse(registered.isArchived());
    }

    @Test
    void testRegisterPatientValidationFailures() {
        // Name too short
        Patient p1 = new Patient();
        p1.setFullName("A");
        p1.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p1.setPhone("+12345678");
        assertThrows(IllegalArgumentException.class, () -> patientService.registerPatient(p1));

        // DOB in future
        Patient p2 = new Patient();
        p2.setFullName("Future Bob");
        p2.setDateOfBirth(LocalDate.now().plusDays(1));
        p2.setPhone("+12345678");
        assertThrows(IllegalArgumentException.class, () -> patientService.registerPatient(p2));

        // Invalid phone
        Patient p3 = new Patient();
        p3.setFullName("Valid Name");
        p3.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p3.setPhone("abc");
        assertThrows(IllegalArgumentException.class, () -> patientService.registerPatient(p3));
    }

    @Test
    void testRegistrationRequiresAuthentication() {
        SessionContext.clear();
        Patient p = new Patient();
        p.setFullName("Unauthenticated Guy");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setPhone("+12345678");

        assertThrows(UnauthorizedAccessException.class, () -> patientService.registerPatient(p));
    }

    @Test
    void testGetPatientByIdAndNumber() {
        Patient patient = new Patient();
        patient.setFullName("Charlie Brown");
        patient.setDateOfBirth(LocalDate.of(1985, 3, 20));
        patient.setPhone("+1 555 333 4444");

        Patient registered = patientService.registerPatient(patient);

        Patient foundById = patientService.getPatientById(registered.getId());
        assertEquals("Charlie Brown", foundById.getFullName());

        Patient foundByNumber = patientService.getPatientByNumber(registered.getPatientNumber());
        assertEquals(registered.getId(), foundByNumber.getId());

        assertThrows(PatientNotFoundException.class, () -> patientService.getPatientById("unknown-id"));
    }

    @Test
    void testArchivalPermissions() {
        Patient patient = new Patient();
        patient.setFullName("David Miller");
        patient.setDateOfBirth(LocalDate.of(1975, 8, 10));
        patient.setPhone("+1 555 111 2222");
        Patient registered = patientService.registerPatient(patient);

        // Receptionist cannot archive
        assertThrows(UnauthorizedAccessException.class, () -> patientService.archivePatient(registered.getId()));

        // Switch to Doctor
        User doctor = new User("doc-1", "Dr. Jane", "dr.jane@clinic.local", "hash", Role.DOCTOR, true);
        SessionContext.setCurrentUser(doctor);

        patientService.archivePatient(registered.getId());
        Patient archived = patientService.getPatientById(registered.getId());
        assertTrue(archived.isArchived());
    }

    @Test
    void testSearchAndPagination() {
        for (int i = 1; i <= 5; i++) {
            Patient p = new Patient();
            p.setFullName("Searchable Patient " + i);
            p.setDateOfBirth(LocalDate.of(1990, 1, i));
            p.setPhone("+1 555 000 000" + i);
            patientService.registerPatient(p);
        }

        List<Patient> matches = patientService.searchPatients("Patient 3", false);
        assertEquals(1, matches.size());
        assertEquals("Searchable Patient 3", matches.get(0).getFullName());

        List<Patient> page1 = patientService.getPatients(0, 3, false);
        assertEquals(3, page1.size());
    }

    // In-Memory Test Stub for PatientDAO
    private static class InMemoryPatientDAO implements PatientDAO {
        private final Map<String, Patient> storage = new LinkedHashMap<>();
        private int sequence = 1;

        @Override
        public void save(Patient entity) {
            storage.put(entity.getId(), entity);
        }

        @Override
        public Optional<Patient> findById(String id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Patient> findByPatientNumber(String patientNumber) {
            return storage.values().stream()
                    .filter(p -> p.getPatientNumber().equalsIgnoreCase(patientNumber))
                    .findFirst();
        }

        @Override
        public List<Patient> findAll(int offset, int limit, boolean includeArchived) {
            return storage.values().stream()
                    .filter(p -> includeArchived || !p.isArchived())
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Patient> search(String query, boolean includeArchived) {
            String lower = query.toLowerCase();
            return storage.values().stream()
                    .filter(p -> includeArchived || !p.isArchived())
                    .filter(p -> (p.getPatientNumber() != null && p.getPatientNumber().toLowerCase().contains(lower)) ||
                                 (p.getFullName() != null && p.getFullName().toLowerCase().contains(lower)) ||
                                 (p.getPhone() != null && p.getPhone().contains(query)))
                    .toList();
        }

        @Override
        public void update(Patient entity) {
            storage.put(entity.getId(), entity);
        }

        @Override
        public void archive(String id) {
            Patient p = storage.get(id);
            if (p != null) p.setArchived(true);
        }

        @Override
        public void unarchive(String id) {
            Patient p = storage.get(id);
            if (p != null) p.setArchived(false);
        }

        @Override
        public void delete(String id) {
            archive(id);
        }

        @Override
        public int countActive() {
            return (int) storage.values().stream().filter(p -> !p.isArchived()).count();
        }

        @Override
        public int getNextSequenceNumber(int year) {
            return sequence++;
        }
    }
}
