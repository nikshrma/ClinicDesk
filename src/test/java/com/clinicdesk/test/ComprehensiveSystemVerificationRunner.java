package com.clinicdesk.test;

import com.clinicdesk.backup.BackupData;
import com.clinicdesk.backup.BackupService;
import com.clinicdesk.camera.CameraService;
import com.clinicdesk.camera.ImageProcessingService;
import com.clinicdesk.camera.OpenCVCameraService;
import com.clinicdesk.dao.*;
import com.clinicdesk.exception.*;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.*;
import com.clinicdesk.storage.DocumentStorageService;
import com.clinicdesk.util.PasswordUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Master Verification Suite executing comprehensive end-to-end integration tests across
 * all architectural modules of ClinicDesk in strict headless mode.
 */
public class ComprehensiveSystemVerificationRunner {

    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("     CLINICDESK — COMPREHENSIVE SYSTEM INTEGRATION VERIFICATION        ");
        System.out.println("========================================================================");

        int passed = 0;
        int total = 8;

        try {
            // Module 1: Authentication & User RBAC
            testAuthenticationAndRBAC();
            passed++;

            // Module 2: Patient Registration & Sequential ID Generation
            testPatientManagement();
            passed++;

            // Module 3: Scheduling & Double-Booking Conflict Prevention
            testAppointmentScheduling();
            passed++;

            // Module 4: Medical Records & Status Lifecycle
            testMedicalRecords();
            passed++;

            // Module 5: Document Storage & Atomic Compensation Rollback
            testDocumentStorageAndRollback();
            passed++;

            // Module 6: Camera Simulation & Image Transformations
            testCameraAndImageProcessing();
            passed++;

            // Module 7: Overview Dashboard Aggregates
            testDashboardAggregates();
            passed++;

            // Module 8: Java Object Serialization & SHA-256 Tamper Rejection
            testBackupAndSerialization();
            passed++;

            System.out.println("========================================================================");
            System.out.printf("  ALL %d/%d MODULE VERIFICATION SUITES COMPLETED SUCCESSFULLY! (100%%)\n", passed, total);
            System.out.println("========================================================================");
        } catch (Throwable t) {
            System.err.println("\n❌ CRITICAL VERIFICATION FAILURE AT MODULE " + (passed + 1) + ":");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void testAuthenticationAndRBAC() {
        System.out.print("[Module 1/8] Authentication, Password Hashing & RBAC... ");

        String rawPassword = "SecureDoctorPassword2026!";
        String hash = PasswordUtil.hashPassword(rawPassword);
        assert PasswordUtil.verifyPassword(rawPassword, hash) : "BCrypt/PBKDF2 verification failed for valid password";
        assert !PasswordUtil.verifyPassword("WrongPassword", hash) : "Verification succeeded for invalid password";

        User doctor = new User("USR-DOC-1", "doctor@clinicdesk.com", hash, "Dr. Meredith Grey", Role.DOCTOR, true, Instant.now(), null);
        SessionContext.setCurrentUser(doctor);

        assert SessionContext.isAuthenticated() : "Expected user to be authenticated";
        assert SessionContext.hasRole(Role.DOCTOR) : "Expected user to have DOCTOR role";
        assert !SessionContext.hasRole(Role.ADMIN) : "Doctor should not have ADMIN role";

        boolean blocked = false;
        try {
            SessionContext.requireRole(Role.ADMIN);
        } catch (UnauthorizedAccessException e) {
            blocked = true;
        }
        assert blocked : "SessionContext failed to block non-admin from admin resource";

        System.out.println("PASSED ✓");
    }

    private static void testPatientManagement() throws SQLException {
        System.out.print("[Module 2/8] Patient Management & Sequential ID Generation... ");

        Map<String, Patient> patientDb = new HashMap<>();
        PatientDAO patientDAO = new PatientDAO() {
            int seq = 1;
            @Override public void save(Patient p) { patientDb.put(p.getId(), p); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patientDb.get(id)); }
            @Override public void update(Patient p) { patientDb.put(p.getId(), p); }
            @Override public void delete(String id) { patientDb.remove(id); }
            @Override public Optional<Patient> findByPatientNumber(String num) { return patientDb.values().stream().filter(p -> p.getPatientNumber().equals(num)).findFirst(); }
            @Override public List<Patient> findAll(int offset, int limit, boolean includeArchived) { return patientDb.values().stream().filter(p -> includeArchived || !p.isArchived()).toList(); }
            @Override public List<Patient> search(String query, boolean includeArchived) { return new ArrayList<>(patientDb.values()); }
            @Override public void archive(String id) { Patient p = patientDb.get(id); if (p != null) p.setArchived(true); }
            @Override public void unarchive(String id) { Patient p = patientDb.get(id); if (p != null) p.setArchived(false); }
            @Override public int countActive() { return (int) patientDb.values().stream().filter(p -> !p.isArchived()).count(); }
            @Override public int getNextSequenceNumber(int year) { return seq++; }
        };

        PatientService patientService = new PatientService(patientDAO);

        Patient p1 = new Patient();
        p1.setFullName("John Doe");
        p1.setPhone("+15551234567");
        p1.setEmail("john.doe@example.com");
        p1.setDateOfBirth(LocalDate.of(1985, 5, 20));
        p1.setGender(Gender.MALE);

        Patient registered = patientService.registerPatient(p1);
        int currentYear = LocalDate.now().getYear();
        String expectedPrefix = "CD-" + currentYear + "-";
        assert registered.getPatientNumber().startsWith(expectedPrefix) : "Sequential ID format mismatch: " + registered.getPatientNumber();
        assert patientService.getActivePatientCount() == 1 : "Expected 1 active patient";

        patientService.archivePatient(registered.getId());
        assert patientService.getActivePatientCount() == 0 : "Archived patient should not be counted in active patients";

        System.out.println("PASSED ✓");
    }

