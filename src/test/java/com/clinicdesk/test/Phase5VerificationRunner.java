package com.clinicdesk.test;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.MedicalRecordDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.MedicalRecordService;
import com.clinicdesk.service.PatientService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class Phase5VerificationRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Phase 5 Verification...");

        Map<String, MedicalRecord> mrStorage = new LinkedHashMap<>();
        Map<String, Appointment> appStorage = new LinkedHashMap<>();
        Map<String, Patient> patStorage = new LinkedHashMap<>();
        Map<String, User> userStorage = new LinkedHashMap<>();

        MedicalRecordDAO mrDao = new MedicalRecordDAO() {
            @Override public void save(MedicalRecord entity) { mrStorage.put(entity.getId(), entity); }
            @Override public Optional<MedicalRecord> findById(String id) { return Optional.ofNullable(mrStorage.get(id)); }
            @Override public Optional<MedicalRecord> findByAppointmentId(String aid) {
                return mrStorage.values().stream().filter(m -> m.getAppointmentId().equals(aid)).findFirst();
            }
            @Override public List<MedicalRecord> findByPatientId(String pid) {
                return mrStorage.values().stream().filter(m -> m.getPatientId().equals(pid))
                        .sorted(Comparator.comparing(MedicalRecord::getVisitDate).reversed())
                        .toList();
            }
            @Override public List<MedicalRecord> findAll(int o, int l) { return new ArrayList<>(mrStorage.values()); }
            @Override public List<MedicalRecord> findRecent(int l) { return new ArrayList<>(mrStorage.values()); }
            @Override public void update(MedicalRecord entity) { mrStorage.put(entity.getId(), entity); }
            @Override public void delete(String id) { mrStorage.remove(id); }
            @Override public int countTotal() { return mrStorage.size(); }
        };

        AppointmentDAO appDao = new AppointmentDAO() {
            @Override public void save(Appointment entity) { appStorage.put(entity.getId(), entity); }
            @Override public Optional<Appointment> findById(String id) { return Optional.ofNullable(appStorage.get(id)); }
            @Override public List<Appointment> findByPatientId(String p) { return new ArrayList<>(appStorage.values()); }
            @Override public List<Appointment> findByDoctorAndDateRange(String d, LocalDateTime s, LocalDateTime e) { return List.of(); }
            @Override public List<Appointment> findByDate(LocalDate d) { return List.of(); }
            @Override public List<Appointment> findUpcoming(int l) { return List.of(); }
            @Override public boolean hasDoctorConflict(String d, LocalDateTime s, LocalDateTime e, String x) { return false; }
            @Override public void update(Appointment entity) { appStorage.put(entity.getId(), entity); }
            @Override public void updateStatus(String id, AppointmentStatus status) {
                Appointment a = appStorage.get(id);
                if (a != null) a.setStatus(status);
            }
            @Override public void delete(String id) { }
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate d) { return Map.of(); }
        };

        PatientDAO patDao = new PatientDAO() {
            @Override public void save(Patient entity) { patStorage.put(entity.getId(), entity); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patStorage.get(id)); }
            @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
            @Override public List<Patient> findAll(int o, int l, boolean i) { return new ArrayList<>(patStorage.values()); }
            @Override public List<Patient> search(String q, boolean i) { return List.of(); }
            @Override public void update(Patient entity) { patStorage.put(entity.getId(), entity); }
            @Override public void archive(String id) { }
            @Override public void unarchive(String id) { }
            @Override public void delete(String id) { }
            @Override public int countActive() { return patStorage.size(); }
            @Override public int getNextSequenceNumber(int y) { return patStorage.size() + 1; }
        };

        UserDAO userDao = new UserDAO() {
            @Override public void save(User entity) { userStorage.put(entity.getId(), entity); }
            @Override public Optional<User> findById(String id) { return Optional.ofNullable(userStorage.get(id)); }
            @Override public Optional<User> findByEmail(String e) { return Optional.empty(); }
            @Override public List<User> findAll(boolean i) { return List.of(); }
            @Override public List<User> findByRole(Role r) { return List.of(); }
            @Override public void update(User entity) { userStorage.put(entity.getId(), entity); }
            @Override public void updatePassword(String id, String p) { }
            @Override public void setActiveStatus(String id, boolean a) { }
            @Override public void delete(String id) { }
        };

        PatientService patService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patService, userDao);
        MedicalRecordService mrService = new MedicalRecordService(mrDao, patService, appService);

        // Seed data
        User doctor = new User("doc-1", "Dr. Sarah Smith", "dr.smith@clinic.local", "hash", Role.DOCTOR, true);
        User receptionist = new User("rec-1", "Receptionist", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);
        userStorage.put(doctor.getId(), doctor);
        userStorage.put(receptionist.getId(), receptionist);

        Patient patient = new Patient("pat-1", "CD-2026-0001", "Bruce Wayne", LocalDate.of(1985, 2, 19), Gender.MALE, "+15551234567", null, null, null, "O+", null);
        patStorage.put(patient.getId(), patient);

        Appointment app1 = new Appointment("app-1", patient.getId(), doctor.getId(), LocalDateTime.now().withHour(10), 30, "Shoulder pain", AppointmentStatus.CHECKED_IN);
        appStorage.put(app1.getId(), app1);

        // 1. Receptionist rejected from creating medical record
        SessionContext.setCurrentUser(receptionist);
        try {
            MedicalRecord r = new MedicalRecord("mr-rec", patient.getId(), app1.getId(), doctor.getId(), LocalDate.now(), "Pain", "Dislocation", null);
            mrService.createMedicalRecord(r);
            throw new AssertionError("Receptionist must not create medical records");
        } catch (UnauthorizedAccessException e) {
            System.out.println("✓ Receptionist denied permission to create medical record.");
        }

        // 2. Doctor creates medical record
        SessionContext.setCurrentUser(doctor);
        MedicalRecord rec1 = new MedicalRecord("mr-1", patient.getId(), app1.getId(), doctor.getId(),
                LocalDate.now(), "Right shoulder acute pain following fall", "Acromioclavicular sprain grade I", LocalDate.now().plusDays(14));
        MedicalRecord created = mrService.createMedicalRecord(rec1);
        assert created.getId() != null : "Created ID is null";
        assert appStorage.get(app1.getId()).getStatus() == AppointmentStatus.COMPLETED : "Appointment status should be COMPLETED";
        System.out.println("✓ Medical record created and appointment marked COMPLETED.");

        // 3. Prevent duplicate record for same appointment
        try {
            MedicalRecord duplicate = new MedicalRecord("mr-dup", patient.getId(), app1.getId(), doctor.getId(),
                    LocalDate.now(), "Second complaint", "Second diagnosis", null);
            mrService.createMedicalRecord(duplicate);
            throw new AssertionError("Duplicate record for same appointment must fail");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Duplicate medical record for same appointment prevented.");
        }

        // 4. Longitudinal medical history chronological retrieval
        Appointment app2 = new Appointment("app-2", patient.getId(), doctor.getId(), LocalDateTime.now().minusMonths(2), 30, "Follow up", AppointmentStatus.COMPLETED);
        appStorage.put(app2.getId(), app2);
        MedicalRecord olderRecord = new MedicalRecord("mr-old", patient.getId(), app2.getId(), doctor.getId(),
                LocalDate.now().minusMonths(2), "Initial injury", "Contusion", null);
        mrDao.save(olderRecord);

        List<MedicalRecord> history = mrService.getPatientMedicalHistory(patient.getId());
        assert history.size() == 2 : "Expected 2 medical records";
        assert history.get(0).getVisitDate().isAfter(history.get(1).getVisitDate()) : "History must be sorted descending";
        System.out.println("✓ Longitudinal medical history retrieved chronologically.");

        System.out.println("\nALL PHASE 5 VERIFICATIONS PASSED SUCCESSFULLY!");
    }
}
