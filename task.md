# ClinicDesk — Handover & Next Tasks (Windows Developer Guide)

Welcome to **ClinicDesk**! This document outlines the current project status, credentials, and known issues for continuing development on a Windows workstation.

---

## 📌 Project Overview
ClinicDesk is a desktop-based Clinic & Patient Records Management System built with:
- **Core Java 21 LTS** + **JavaFX 21**
- **Raw JDBC** (BaseDAO pattern + HikariCP) — **Strictly Zero-Framework** (No Spring, Hibernate, or JPA)
- **MySQL 8.0+** (InnoDB, transactional integrity, sequential `CD-YYYY-XXXX` IDs)
- **MinIO Object Storage** (Private bucket document storage)
- **JavaCV / OpenCV 4.9** (Real-time laptop webcam streaming & image processing)
- **Java Object Serialization** (`.cdbak` with SHA-256 tamper verification)

All core modules (Authentication, Patients, Scheduling, Medical Records, Camera Processing, Aggregation Dashboard, Serialization Backup) are complete, with 100% tests passing in `ComprehensiveSystemVerificationRunner`.

---

## 🔑 Pre-Seeded Accounts
Default login credentials for local testing (initialized in `infrastructure/mysql/init/02-seed.sql`):

| Role | Email | Password | Permissions |
| :--- | :--- | :--- | :--- |
| **System Admin** | `admin@clinicdesk.local` | `password123` | Full system access, Staff Management, Settings & `.cdbak` Backup/Restore |
| **Doctor** | `dr.smith@clinicdesk.local` | `password123` | Longitudinal Patient History, Chief Complaint + Diagnosis Records, Prescription Camera Capture |
| **Receptionist** | `reception@clinicdesk.local` | `password123` | Patient Directory (Registration/Search), Appointment Scheduling & Check-In |

---

## ⚠️ Known Issues to Resolve

### Issue 1: Camera Real-Time Video Feed (OpenCV / JavaCV)
- **Symptom:** Opening the "Capture Prescription" camera dialog on macOS currently defaults to the fallback prescription graphic (`Patient: [Camera Device In Use / Fallback]`) instead of showing the real-time webcam video feed.
- **Root Cause on macOS:**
  - macOS AVFoundation requires system permission (TCC). When OpenCV calls `requestAccess()`, it happens asynchronously (`status 0`).
  - `OpenCVFrameGrabber.start()` immediately called `read()` in the exact same millisecond before authorization settled, throwing `Could not read frame in start()` and triggering the fallback pattern generator in `OpenCVCameraService.java`.
- **Next Task on Windows:**
  1. Windows uses DirectShow (`DSHOW`) or Media Foundation (`MSMF`), which does not suffer from macOS's asynchronous TCC gate.
  2. In `com.clinicdesk.camera.OpenCVCameraService`:
     - Add a retry/warm-up loop when calling `grabber.start()` (e.g. 3 attempts with 500ms sleep) before falling back.
     - On Windows, verify `grabber = new OpenCVFrameGrabber(0)` opens the integrated webcam. If multiple webcams exist, add a device selector dropdown in `CameraCaptureDialog.fxml`.
     - Test live video streaming (30 FPS) and ensure `grabber.grab()` returns real frames to `Java2DFrameConverter`.

---

### Issue 2: MinIO Storage vs. Local Disk Storage Fallback
- **Symptom:** Confirming and saving a captured photo throws:
  `Failed to verify or create MinIO bucket: clinicdesk-documents`
- **Root Cause:**
  - `docker compose up -d` on macOS encountered a Docker Hub authentication/rate-limit error when pulling `minio/minio`.
  - While MySQL 8 is running and healthy on port 3306, MinIO was not running on port 9000.
- **Next Task on Windows:**
  1. **Option A (Run MinIO in Docker):**
     - Run `docker compose up -d` on Windows. If `minio/minio:latest` fails to pull on your machine, pull `quay.io/minio/minio` or use a local MinIO binary (`minio.exe server C:\minio-data`).
  2. **Option B (Local Filesystem Fallback - Recommended):**
     - Update `DocumentService.java` / `DocumentStorageService.java` to gracefully fall back to local disk storage (`./storage/documents/`) when MinIO (`localhost:9000`) is offline or unreachable.
     - This ensures document and camera prescription saving works out-of-the-box even without Docker!

---

## 🚀 How to Run on Windows

### 1. Prerequisites
- **JDK 21:** Install OpenJDK 21 or Eclipse Temurin 21. Set `JAVA_HOME` and add `%JAVA_HOME%\bin` to your `PATH`.
- **Maven:** Maven 3.8+ (Verify with `mvn -version`).
- **Database:** Start MySQL via Docker:
  ```powershell
  docker compose up -d
  ```
  *(Or run local MySQL on port 3306 with database `clinicdesk`, user `clinicdesk`, password `clinicdesk123`)*.

### 2. Launch ClinicDesk
```powershell
mvn clean javafx:run
```

### 3. Run System Test Suite
```powershell
mvn test
```
Or execute the master runner directly:
```powershell
java -ea -cp "target/classes;target/test-classes;..." com.clinicdesk.test.ComprehensiveSystemVerificationRunner
```
