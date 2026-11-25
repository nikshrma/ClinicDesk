package com.clinicdesk.storage.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalDocumentStorageServiceTest {

    private LocalDocumentStorageService storageService;
    private Path tempStorageDir;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        this.tempStorageDir = tempDir.resolve("documents");
        this.storageService = new LocalDocumentStorageService(tempStorageDir);
    }

    @Test
    void testIsAvailable() {
        assertTrue(storageService.isAvailable(), "Local storage directory should be writable and available");
    }

    @Test
    void testUploadDownloadAndDeleteCycle(@TempDir Path tempSrcDir) throws Exception {
        File srcFile = tempSrcDir.resolve("test_prescription.jpg").toFile();
        try (FileWriter writer = new FileWriter(srcFile)) {
            writer.write("fake jpeg binary content for testing");
        }

        // Upload
        String key = storageService.upload(srcFile, "image/jpeg");
        assertNotNull(key);
        assertTrue(key.contains("test_prescription.jpg"));

        // Download
        try (InputStream in = storageService.download(key)) {
            byte[] bytes = in.readAllBytes();
            String content = new String(bytes);
            assertEquals("fake jpeg binary content for testing", content);
        }

        // Presigned / Local file URL
        String url = storageService.generatePresignedUrl(key, 3600);
        assertNotNull(url);
        assertTrue(url.startsWith("file:/"));

        // Delete
        storageService.delete(key);

        // Verify deleted
        assertThrows(Exception.class, () -> storageService.download(key));
    }
}