    private static void testAppointmentScheduling() throws SQLException {
        System.out.print("[Module 3/8] Appointment Scheduling & Conflict Prevention... ");

        Map<String, Appointment> appDb = new HashMap<>();
        Map<String, Patient> patDb = new HashMap<>();
        Map<String, User> userDb = new HashMap<>();

        Patient p = new Patient("pat-1", "CD-2026-0001", "Emma Watson", LocalDate.of(1990, 4, 15), Gender.FEMALE, "+15551234567", "emma@test.com", null, null, "O+", null);
        patDb.put(p.getId(), p);

        User doctor = new User("doc-1", "Dr. Sarah", "dr.sarah@clinic.local", "hash", Role.DOCTOR, true);
        userDb.put(doctor.getId(), doctor);

        AppointmentDAO appointmentDAO = new AppointmentDAO() {
            @Override public void save(Appointment entity) { appDb.put(entity.getId(), entity); }
            @Override public Optional<Appointment> findById(String id) { return Optional.ofNullable(appDb.get(id)); }
            @Override public void update(Appointment entity) { appDb.put(entity.getId(), entity); }
            @Override public void delete(String id) { appDb.remove(id); }
            @Override public List<Appointment> findByPatientId(String pid) { return appDb.values().stream().filter(a -> a.getPatientId().equals(pid)).toList(); }
            @Override public List<Appointment> findByDoctorAndDateRange(String doc, LocalDateTime start, LocalDateTime end) { return new ArrayList<>(appDb.values()); }
            @Override public List<Appointment> findByDate(LocalDate date) { return new ArrayList<>(appDb.values()); }
            @Override public List<Appointment> findUpcoming(int limit) { return new ArrayList<>(appDb.values()); }
            @Override public boolean hasDoctorConflict(String doc, LocalDateTime start, LocalDateTime end, String excludeId) {
                return appDb.values().stream()
                        .filter(a -> !a.getId().equals(excludeId) && a.getDoctorId().equals(doc) && a.getStatus() != AppointmentStatus.CANCELLED)
                        .anyMatch(a -> a.getScheduledAt().isBefore(end) && a.getScheduledAt().plusMinutes(a.getDurationMinutes()).isAfter(start));
            }
            @Override public void updateStatus(String id, AppointmentStatus status) { Appointment a = appDb.get(id); if (a != null) a.setStatus(status); }
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) { return Collections.emptyMap(); }
        };

