package com.clinicdesk.service;

import com.clinicdesk.dao.DocumentDAO;
import com.clinicdesk.dao.PatientDAO;
import com.clinicdesk.exception.DocumentStorageException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.*;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.storage.DocumentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DocumentServiceTest {

    private InMemoryDocumentDAO documentDAO;
    private MockDocumentStorageService storageService;
    private DocumentService documentService;
    private Patient testPatient;
    private User doctor;
    private User receptionist;

    @BeforeEach
    void setup() {
        SessionContext.clear();
        documentDAO = new InMemoryDocumentDAO();
        storageService = new MockDocumentStorageService();
        InMemoryPatientDAO patientDAO = new InMemoryPatientDAO();
        PatientService patientService = new PatientService(patientDAO);
        documentService = new DocumentService(documentDAO, storageService, patientService);

        doctor = new User("d1", "Dr. Sarah", "sarah@clinic.local", "hash", Role.DOCTOR, true);
        receptionist = new User("r1", "Reception Desk", "rec@clinic.local", "hash", Role.RECEPTIONIST, true);

        testPatient = new Patient("p1", "CD-2026-0001", "Emma Stone", LocalDate.of(1990, 1, 1),
                Gender.FEMALE, "+15551234567", null, null, null, "B+", null);
        patientDAO.storage.put(testPatient.getId(), testPatient);

        SessionContext.setCurrentUser(doctor);
    }

    @Test
    void testUploadDocumentSuccess(@TempDir Path tempDir) throws IOException {
        File dummyFile = tempDir.resolve("lab_results.pdf").toFile();
        try (FileWriter writer = new FileWriter(dummyFile)) {
            writer.write("%PDF-1.4 dummy content");
        }

        DocumentMetadata metadata = documentService.uploadDocument(
                dummyFile, testPatient.getId(), null, DocumentCategory.LAB_REPORT, "lab_results.pdf", Instant.now()
        );

        assertNotNull(metadata.getId());
        assertNotNull(metadata.getObjectKey());
        assertEquals("application/pdf", metadata.getMimeType());
        assertEquals(DocumentCategory.LAB_REPORT, metadata.getCategory());
        assertTrue(storageService.uploadedObjects.containsKey(metadata.getObjectKey()));
    }

    @Test
    void testPrescriptionUploadRequiresDoctorRole(@TempDir Path tempDir) throws IOException {
        File rxFile = tempDir.resolve("rx_photo.jpg").toFile();
        try (FileWriter writer = new FileWriter(rxFile)) {
            writer.write("dummy image bytes");
        }

        // Receptionist attempting to upload prescription must fail
        SessionContext.setCurrentUser(receptionist);
        assertThrows(UnauthorizedAccessException.class, () ->
                documentService.uploadDocument(rxFile, testPatient.getId(), null, DocumentCategory.PRESCRIPTION, "rx_photo.jpg", Instant.now())
        );

        // Doctor allowed
        SessionContext.setCurrentUser(doctor);
        DocumentMetadata meta = documentService.uploadDocument(
                rxFile, testPatient.getId(), null, DocumentCategory.PRESCRIPTION, "rx_photo.jpg", Instant.now()
        );
        assertNotNull(meta);
        assertEquals(DocumentCategory.PRESCRIPTION, meta.getCategory());
    }

    @Test
    void testUnsupportedExtensionRejected(@TempDir Path tempDir) throws IOException {
        File badFile = tempDir.resolve("script.sh").toFile();
        try (FileWriter writer = new FileWriter(badFile)) {
            writer.write("echo hello");
        }

        assertThrows(IllegalArgumentException.class, () ->
                documentService.uploadDocument(badFile, testPatient.getId(), null, DocumentCategory.OTHER, "script.sh", Instant.now())
        );
    }

    @Test
    void testAtomicCompensationRollbackOnDatabaseFailure(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("scan.png").toFile();
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("png dummy data");
        }

        // Configure DAO to fail on save
        documentDAO.simulateDbFailure = true;

        DocumentStorageException ex = assertThrows(DocumentStorageException.class, () ->
                documentService.uploadDocument(testFile, testPatient.getId(), null, DocumentCategory.MEDICAL_IMAGE, "scan.png", Instant.now())
        );

        assertTrue(ex.getMessage().contains("Storage upload rolled back"));

        // Verify compensation: MinIO object was deleted immediately
        assertEquals(1, storageService.deleteCallCount, "Storage compensation delete must be executed on DB failure");
        assertTrue(storageService.uploadedObjects.isEmpty(), "No orphaned file must remain in storage");
    }

    // In-memory test stubs
    private static class MockDocumentStorageService implements DocumentStorageService {
        final Map<String, byte[]> uploadedObjects = new HashMap<>();
        int deleteCallCount = 0;

        @Override
        public String upload(File file, String contentType) {
            String key = UUID.randomUUID() + "-" + file.getName();
            uploadedObjects.put(key, new byte[(int) file.length()]);
            return key;
        }

        @Override
        public InputStream download(String objectKey) {
            byte[] data = uploadedObjects.get(objectKey);
            return new ByteArrayInputStream(data != null ? data : new byte[0]);
        }

        @Override
        public void delete(String objectKey) {
            deleteCallCount++;
            uploadedObjects.remove(objectKey);
        }

        @Override
        public String generatePresignedUrl(String objectKey, int expirySeconds) {
            return "https://minio.local/clinicdesk-documents/" + objectKey + "?token=mock";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    private static class InMemoryDocumentDAO implements DocumentDAO {
        final Map<String, DocumentMetadata> storage = new LinkedHashMap<>();
        boolean simulateDbFailure = false;

        @Override
        public void save(DocumentMetadata entity) throws SQLException {
            if (simulateDbFailure) {
                throw new SQLException("Simulated database constraint violation");
            }
            storage.put(entity.getId(), entity);
        }

        @Override public Optional<DocumentMetadata> findById(String id) { return Optional.ofNullable(storage.get(id)); }
        @Override public Optional<DocumentMetadata> findByObjectKey(String k) { return storage.values().stream().filter(d -> d.getObjectKey().equals(k)).findFirst(); }
        @Override public List<DocumentMetadata> findByPatientId(String pid) { return storage.values().stream().filter(d -> d.getPatientId().equals(pid)).toList(); }
        @Override public List<DocumentMetadata> findByMedicalRecordId(String mrid) { return storage.values().stream().filter(d -> mrid.equals(d.getMedicalRecordId())).toList(); }
        @Override public List<DocumentMetadata> findByCategory(DocumentCategory cat) { return storage.values().stream().filter(d -> d.getCategory() == cat).toList(); }
        @Override public List<DocumentMetadata> findAll(int o, int l) { return new ArrayList<>(storage.values()); }
        @Override public List<DocumentMetadata> findRecent(int l) { return new ArrayList<>(storage.values()); }
        @Override public void archive(String id) { DocumentMetadata d = storage.get(id); if (d != null) d.setArchived(true); }
        @Override public void update(DocumentMetadata entity) { storage.put(entity.getId(), entity); }
        @Override public void delete(String id) { archive(id); }
        @Override public int countTotal() { return storage.size(); }
    }

    private static class InMemoryPatientDAO implements PatientDAO {
        final Map<String, Patient> storage = new HashMap<>();
        @Override public void save(Patient entity) { storage.put(entity.getId(), entity); }
        @Override public Optional<Patient> findById(String id) { return Optional.ofNullable(storage.get(id)); }
        @Override public Optional<Patient> findByPatientNumber(String n) { return Optional.empty(); }
        @Override public List<Patient> findAll(int o, int l, boolean i) { return new ArrayList<>(storage.values()); }
        @Override public List<Patient> search(String q, boolean i) { return List.of(); }
        @Override public void update(Patient entity) { storage.put(entity.getId(), entity); }
        @Override public void archive(String id) { }
        @Override public void unarchive(String id) { }
        @Override public void delete(String id) { }
        @Override public int countActive() { return storage.size(); }
        @Override public int getNextSequenceNumber(int y) { return storage.size() + 1; }
    }
}
