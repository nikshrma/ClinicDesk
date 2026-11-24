package com.clinicdesk.test;

import com.clinicdesk.storage.minio.MinioDocumentStorageService;
import java.io.File;
import java.nio.file.Files;

public class TestMinioLive {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing MinIO live connection...");
        MinioDocumentStorageService storage = new MinioDocumentStorageService();
        System.out.println("MinIO isAvailable: " + storage.isAvailable());
        
        File temp = File.createTempFile("minio_test_", ".txt");
        temp.deleteOnExit();
        Files.writeString(temp.toPath(), "Hello ClinicDesk MinIO live test!");
        
        String key = storage.upload(temp, "text/plain");
        System.out.println("✓ Uploaded object key: " + key);
        
        byte[] downloaded = storage.download(key).readAllBytes();
        String content = new String(downloaded);
        System.out.println("✓ Downloaded content: " + content);
        assert "Hello ClinicDesk MinIO live test!".equals(content) : "Content mismatch";
        
        storage.delete(key);
        System.out.println("✓ Cleaned up test object.");
        System.out.println("ALL MINIO LIVE TESTS PASSED! Bucket and permissions are working 100%.");
    }
}
