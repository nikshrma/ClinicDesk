package com.clinicdesk.test;

import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.DocumentStorageException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.DocumentService;
import com.clinicdesk.service.PatientService;
import com.clinicdesk.storage.DocumentStorageService;

import java.io.*;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class Phase6VerificationRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Phase 6 Verification...");

        Map<String, DocumentMetadata> docStorage = new LinkedHashMap<>();
        Map<String, byte[]> minioStorage = new HashMap<>();
        final int[] compensationDeleteCount = {0};
        final boolean[] simulateDbError = {false};

        DocumentStorageService mockStorage = new DocumentStorageService() {
            @Override
            public String upload(File file, String contentType) {
                String key = "test-key-" + UUID.randomUUID() + "-" + file.getName();
                minioStorage.put(key, new byte[(int) file.length()]);
                return key;
            }
            @Override
            public InputStream download(String objectKey) {
                byte[] data = minioStorage.get(objectKey);
                return new ByteArrayInputStream(data != null ? data : new byte[0]);
            }
            @Override
            public void delete(String objectKey) {
                compensationDeleteCount[0]++;
                minioStorage.remove(objectKey);
            }
            @Override
            public String generatePresignedUrl(String objectKey, int expirySeconds) {
                return "http://localhost:9000/clinicdesk-documents/" + objectKey;
            }
            @Override
            public boolean isAvailable() { return true; }
        };

        DocumentDAO docDao = new DocumentDAO() {
            @Override
            public void save(DocumentMetadata entity) throws SQLException {
                if (simulateDbError[0]) {
                    throw new SQLException("Simulated database failure on insert");
                }
                docStorage.put(entity.getId(), entity);
            }
            @Override public Optional<DocumentMetadata> findById(String id) { return Optional.ofNullable(docStorage.get(id)); }
            @Override public Optional<DocumentMetadata> findByObjectKey(String k) { return docStorage.values().stream().filter(d -> d.getObjectKey().equals(k)).findFirst(); }
            @Override public List<DocumentMetadata> findByPatientId(String pid) { return docStorage.values().stream().filter(d -> d.getPatientId().equals(pid)).toList(); }
            @Override public List<DocumentMetadata> findByMedicalRecordId(String mrid) { return docStorage.values().stream().filter(d -> mrid.equals(d.getMedicalRecordId())).toList(); }
            @Override public List<DocumentMetadata> findByCategory(DocumentCategory cat) { return docStorage.values().stream().filter(d -> d.getCategory() == cat).toList(); }
            @Override public List<DocumentMetadata> findAll(int o, int l) { return new ArrayList<>(docStorage.values()); }
            @Override public List<DocumentMetadata> findRecent(int l) { return new ArrayList<>(docStorage.values()); }
            @Override public void archive(String id) { DocumentMetadata d = docStorage.get(id); if (d != null) d.setArchived(true); }
            @Override public void update(DocumentMetadata entity) { docStorage.put(entity.getId(), entity); }
            @Override public void delete(String id) { archive(id); }
            @Override public int countTotal() { return docStorage.size(); }
        };

        Map<String, Patient> patStorage = new HashMap<>();
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

        PatientService patService = new PatientService(patDao);
        DocumentService docService = new DocumentService(docDao, mockStorage, patService);

        // Seed doctor, receptionist, patient
        User doctor = new User("doc-1", "Dr. Sarah", "sarah@clinic.local", "hash", Role.DOCTOR, true);
        User receptionist = new User("rec-1", "Front Desk", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);
        Patient patient = new Patient("pat-1", "CD-2026-0001", "Clark Kent", LocalDate.of(1982, 6, 18), Gender.MALE, "+15551234567", null, null, null, "O+", null);
        patStorage.put(patient.getId(), patient);

        File tempFile = File.createTempFile("test_doc", ".pdf");
        tempFile.deleteOnExit();
        try (FileWriter fw = new FileWriter(tempFile)) { fw.write("Test PDF content"); }

        // 1. Receptionist rejected when uploading PRESCRIPTION
        SessionContext.setCurrentUser(receptionist);
        try {
            docService.uploadDocument(tempFile, patient.getId(), null, DocumentCategory.PRESCRIPTION, "rx.pdf", Instant.now());
            throw new AssertionError("Receptionist must not upload prescription");
        } catch (UnauthorizedAccessException e) {
            System.out.println("✓ Receptionist denied permission to upload PRESCRIPTION category.");
        }

        // 2. Doctor uploads prescription successfully
        SessionContext.setCurrentUser(doctor);
        DocumentMetadata uploaded = docService.uploadDocument(tempFile, patient.getId(), null, DocumentCategory.PRESCRIPTION, "rx.pdf", Instant.now());
        assert uploaded.getId() != null : "Uploaded doc ID is null";
        assert uploaded.getCategory() == DocumentCategory.PRESCRIPTION : "Category mismatch";
        assert minioStorage.containsKey(uploaded.getObjectKey()) : "Object must exist in MinIO storage";
        System.out.println("✓ Doctor successfully uploaded prescription: " + uploaded.getObjectKey());

        // 3. Compensation rollback on database failure
        File tempImage = File.createTempFile("test_scan", ".jpg");
        tempImage.deleteOnExit();
        try (FileWriter fw = new FileWriter(tempImage)) { fw.write("JPEG test content"); }

        simulateDbError[0] = true;
        try {
            docService.uploadDocument(tempImage, patient.getId(), null, DocumentCategory.LAB_REPORT, "scan.jpg", Instant.now());
            throw new AssertionError("Upload must fail when DB fails");
        } catch (DocumentStorageException e) {
            assert e.getMessage().contains("Storage upload rolled back") : "Exception should mention storage rollback";
            assert compensationDeleteCount[0] == 1 : "Storage compensation delete must be executed";
            System.out.println("✓ Compensation rollback executed: orphaned file deleted from storage upon DB error.");
        }

        // 4. File format validation
        File invalidFile = File.createTempFile("bad", ".exe");
        invalidFile.deleteOnExit();
        simulateDbError[0] = false;
        try {
            docService.uploadDocument(invalidFile, patient.getId(), null, DocumentCategory.OTHER, "bad.exe", Instant.now());
            throw new AssertionError("Invalid file extension must be rejected");
        } catch (IllegalArgumentException e) {
            System.out.println("✓ Unsupported file extension (.exe) rejected.");
        }

        System.out.println("\nALL PHASE 6 VERIFICATIONS PASSED SUCCESSFULLY!");
    }
}
