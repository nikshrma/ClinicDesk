package com.clinicdesk.test;

import com.clinicdesk.backup.BackupData;
import com.clinicdesk.backup.BackupService;
import com.clinicdesk.dao.AppointmentDAO;
import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.dao.MedicalRecordDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.DashboardService;

import java.io.*;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class Phase8VerificationRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Phase 8 Verification...");

        // Setup test in-memory mock datasets
        Map<String, Patient> patientMap = new LinkedHashMap<>();
        Map<String, Appointment> appointmentMap = new LinkedHashMap<>();
        Map<String, MedicalRecord> medicalRecordMap = new LinkedHashMap<>();
        Map<String, DocumentMetadata> documentMap = new LinkedHashMap<>();

        // Seed sample data
        for (int i = 1; i <= 5; i++) {
            Patient p = new Patient();
            p.setId("CD-2026-000" + i);
            p.setPatientNumber("CD-2026-000" + i);
            p.setFullName("Test Patient " + i);
            p.setPhone("+1555000000" + i);
            p.setDateOfBirth(LocalDate.of(1990, 1, i));
            p.setGender(Gender.MALE);
            p.setArchived(i == 5); // 4 active, 1 archived
            p.setCreatedAt(Instant.now());
            patientMap.put(p.getId(), p);
        }

        LocalDate today = LocalDate.now();
        Appointment a1 = new Appointment("APT-1", "CD-2026-0001", "Dr. House", LocalDateTime.now().withHour(9), 30, "Checkup", AppointmentStatus.CHECKED_IN);
        Appointment a2 = new Appointment("APT-2", "CD-2026-0002", "Dr. House", LocalDateTime.now().withHour(10), 30, "Flu", AppointmentStatus.COMPLETED);
        Appointment a3 = new Appointment("APT-3", "CD-2026-0003", "Dr. House", LocalDateTime.now().withHour(11), 30, "Followup", AppointmentStatus.CANCELLED);
        appointmentMap.put(a1.getId(), a1);
        appointmentMap.put(a2.getId(), a2);
        appointmentMap.put(a3.getId(), a3);

        DocumentMetadata d1 = new DocumentMetadata();
        d1.setId("DOC-1");
        d1.setPatientId("CD-2026-0001");
        d1.setCategory(DocumentCategory.PRESCRIPTION);
        d1.setOriginalFilename("rx1.jpg");
        d1.setObjectKey("keys/rx1.jpg");
        d1.setFileSize(2048);
        d1.setMimeType("image/jpeg");
        d1.setCreatedAt(Instant.now());
        documentMap.put(d1.getId(), d1);

        MedicalRecord mr1 = new MedicalRecord();
        mr1.setId("MR-1");
        mr1.setPatientId("CD-2026-0001");
        mr1.setAppointmentId(a2.getId());
        mr1.setChiefComplaint("Cough");
        mr1.setDiagnosis("Bronchitis");
        mr1.setDoctorId("USR-DOC-1");
        mr1.setCreatedAt(Instant.now());
        medicalRecordMap.put(mr1.getId(), mr1);

        // Mock DAOs
        PatientDAO mockPatientDAO = new PatientDAO() {
            @Override public void save(Patient entity) { patientMap.put(entity.getId(), entity); }
            @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(patientMap.get(id)); }
            @Override public void update(Patient entity) { patientMap.put(entity.getId(), entity); }
            @Override public void delete(String id) { patientMap.remove(id); }
            @Override public Optional<Patient> findByPatientNumber(String num) { return patientMap.values().stream().filter(p -> p.getPatientNumber().equals(num)).findFirst(); }
            @Override public List<Patient> findAll(int offset, int limit, boolean includeArchived) {
                return patientMap.values().stream().filter(p -> includeArchived || !p.isArchived()).toList();
            }
            @Override public List<Patient> search(String query, boolean includeArchived) { return new ArrayList<>(patientMap.values()); }
            @Override public void archive(String id) { Patient p = patientMap.get(id); if (p != null) p.setArchived(true); }
            @Override public void unarchive(String id) { Patient p = patientMap.get(id); if (p != null) p.setArchived(false); }
            @Override public int countActive() { return (int) patientMap.values().stream().filter(p -> !p.isArchived()).count(); }
            @Override public int getNextSequenceNumber(int year) { return patientMap.size() + 1; }
        };

        AppointmentDAO mockAppointmentDAO = new AppointmentDAO() {
            @Override public void save(Appointment entity) { appointmentMap.put(entity.getId(), entity); }
            @Override public Optional<Appointment> findById(String id) { return Optional.ofNullable(appointmentMap.get(id)); }
            @Override public void update(Appointment entity) { appointmentMap.put(entity.getId(), entity); }
            @Override public void delete(String id) { appointmentMap.remove(id); }
            @Override public List<Appointment> findByPatientId(String pid) { return appointmentMap.values().stream().filter(a -> a.getPatientId().equals(pid)).toList(); }
            @Override public List<Appointment> findByDoctorAndDateRange(String doc, LocalDateTime start, LocalDateTime end) { return new ArrayList<>(appointmentMap.values()); }
            @Override public List<Appointment> findByDate(LocalDate date) { return new ArrayList<>(appointmentMap.values()); }
            @Override public List<Appointment> findUpcoming(int limit) { return new ArrayList<>(appointmentMap.values()); }
            @Override public boolean hasDoctorConflict(String doc, LocalDateTime start, LocalDateTime end, String excludeId) { return false; }
            @Override public void updateStatus(String id, AppointmentStatus status) { Appointment a = appointmentMap.get(id); if (a != null) a.setStatus(status); }
            @Override public Map<AppointmentStatus, Integer> getStatusCountsForDate(LocalDate date) {
                Map<AppointmentStatus, Integer> counts = new EnumMap<>(AppointmentStatus.class);
                for (Appointment a : appointmentMap.values()) {
                    counts.put(a.getStatus(), counts.getOrDefault(a.getStatus(), 0) + 1);
                }
                return counts;
            }
        };

        DocumentDAO mockDocumentDAO = new DocumentDAO() {
            @Override public void save(DocumentMetadata entity) { documentMap.put(entity.getId(), entity); }
            @Override public Optional<DocumentMetadata> findById(String id) { return Optional.ofNullable(documentMap.get(id)); }
            @Override public void update(DocumentMetadata entity) { documentMap.put(entity.getId(), entity); }
            @Override public void delete(String id) { documentMap.remove(id); }
            @Override public Optional<DocumentMetadata> findByObjectKey(String k) { return documentMap.values().stream().filter(d -> d.getObjectKey().equals(k)).findFirst(); }
            @Override public List<DocumentMetadata> findByPatientId(String pid) { return documentMap.values().stream().filter(d -> d.getPatientId().equals(pid)).toList(); }
            @Override public List<DocumentMetadata> findByMedicalRecordId(String mrid) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findByCategory(DocumentCategory cat) { return Collections.emptyList(); }
            @Override public List<DocumentMetadata> findAll(int offset, int limit) { return new ArrayList<>(documentMap.values()); }
            @Override public List<DocumentMetadata> findRecent(int limit) { return new ArrayList<>(documentMap.values()); }
            @Override public void archive(String id) { DocumentMetadata d = documentMap.get(id); if (d != null) d.setArchived(true); }
            @Override public int countTotal() { return documentMap.size(); }
        };

        MedicalRecordDAO mockMedicalRecordDAO = new MedicalRecordDAO() {
            @Override public void save(MedicalRecord entity) { medicalRecordMap.put(entity.getId(), entity); }
            @Override public Optional<MedicalRecord> findById(String id) { return Optional.ofNullable(medicalRecordMap.get(id)); }
            @Override public void update(MedicalRecord entity) { medicalRecordMap.put(entity.getId(), entity); }
            @Override public void delete(String id) { medicalRecordMap.remove(id); }
            @Override public Optional<MedicalRecord> findByAppointmentId(String aid) { return medicalRecordMap.values().stream().filter(m -> m.getAppointmentId().equals(aid)).findFirst(); }
            @Override public List<MedicalRecord> findByPatientId(String pid) { return medicalRecordMap.values().stream().filter(m -> m.getPatientId().equals(pid)).toList(); }
            @Override public List<MedicalRecord> findAll(int offset, int limit) { return new ArrayList<>(medicalRecordMap.values()); }
            @Override public List<MedicalRecord> findRecent(int limit) { return new ArrayList<>(medicalRecordMap.values()); }
            @Override public int countTotal() { return medicalRecordMap.size(); }
        };

        // TEST 1: DashboardService Aggregate Gathering
        System.out.println("--- Test 1: DashboardService Aggregation ---");
        DashboardService dashboardService = new DashboardService(mockPatientDAO, mockAppointmentDAO, mockDocumentDAO);
        DashboardStats stats = dashboardService.getDashboardStatistics();

        assert stats.getActivePatientsCount() == 4 : "Expected 4 active patients, got: " + stats.getActivePatientsCount();
        assert stats.getTotalDocumentsCount() == 1 : "Expected 1 document, got: " + stats.getTotalDocumentsCount();
        assert stats.getTodayTotalAppointments() == 3 : "Expected 3 total appointments today, got: " + stats.getTodayTotalAppointments();
        assert stats.getTodayCheckedIn() == 1 : "Expected 1 checked in appointment, got: " + stats.getTodayCheckedIn();
        assert stats.getTodayCompleted() == 1 : "Expected 1 completed appointment, got: " + stats.getTodayCompleted();
        assert stats.getTodayCancelled() == 1 : "Expected 1 cancelled appointment, got: " + stats.getTodayCancelled();
        assert stats.getTodayAppointments().size() == 3 : "Expected 3 appointments in list, got: " + stats.getTodayAppointments().size();
        assert stats.getRecentPatients().size() == 4 : "Expected 4 active recent patients, got: " + stats.getRecentPatients().size();
        assert stats.getRecentDocuments().size() == 1 : "Expected 1 recent document, got: " + stats.getRecentDocuments().size();
        System.out.println("✓ Test 1 Passed: DashboardService gathered and aggregated clinic metrics accurately.");

        // TEST 2: RBAC Protection on BackupService
        System.out.println("--- Test 2: BackupService RBAC Protection ---");
        BackupService backupService = new BackupService(mockPatientDAO, mockAppointmentDAO, mockMedicalRecordDAO, mockDocumentDAO);
        User doctorUser = new User("USR-2", "doctor@clinicdesk.com", "hash", "Dr. House", Role.DOCTOR, true, Instant.now(), null);
        SessionContext.setCurrentUser(doctorUser);

        File tempBackup = File.createTempFile("clinicdesk_test_", ".cdbak");
        tempBackup.deleteOnExit();

        boolean rbacBlocked = false;
        try {
            backupService.exportBackup(tempBackup);
        } catch (UnauthorizedAccessException e) {
            rbacBlocked = true;
        }
        assert rbacBlocked : "Expected exportBackup to be blocked for DOCTOR role";

        rbacBlocked = false;
        try {
            backupService.importBackup(tempBackup, false);
        } catch (UnauthorizedAccessException e) {
            rbacBlocked = true;
        }
        assert rbacBlocked : "Expected importBackup to be blocked for DOCTOR role";
        System.out.println("✓ Test 2 Passed: Non-admin users are strictly blocked from export/import.");

        // TEST 3: Admin Backup Export with Checksum
        System.out.println("--- Test 3: Admin Backup Export ---");
        User adminUser = new User("USR-1", "admin@clinicdesk.com", "hash", "Admin User", Role.ADMIN, true, Instant.now(), null);
        SessionContext.setCurrentUser(adminUser);

        BackupData exportedData = backupService.exportBackup(tempBackup);
        assert tempBackup.exists() && tempBackup.length() > 0 : "Backup file was not created or is empty";
        assert exportedData.getChecksum() != null && !exportedData.getChecksum().isEmpty() : "Exported data missing SHA-256 checksum";
        assert exportedData.getPatients().size() == 5 : "Expected 5 exported patients, got: " + exportedData.getPatients().size();
        assert exportedData.getMedicalRecords().size() == 1 : "Expected 1 exported medical record, got: " + exportedData.getMedicalRecords().size();
        System.out.println("✓ Test 3 Passed: Serialized .cdbak export succeeded with SHA-256 checksum: " + exportedData.getChecksum());

        // TEST 4: Backup Deserialization and Integrity Verification
        System.out.println("--- Test 4: Deserialization & Integrity Verification ---");
        BackupData importedData = backupService.importBackup(tempBackup, false);
        assert importedData != null : "Imported data is null";
        assert importedData.getChecksum().equals(exportedData.getChecksum()) : "Imported checksum does not match exported checksum";
        assert importedData.getPatients().size() == 5 : "Expected 5 restored patients";
        assert importedData.getMedicalRecords().size() == 1 : "Expected 1 restored medical record";
        System.out.println("✓ Test 4 Passed: Backup file deserialized and verified successfully.");

        // TEST 5: Checksum Tamper Detection
        System.out.println("--- Test 5: Checksum Tamper Detection ---");
        BackupData tamperedData;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(tempBackup))) {
            tamperedData = (BackupData) ois.readObject();
        }
        tamperedData.setChecksum("0000000000000000000000000000000000000000000000000000000000000000"); // Corrupt checksum
        File tamperedFile = File.createTempFile("clinicdesk_tampered_", ".cdbak");
        tamperedFile.deleteOnExit();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tamperedFile))) {
            oos.writeObject(tamperedData);
        }

        boolean tamperCaught = false;
        try {
            backupService.importBackup(tamperedFile, false);
        } catch (ClinicDeskException e) {
            tamperCaught = true;
            assert e.getMessage().contains("checksum mismatch") : "Unexpected message: " + e.getMessage();
        }
        assert tamperCaught : "Expected corrupted/tampered backup file to be rejected by BackupService";
        System.out.println("✓ Test 5 Passed: Tampered backup file was detected and rejected.");

        System.out.println("\nALL PHASE 8 TESTS PASSED SUCCESSFULLY!");
    }
}