        PatientDAO patientDAO = new PatientDAO() {
            @Override public void save(Patient entity) { patDb.put(entity.getId(), entity); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patDb.get(id)); }
            @Override public void update(Patient entity) { patDb.put(entity.getId(), entity); }
            @Override public void delete(String id) { patDb.remove(id); }
            @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
            @Override public List<Patient> findAll(int o, int l, boolean inc) { return new ArrayList<>(patDb.values()); }
            @Override public List<Patient> search(String q, boolean inc) { return new ArrayList<>(patDb.values()); }
            @Override public void archive(String id) {}
            @Override public void unarchive(String id) {}
            @Override public int countActive() { return patDb.size(); }
            @Override public int getNextSequenceNumber(int year) { return 1; }
        };

        UserDAO userDAO = new UserDAO() {
            @Override public void save(User entity) { userDb.put(entity.getId(), entity); }
            @Override public Optional<User> findById(String id) { return Optional.ofNullable(userDb.get(id)); }
            @Override public Optional<User> findByEmail(String email) { return Optional.empty(); }
            @Override public List<User> findAll(boolean inc) { return new ArrayList<>(userDb.values()); }
            @Override public List<User> findByRole(Role role) { return userDb.values().stream().filter(u -> u.getRole() == role).toList(); }
            @Override public void update(User entity) { userDb.put(entity.getId(), entity); }
            @Override public void updatePassword(String id, String p) {}
            @Override public void setActiveStatus(String id, boolean a) {}
            @Override public void delete(String id) {}
        };

        PatientService patService = new PatientService(patientDAO);
        AppointmentService appointmentService = new AppointmentService(appointmentDAO, patService, userDAO);

        LocalDateTime slot = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Appointment app1 = new Appointment("a1", p.getId(), doctor.getId(), slot, 30, "Consultation", AppointmentStatus.SCHEDULED);
        Appointment scheduled = appointmentService.scheduleAppointment(app1);
        assert scheduled.getId() != null : "Created ID is null";

        // Conflict check
        boolean conflictCaught = false;
        try {
            Appointment conflictApp = new Appointment("a2", p.getId(), doctor.getId(), slot.plusMinutes(15), 30, "Conflicting", AppointmentStatus.SCHEDULED);
            appointmentService.scheduleAppointment(conflictApp);
        } catch (AppointmentConflictException e) {
            conflictCaught = true;
        }
        assert conflictCaught : "AppointmentService failed to detect doctor double-booking conflict";

        System.out.println("PASSED ✓");
    }

    private static void testMedicalRecords() throws SQLException {
        System.out.print("[Module 4/8] Medical Records & Auto-Complete Lifecycle... ");

        Map<String, MedicalRecord> mrStorage = new HashMap<>();
        Map<String, Appointment> appStorage = new HashMap<>();
        Map<String, Patient> patStorage = new HashMap<>();
        Map<String, User> userStorage = new HashMap<>();

        User doctor = new User("doc-1", "Dr. Sarah Smith", "dr.smith@clinic.local", "hash", Role.DOCTOR, true);
        User receptionist = new User("rec-1", "Receptionist", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);
        userStorage.put(doctor.getId(), doctor);
        userStorage.put(receptionist.getId(), receptionist);

        Patient patient = new Patient("pat-1", "CD-2026-0001", "Bruce Wayne", LocalDate.of(1985, 2, 19), Gender.MALE, "+15551234567", null, null, null, "O+", null);
        patStorage.put(patient.getId(), patient);

        Appointment app1 = new Appointment("app-1", patient.getId(), doctor.getId(), LocalDateTime.now().withHour(10), 30, "Shoulder pain", AppointmentStatus.CHECKED_IN);
        appStorage.put(app1.getId(), app1);

        MedicalRecordDAO mrDao = new MedicalRecordDAO() {
            @Override public void save(MedicalRecord mr) { mrStorage.put(mr.getId(), mr); }
            @Override public Optional<MedicalRecord> findById(String id) { return Optional.ofNullable(mrStorage.get(id)); }
            @Override public Optional<MedicalRecord> findByAppointmentId(String aid) { return mrStorage.values().stream().filter(m -> aid.equals(m.getAppointmentId())).findFirst(); }
            @Override public List<MedicalRecord> findByPatientId(String pid) { return mrStorage.values().stream().filter(m -> m.getPatientId().equals(pid)).toList(); }
            @Override public List<MedicalRecord> findAll(int o, int l) { return new ArrayList<>(mrStorage.values()); }
            @Override public List<MedicalRecord> findRecent(int l) { return new ArrayList<>(mrStorage.values()); }
            @Override public void update(MedicalRecord mr) { mrStorage.put(mr.getId(), mr); }
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
            @Override public void delete(String id) {}
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate d) { return Map.of(); }
        };

        PatientDAO patDao = new PatientDAO() {
            @Override public void save(Patient entity) { patStorage.put(entity.getId(), entity); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patStorage.get(id)); }
            @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
            @Override public List<Patient> findAll(int o, int l, boolean i) { return new ArrayList<>(patStorage.values()); }
            @Override public List<Patient> search(String q, boolean i) { return List.of(); }
            @Override public void update(Patient entity) { patStorage.put(entity.getId(), entity); }
            @Override public void archive(String id) {}
            @Override public void unarchive(String id) {}
            @Override public void delete(String id) {}
            @Override public int countActive() { return patStorage.size(); }
            @Override public int getNextSequenceNumber(int y) { return 1; }
        };

        UserDAO userDao = new UserDAO() {
            @Override public void save(User entity) { userStorage.put(entity.getId(), entity); }
            @Override public Optional<User> findById(String id) { return Optional.ofNullable(userStorage.get(id)); }
            @Override public Optional<User> findByEmail(String e) { return Optional.empty(); }
            @Override public List<User> findAll(boolean i) { return List.of(); }
            @Override public List<User> findByRole(Role r) { return List.of(); }
            @Override public void update(User entity) { userStorage.put(entity.getId(), entity); }
            @Override public void updatePassword(String id, String p) {}
            @Override public void setActiveStatus(String id, boolean a) {}
            @Override public void delete(String id) {}
        };

        PatientService patService = new PatientService(patDao);
        AppointmentService appService = new AppointmentService(appDao, patService, userDao);
        MedicalRecordService mrService = new MedicalRecordService(mrDao, patService, appService);

        // Receptionist denied
        SessionContext.setCurrentUser(receptionist);
        boolean blocked = false;
        try {
            MedicalRecord r = new MedicalRecord("mr-rec", patient.getId(), app1.getId(), doctor.getId(), LocalDate.now(), "Pain", "Dislocation", null);
            mrService.createMedicalRecord(r);
        } catch (UnauthorizedAccessException e) {
            blocked = true;
        }
        assert blocked : "Receptionist should not be allowed to create medical records";

        // Doctor succeeds and auto-completes appointment
        SessionContext.setCurrentUser(doctor);
        MedicalRecord rec1 = new MedicalRecord("mr-1", patient.getId(), app1.getId(), doctor.getId(),
                LocalDate.now(), "Right shoulder acute pain following fall", "Acromioclavicular sprain grade I", LocalDate.now().plusDays(14));
        MedicalRecord created = mrService.createMedicalRecord(rec1);
        assert created.getId() != null : "Created ID is null";
        assert app1.getStatus() == AppointmentStatus.COMPLETED : "Appointment was not updated to COMPLETED";

        System.out.println("PASSED ✓");
    }

    private static void testDocumentStorageAndRollback() throws Exception {
        System.out.print("[Module 5/8] Storage Service & Atomic Compensation Rollback... ");

        Map<String, byte[]> minioStorage = new HashMap<>();
        Map<String, Patient> patStorage = new HashMap<>();
        final int[] deleteCount = {0};
        final boolean[] simulateDbError = {false};

        Patient p = new Patient("pat-1", "CD-2026-0001", "Clark Kent", LocalDate.of(1982, 6, 18), Gender.MALE, "+15551234567", null, null, null, "O+", null);
        patStorage.put(p.getId(), p);

        DocumentStorageService mockStorage = new DocumentStorageService() {
            @Override public String upload(File file, String contentType) {
                String key = "keys/" + UUID.randomUUID() + "-" + file.getName();
                minioStorage.put(key, new byte[(int) file.length()]);
                return key;
            }
            @Override public InputStream download(String objectKey) { return new ByteArrayInputStream(minioStorage.get(objectKey)); }
            @Override public void delete(String objectKey) {
                deleteCount[0]++;
                minioStorage.remove(objectKey);
            }
            @Override public String generatePresignedUrl(String objectKey, int expirySeconds) { return "http://minio:9000/" + objectKey; }
            @Override public boolean isAvailable() { return true; }
        };

        DocumentDAO mockDao = new DocumentDAO() {
            @Override public void save(DocumentMetadata entity) throws SQLException {
                if (simulateDbError[0]) throw new SQLException("Database connection timeout during metadata insert");
            }
            @Override public Optional<DocumentMetadata> findById(String id) { return Optional.empty(); }
            @Override public void update(DocumentMetadata entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<DocumentMetadata> findByObjectKey(String k) { return Optional.empty(); }
            @Override public List<DocumentMetadata> findByPatientId(String pid) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByMedicalRecordId(String mrid) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByCategory(DocumentCategory cat) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findAll(int offset, int limit) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findRecent(int limit) { return Collections.emptyList(); }
            @Override public void archive(String id) {}
            @Override public int countTotal() { return 0; }
        };

        PatientDAO patDao = new PatientDAO() {
            @Override public void save(Patient entity) {}
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patStorage.get(id)); }
            @Override public void update(Patient entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
            @Override public List<Patient> findAll(int o, int l, boolean i) { return Collections.emptyList(); }
            @Override public List<Patient> search(String q, boolean i) { return Collections.emptyList(); }
            @Override public void archive(String id) {}
            @Override public void unarchive(String id) {}
            @Override public int countActive() { return 1; }
            @Override public int getNextSequenceNumber(int y) { return 1; }
        };

        PatientService patService = new PatientService(patDao);
        DocumentService docService = new DocumentService(mockDao, mockStorage, patService);
        User doctor = new User("doc-1", "Dr. Sarah", "sarah@clinic.local", "hash", Role.DOCTOR, true);
        SessionContext.setCurrentUser(doctor);

        File sampleDoc = File.createTempFile("sample_rx_", ".jpg");
        sampleDoc.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(sampleDoc)) {
            fos.write(new byte[1024]);
        }

        simulateDbError[0] = true;
        boolean rollbackSucceeded = false;
        try {
            docService.uploadDocument(sampleDoc, p.getId(), null, DocumentCategory.PRESCRIPTION, "rx.jpg", Instant.now());
        } catch (DocumentStorageException e) {
            rollbackSucceeded = true;
        }
        assert rollbackSucceeded : "Expected DocumentStorageException on simulated DB failure";
        assert deleteCount[0] == 1 : "MinIO object was not deleted during atomic compensation rollback";
        assert minioStorage.isEmpty() : "MinIO bucket still holds orphaned object after rollback";

        System.out.println("PASSED ✓");
    }

    private static void testCameraAndImageProcessing() throws Exception {
        System.out.print("[Module 6/8] Laptop Camera Preview & Image Transformation... ");

        ImageProcessingService imgService = new ImageProcessingService();
        BufferedImage frame = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        BufferedImage rot90 = imgService.rotate(frame, 90);
        assert rot90.getWidth() == frame.getHeight() && rot90.getHeight() == frame.getWidth() : "90 degree rotation dimension swap failed";

        BufferedImage flipH = imgService.flipHorizontal(frame);
        assert flipH.getWidth() == frame.getWidth() && flipH.getHeight() == frame.getHeight() : "Flip horizontal dimension mismatch";
        BufferedImage flipV = imgService.flipVertical(frame);
        assert flipV.getWidth() == frame.getWidth() && flipV.getHeight() == frame.getHeight() : "Flip vertical dimension mismatch";

        File exported = File.createTempFile("cam_test_", ".jpg");
        exported.deleteOnExit();
        imgService.saveAsJpeg(rot90, exported, 0.90f);
        assert exported.exists() && exported.length() > 0 : "Exported JPEG file missing or empty";

        System.out.println("PASSED ✓");
    }

    private static void testDashboardAggregates() throws SQLException {
        System.out.print("[Module 7/8] Overview Dashboard Real-Time Aggregation... ");

        PatientDAO patDao = new PatientDAO() {
            @Override public void save(Patient entity) {}
            @Override public Optional<Patient> findById(String id) { return Optional.empty(); }
            @Override public void update(Patient entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<Patient> findByPatientNumber(String patientNumber) { return Optional.empty(); }
            @Override public List<Patient> findAll(int offset, int limit, boolean includeArchived) { return Collections.emptyList(); }
            @Override public List<Patient> search(String query, boolean includeArchived) { return Collections.emptyList(); }
            @Override public void archive(String id) {}
            @Override public void unarchive(String id) {}
            @Override public int countActive() { return 42; }
            @Override public int getNextSequenceNumber(int year) { return 1; }
        };

        AppointmentDAO appDao = new AppointmentDAO() {
            @Override public void save(Appointment entity) {}
            @Override public Optional<Appointment> findById(String id) { return Optional.empty(); }
            @Override public void update(Appointment entity) {}
            @Override public void delete(String id) {}
            @Override public List<Appointment> findByPatientId(String patientId) { return Collections.emptyList(); }
            @Override public List<Appointment> findByDoctorAndDateRange(String doctorId, LocalDateTime start, LocalDateTime end) { return Collections.emptyList(); }
            @Override public List<Appointment> findByDate(LocalDate date) { return Collections.emptyList(); }
            @Override public List<Appointment> findUpcoming(int limit) { return Collections.emptyList(); }
            @Override public boolean hasDoctorConflict(String doctorId, LocalDateTime start, LocalDateTime end, String excludeAppointmentId) { return false; }
            @Override public void updateStatus(String appointmentId, AppointmentStatus status) {}
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) {
                Map<AppointmentStatus, Integer> map = new EnumMap<>(AppointmentStatus.class);
                map.put(AppointmentStatus.CHECKED_IN, 3);
                map.put(AppointmentStatus.COMPLETED, 8);
                map.put(AppointmentStatus.CANCELLED, 1);
                return map;
            }
        };

        DocumentDAO docDao = new DocumentDAO() {
            @Override public void save(DocumentMetadata entity) {}
            @Override public Optional<DocumentMetadata> findById(String id) { return Optional.empty(); }
            @Override public void update(DocumentMetadata entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<DocumentMetadata> findByObjectKey(String objectKey) { return Optional.empty(); }
            @Override public List<DocumentMetadata> findByPatientId(String patientId) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByMedicalRecordId(String medicalRecordId) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByCategory(DocumentCategory category) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findAll(int offset, int limit) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findRecent(int limit) { return Collections.emptyList(); }
            @Override public void archive(String id) {}
            @Override public int countTotal() { return 105; }
        };

        DashboardService dashboardService = new DashboardService(patDao, appDao, docDao);
        DashboardStats stats = dashboardService.getDashboardStatistics();

        assert stats.getActivePatientsCount() == 42 : "Expected 42 active patients";
        assert stats.getTotalDocumentsCount() == 105 : "Expected 105 total documents";
        assert stats.getTodayTotalAppointments() == 12 : "Expected 12 total appointments today (3+8+1)";
        assert stats.getTodayCompleted() == 8 : "Expected 8 completed appointments";

        System.out.println("PASSED ✓");
    }

    private static void testBackupAndSerialization() throws Exception {
        System.out.print("[Module 8/8] Java Serialization & Cryptographic Checksums... ");

        Patient p = new Patient();
        p.setId("CD-2026-0001");
        p.setFullName("John Doe");

        PatientDAO patDao = new PatientDAO() {
            @Override public void save(Patient entity) {}
            @Override public Optional<Patient> findById(String id) { return Optional.of(p); }
            @Override public void update(Patient entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<Patient> findByPatientNumber(String patientNumber) { return Optional.empty(); }
            @Override public List<Patient> findAll(int offset, int limit, boolean includeArchived) { return List.of(p); }
            @Override public List<Patient> search(String query, boolean includeArchived) { return List.of(p); }
            @Override public void archive(String id) {}
            @Override public void unarchive(String id) {}
            @Override public int countActive() { return 1; }
            @Override public int getNextSequenceNumber(int year) { return 2; }
        };

        AppointmentDAO appDao = new AppointmentDAO() {
            @Override public void save(Appointment entity) {}
            @Override public Optional<Appointment> findById(String id) { return Optional.empty(); }
            @Override public void update(Appointment entity) {}
            @Override public void delete(String id) {}
            @Override public List<Appointment> findByPatientId(String patientId) { return Collections.emptyList(); }
            @Override public List<Appointment> findByDoctorAndDateRange(String doctorId, LocalDateTime start, LocalDateTime end) { return Collections.emptyList(); }
            @Override public List<Appointment> findByDate(LocalDate date) { return Collections.emptyList(); }
            @Override public List<Appointment> findUpcoming(int limit) { return Collections.emptyList(); }
            @Override public boolean hasDoctorConflict(String doctorId, LocalDateTime start, LocalDateTime end, String excludeAppointmentId) { return false; }
            @Override public void updateStatus(String appointmentId, AppointmentStatus status) {}
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) { return Collections.emptyMap(); }
        };

        MedicalRecordDAO mrDao = new MedicalRecordDAO() {
            @Override public void save(MedicalRecord entity) {}
            @Override public Optional<MedicalRecord> findById(String id) { return Optional.empty(); }
            @Override public void update(MedicalRecord entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<MedicalRecord> findByAppointmentId(String appointmentId) { return Optional.empty(); }
            @Override public List<MedicalRecord> findByPatientId(String patientId) { return Collections.emptyList(); }
            @Override public List<MedicalRecord> findAll(int offset, int limit) { return Collections.emptyList(); }
            @Override public List<MedicalRecord> findRecent(int limit) { return Collections.emptyList(); }
            @Override public int countTotal() { return 0; }
        };

        DocumentDAO docDao = new DocumentDAO() {
            @Override public void save(DocumentMetadata entity) {}
            @Override public Optional<DocumentMetadata> findById(String id) { return Optional.empty(); }
            @Override public void update(DocumentMetadata entity) {}
            @Override public void delete(String id) {}
            @Override public Optional<DocumentMetadata> findByObjectKey(String objectKey) { return Optional.empty(); }
            @Override public List<DocumentMetadata> findByPatientId(String patientId) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByMedicalRecordId(String medicalRecordId) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByCategory(DocumentCategory category) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findAll(int offset, int limit) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findRecent(int limit) { return Collections.emptyList(); }
            @Override public void archive(String id) {}
            @Override public int countTotal() { return 0; }
        };

        BackupService backupService = new BackupService(patDao, appDao, mrDao, docDao);
        User admin = new User("USR-ADMIN-1", "admin@clinicdesk.com", "hash", "Admin", Role.ADMIN, true, Instant.now(), null);
        SessionContext.setCurrentUser(admin);

        File backupFile = File.createTempFile("system_backup_", ".cdbak");
        backupFile.deleteOnExit();

        BackupData exported = backupService.exportBackup(backupFile);
        assert backupFile.exists() && backupFile.length() > 0 : "Exported backup file missing or empty";
        assert exported.getChecksum() != null && exported.getChecksum().length() == 64 : "Invalid SHA-256 hash length";

        BackupData restored = backupService.importBackup(backupFile, false);
        assert restored.getPatients().size() == 1 : "Restored patient count mismatch";

        // Corrupt file to verify tamper detection
        try (RandomAccessFile raf = new RandomAccessFile(backupFile, "rw")) {
            raf.seek(backupFile.length() / 2);
            raf.write(0xFF);
        }

        boolean tamperDetected = false;
        try {
            backupService.importBackup(backupFile, false);
        } catch (Exception e) {
            tamperDetected = true;
        }
        assert tamperDetected : "Tampered backup file was not caught during deserialization/checksum check";

        System.out.println("PASSED ✓");
    }
}
