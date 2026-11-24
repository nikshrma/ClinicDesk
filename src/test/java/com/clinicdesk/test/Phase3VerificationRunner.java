package com.clinicdesk.test;

import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.PatientNotFoundException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.Gender;
import com.clinicdesk.model.Patient;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.PatientService;

import java.time.LocalDate;
import java.util.*;

public class Phase3VerificationRunner {

    public static void main(String[] args) {
        System.out.println("Starting Phase 3 Verification...");

        Map<String, Patient> storage = new LinkedHashMap<>();
        PatientDAO mockDao = new PatientDAO() {
            private int seq = 1;
            @Override public void save(Patient entity) { storage.put(entity.getId(), entity); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(storage.get(id)); }
            @Override public Optional<Patient> findByPatientNumber(String num) {
                return storage.values().stream().filter(p -> p.getPatientNumber().equalsIgnoreCase(num)).findFirst();
            }
            @Override public List<Patient> findAll(int offset, int limit, boolean inc) {
                return storage.values().stream().filter(p -> inc || !p.isArchived()).skip(offset).limit(limit).toList();
            }
            @Override public List<Patient> search(String q, boolean inc) {
                String l = q.toLowerCase();
                return storage.values().stream().filter(p -> inc || !p.isArchived())
                        .filter(p -> p.getFullName().toLowerCase().contains(l) || p.getPatientNumber().toLowerCase().contains(l))
                        .toList();
            }
            @Override public void update(Patient entity) { storage.put(entity.getId(), entity); }
            @Override public void archive(String id) { storage.get(id).setArchived(true); }
            @Override public void unarchive(String id) { storage.get(id).setArchived(false); }
            @Override public void delete(String id) { archive(id); }
            @Override public int countActive() { return (int) storage.values().stream().filter(p -> !p.isArchived()).count(); }
            @Override public int getNextSequenceNumber(int year) { return seq++; }
        };

        PatientService service = new PatientService(mockDao);

        // 1. Unauthenticated registration rejected
        SessionContext.clear();
        try {
            Patient p = new Patient();
            p.setFullName("John Doe");
            p.setDateOfBirth(LocalDate.of(1990, 1, 1));
            p.setPhone("+12345678");
            service.registerPatient(p);
            throw new AssertionError("Unauthenticated registration should fail");
        } catch (UnauthorizedAccessException e) {
            System.out.println("✓ Unauthenticated patient registration rejected.");
        }

        // Authenticate as Receptionist
        User receptionist = new User("r1", "Receptionist", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);
        SessionContext.setCurrentUser(receptionist);

        // 2. Validate patient input
        try {
            Patient p = new Patient();
            p.setFullName("X"); // Too short
            p.setDateOfBirth(LocalDate.of(1990, 1, 1));
            p.setPhone("+12345678");
            service.registerPatient(p);
            throw new AssertionError("Short name should fail");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Invalid patient name rejected.");
        }

        // 3. Register valid patient
        Patient p = new Patient();
        p.setFullName("Eleanor Vance");
        p.setDateOfBirth(LocalDate.of(1992, 6, 20));
        p.setGender(Gender.FEMALE);
        p.setPhone("+1 555 456 7890");
        p.setEmail("eleanor@example.com");
        p.setBloodGroup("B+");
        p.setAllergies("Penicillin");

        Patient registered = service.registerPatient(p);
        assert registered.getId() != null : "ID must be generated";
        assert registered.getPatientNumber() != null && registered.getPatientNumber().startsWith("CD-") : "Patient number format invalid";
        assert registered.getAge() > 0 : "Age calculation failed";
        System.out.println("✓ Patient registered with number: " + registered.getPatientNumber());

        // 4. Lookup by ID and Number
        Patient byId = service.getPatientById(registered.getId());
        assert byId.getFullName().equals("Eleanor Vance") : "Lookup by ID mismatch";
        Patient byNum = service.getPatientByNumber(registered.getPatientNumber());
        assert byNum.getId().equals(registered.getId()) : "Lookup by number mismatch";
        System.out.println("✓ Patient lookup by ID and number verified.");

        // 5. Archival rules
        // Receptionist cannot archive
        try {
            service.archivePatient(registered.getId());
            throw new AssertionError("Receptionist must not be able to archive");
        } catch (UnauthorizedAccessException e) {
            System.out.println("✓ Receptionist denied archival permission.");
        }

        // Doctor archives patient
        User doctor = new User("d1", "Dr. House", "house@clinic.local", "hash", Role.DOCTOR, true);
        SessionContext.setCurrentUser(doctor);
        service.archivePatient(registered.getId());
        assert service.getPatientById(registered.getId()).isArchived() : "Patient should be marked archived";
        System.out.println("✓ Doctor successfully archived patient.");

        // 6. Search and pagination
        List<Patient> activeSearch = service.searchPatients("Eleanor", false);
        assert activeSearch.isEmpty() : "Archived patient should not appear in active search";
        List<Patient> allSearch = service.searchPatients("Eleanor", true);
        assert allSearch.size() == 1 : "Archived patient should appear when includeArchived=true";
        System.out.println("✓ Search filter with archival status verified.");

        System.out.println("\nALL PHASE 3 VERIFICATIONS PASSED SUCCESSFULLY!");
    }
}
