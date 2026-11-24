package com.clinicdesk.test;

import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.AppointmentConflictException;
import com.clinicdesk.exception.InvalidAppointmentStateException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AppointmentService;
import com.clinicdesk.service.PatientService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class Phase4VerificationRunner {

    public static void main(String[] args) {
        System.out.println("Starting Phase 4 Verification...");

        Map<String, Appointment> appStorage = new LinkedHashMap<>();
        Map<String, Patient> patStorage = new LinkedHashMap<>();
        Map<String, User> userStorage = new LinkedHashMap<>();

        AppointmentDAO appDao = new AppointmentDAO() {
            @Override public void save(Appointment entity) { appStorage.put(entity.getId(), entity); }
            @Override public Optional<Appointment> findById(String id) { return Optional.ofNullable(appStorage.get(id)); }
            @Override public List<Appointment> findByPatientId(String pid) { return appStorage.values().stream().filter(a -> a.getPatientId().equals(pid)).toList(); }
            @Override public List<Appointment> findByDoctorAndDateRange(String did, LocalDateTime s, LocalDateTime e) {
                return appStorage.values().stream().filter(a -> a.getDoctorId().equals(did) && !a.getScheduledAt().isBefore(s) && !a.getScheduledAt().isAfter(e)).toList();
            }
            @Override public List<Appointment> findByDate(LocalDate d) { return appStorage.values().stream().filter(a -> a.getScheduledAt().toLocalDate().equals(d)).toList(); }
            @Override public List<Appointment> findUpcoming(int l) { return appStorage.values().stream().filter(a -> a.getScheduledAt().isAfter(LocalDateTime.now())).limit(l).toList(); }
            @Override public boolean hasDoctorConflict(String did, LocalDateTime s, LocalDateTime e, String excl) {
                return appStorage.values().stream()
                        .filter(a -> a.getDoctorId().equals(did))
                        .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED && a.getStatus() != AppointmentStatus.NO_SHOW)
                        .filter(a -> excl == null || !a.getId().equals(excl))
                        .anyMatch(a -> a.overlapsWith(s, e));
            }
            @Override public void update(Appointment entity) { appStorage.put(entity.getId(), entity); }
            @Override public void updateStatus(String id, AppointmentStatus status) {
                Appointment a = appStorage.get(id);
                if (a != null) a.setStatus(status);
            }
            @Override public void delete(String id) { updateStatus(id, AppointmentStatus.CANCELLED); }
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) {
                Map<AppointmentStatus, Integer> map = new EnumMap<>(AppointmentStatus.class);
                for (Appointment a : findByDate(date)) {
                    map.put(a.getStatus(), map.getOrDefault(a.getStatus(), 0) + 1);
                }
                return map;
            }
        };

        PatientDAO patDao = new PatientDAO() {
            @Override public void save(Patient entity) { patStorage.put(entity.getId(), entity); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patStorage.get(id)); }
            @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
            @Override public List<Patient> findAll(int o, int l, boolean inc) { return new ArrayList<>(patStorage.values()); }
            @Override public List<Patient> search(String q, boolean inc) { return new ArrayList<>(patStorage.values()); }
            @Override public void update(Patient entity) { patStorage.put(entity.getId(), entity); }
            @Override public void archive(String id) { patStorage.get(id).setArchived(true); }
            @Override public void unarchive(String id) { patStorage.get(id).setArchived(false); }
            @Override public void delete(String id) { archive(id); }
            @Override public int countActive() { return patStorage.size(); }
            @Override public int getNextSequenceNumber(int year) { return patStorage.size() + 1; }
        };

        UserDAO userDao = new UserDAO() {
            @Override public void save(User entity) { userStorage.put(entity.getId(), entity); }
            @Override public Optional<User> findById(String id) { return Optional.ofNullable(userStorage.get(id)); }
            @Override public Optional<User> findByEmail(String email) { return Optional.empty(); }
            @Override public List<User> findAll(boolean inc) { return new ArrayList<>(userStorage.values()); }
            @Override public List<User> findByRole(Role role) { return userStorage.values().stream().filter(u -> u.getRole() == role).toList(); }
            @Override public void update(User entity) { userStorage.put(entity.getId(), entity); }
            @Override public void updatePassword(String id, String p) { }
            @Override public void setActiveStatus(String id, boolean a) { }
            @Override public void delete(String id) { }
        };

        PatientService patService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patService, userDao);

        // Seed doctor & patient
        User doctor = new User("doc-1", "Dr. Sarah", "dr.sarah@clinic.local", "hash", Role.DOCTOR, true);
        userStorage.put(doctor.getId(), doctor);

        Patient activePatient = new Patient("pat-1", "CD-2026-0001", "Emma Watson", LocalDate.of(1990, 4, 15), Gender.FEMALE, "+15551234567", "emma@test.com", null, null, "O+", null);
        patStorage.put(activePatient.getId(), activePatient);

        Patient archivedPatient = new Patient("pat-2", "CD-2026-0002", "Archived Patient", LocalDate.of(1985, 1, 1), Gender.MALE, "+15559876543", null, null, null, null, null);
        archivedPatient.setArchived(true);
        patStorage.put(archivedPatient.getId(), archivedPatient);

        // Authenticate receptionist
        User receptionist = new User("rec-1", "Reception", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);
        SessionContext.setCurrentUser(receptionist);

        // 1. Schedule appointment success
        LocalDateTime slot1 = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        Appointment app1 = new Appointment("a1", activePatient.getId(), doctor.getId(), slot1, 30, "Annual Checkup", AppointmentStatus.SCHEDULED);
        Appointment created = appService.scheduleAppointment(app1);
        assert created.getId() != null : "Created ID is null";
        System.out.println("✓ Appointment scheduled successfully: " + slot1);

        // 2. Reject archived patient
        try {
            Appointment archApp = new Appointment("a2", archivedPatient.getId(), doctor.getId(), slot1.plusHours(2), 30, "Check", AppointmentStatus.SCHEDULED);
            appService.scheduleAppointment(archApp);
            throw new AssertionError("Archived patient scheduling should fail");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Archived patient appointment rejected.");
        }

        // 3. Reject past date
        try {
            Appointment pastApp = new Appointment("a3", activePatient.getId(), doctor.getId(), LocalDateTime.now().minusDays(1), 30, "Check", AppointmentStatus.SCHEDULED);
            appService.scheduleAppointment(pastApp);
            throw new AssertionError("Past date appointment should fail");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Past appointment date rejected.");
        }

        // 4. Overlapping conflict detection
        try {
            // Overlaps slot1 (10:00 - 10:30) with 10:15 - 10:45
            Appointment conflictApp = new Appointment("a4", activePatient.getId(), doctor.getId(), slot1.plusMinutes(15), 30, "Double Booking", AppointmentStatus.SCHEDULED);
            appService.scheduleAppointment(conflictApp);
            throw new AssertionError("Doctor conflict detection failed");
        } catch (AppointmentConflictException e) {
            System.out.println("✓ Doctor double-booking conflict detected and rejected.");
        }

        // 5. State transitions
        appService.transitionStatus(created.getId(), AppointmentStatus.CHECKED_IN);
        assert appService.getAppointmentById(created.getId()).getStatus() == AppointmentStatus.CHECKED_IN;
        System.out.println("✓ Transitioned to CHECKED_IN.");

        // Receptionist cannot complete
        try {
            appService.transitionStatus(created.getId(), AppointmentStatus.COMPLETED);
            throw new AssertionError("Receptionist must not complete appointment");
        } catch (UnauthorizedAccessException e) {
            System.out.println("✓ Receptionist denied permission to mark visit COMPLETED.");
        }

        // Doctor marks completed
        SessionContext.setCurrentUser(doctor);
        appService.transitionStatus(created.getId(), AppointmentStatus.COMPLETED);
        assert appService.getAppointmentById(created.getId()).getStatus() == AppointmentStatus.COMPLETED;
        System.out.println("✓ Doctor successfully marked appointment COMPLETED.");

        // COMPLETED cannot be cancelled
        try {
            appService.transitionStatus(created.getId(), AppointmentStatus.CANCELLED);
            throw new AssertionError("Completed appointment must not transition to cancelled");
        } catch (InvalidAppointmentStateException e) {
            System.out.println("✓ Illegal transition from terminal state COMPLETED rejected.");
        }

        System.out.println("\nALL PHASE 4 VERIFICATIONS PASSED SUCCESSFULLY!");
    }
}
