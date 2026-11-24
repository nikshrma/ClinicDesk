# ClinicDesk — AI Agent & Windows Developer Onboarding Guide

> **Directive for AI Agents & Developers:**
> You are picking up development of **ClinicDesk** on a Windows workstation. Read this entire document before executing commands or modifying code.
> **DO NOT modify any architecture rules or add third-party frameworks.**

---

## 1. 🏛️ Non-Negotiable Architectural Contracts

1. **Strict Zero-Framework Mandate:**
   - **DO NOT** add Spring, Spring Boot, Hibernate, JPA, Micronaut, Quarkus, or REST API middleware.
   - The project is strictly **Core Java 21 LTS**, **JavaFX 21**, and **Raw JDBC** (`java.sql.PreparedStatement`) via the `BaseDAO<T, ID>` pattern with HikariCP.
2. **Clinical Workflow Invariant (No Manual Prescription Text):**
   - In `MedicalRecord`, only **Chief Complaint** and **Diagnosis** are captured as text.
   - Prescriptions and clinical notes are **never** entered via manual text areas. Prescriptions are captured as physical artifacts via the laptop webcam or uploaded as PDF/image documents.
3. **JavaFX Concurrency Model:**
   - **Never** perform database queries, camera grabber I/O, or file storage operations on the JavaFX Application Thread.
   - Use background daemon executors or `javafx.concurrent.Task`, and update UI controls strictly via `Platform.runLater()`.

---

## 2. 🔑 Seed Credentials & Database Connectivity

- **Database:** MySQL 8.0+ running on `localhost:3306` (Database: `clinicdesk`, User: `clinicdesk`, Password: `clinicdesk123`).
- **Configuration File:** `src/main/resources/config/application.properties`
- **Pre-Seeded Login Accounts:**

| Role | Email | Password | Permissions |
| :--- | :--- | :--- | :--- |
| **System Admin** | `admin@clinicdesk.local` | `password123` | Full system access, Staff Management, Settings & `.cdbak` Backup/Restore |
| **Doctor** | `dr.smith@clinicdesk.local` | `password123` | Patient Charts, Medical Records (Diagnosis only), Prescription Camera Capture |
| **Receptionist** | `reception@clinicdesk.local` | `password123` | Patient Directory (Registration/Search), Appointment Scheduling & Check-In |

*(Note: `PasswordUtil.java` contains a development bypass allowing `password123`, `Admin123!`, `Doctor123!`, or `Reception123!`)*.

---

## 3. 🎯 Next Tasks to Complete

### Task 1: Fix Real-Time Camera Preview (Eliminate Synthetic Fallback)

#### Root Cause of the Fallback Graphic:
As seen in the screenshot, the camera dialog displays:
`Patient: [Camera Device In Use / Fallback]` with the synthetic prescription template.
This occurs inside `OpenCVCameraService.java`:
1. **Premature Permanent Fallback:** If `grabber.grab()` returns `null` or throws on the first frame (common while webcam hardware initializes auto-exposure), line 100 permanently sets `hardwareActive = false;`, locking the entire session into the synthetic fallback loop.
2. **Hardcoded Resolution Failure:** Setting `grabber.setImageWidth(1280)` and `grabber.setImageHeight(720)` before `grabber.start()` causes certain built-in laptop webcams to fail initialization if they default to 640x480 or don't support 720p negotiation.
3. **Device Index Lock:** Hardcoded `deviceIndex = 0` can point to inactive virtual cameras, IR sensors, or disconnected secondary capture devices.

#### Key Files:
- `src/main/java/com/clinicdesk/camera/OpenCVCameraService.java`
- `src/main/java/com/clinicdesk/camera/ImageProcessingService.java`
- `src/main/java/com/clinicdesk/ui/controller/CameraCaptureController.java`
- `src/main/resources/fxml/CameraCaptureDialog.fxml`

#### Actionable Implementation Steps:
1. **Dynamic Resolution & Graceful Startup:**
   - In `OpenCVCameraService.startLiveStream()`:
     - Initialize `grabber = new OpenCVFrameGrabber(deviceIndex);`
     - Do not hardcode width/height before `start()`. Call `grabber.start();` first, then inspect `grabber.getImageWidth()` and `grabber.getImageHeight()`.
2. **Sensor Warm-Up Retry Loop:**
   - After `grabber.start()`, run a warm-up loop (up to 10 frames with 50ms pause) to discard blank/black initialization frames:
     ```java
     boolean warmUpSuccess = false;
     for (int i = 0; i < 10; i++) {
         try {
             Frame f = grabber.grab();
             if (f != null && f.image != null) {
                 warmUpSuccess = true;
                 break;
             }
         } catch (Exception ignored) {}
         Thread.sleep(50);
     }
     ```
3. **Resilient Streaming Loop:**
   - In the scheduled streaming executor, do **not** permanently disable `hardwareActive = false` on a single transient frame drop.
   - Maintain a consecutive failure counter (`consecutiveFailures`). Only fall back to `generateNextFrame()` if failures exceed a threshold (e.g. > 15 consecutive drops / ~500ms).
4. **Device Fallback & Switcher:**
   - If device index 0 fails to start, attempt fallback to device index 1 before declaring the camera unavailable.
5. **Verify on Windows:**
   - Verify that DirectShow (`DSHOW`) activates the webcam LED.
   - Verify live 30 FPS video feed renders smoothly in `CameraCaptureDialog.fxml`.
   - Verify freeze on "Capture Photograph", image rotation (90°/180°/270°), and horizontal/vertical flips.

---

### Task 2: Implement Offline Local Document Storage Fallback

#### Working MinIO Image Reference:
The verified working Docker image is **`abhishekbatra123/minio:latest`**:
```bash
docker run -d --name clinicdesk-minio -p 9000:9000 -p 9001:9001 -e MINIO_ROOT_USER=clinicadmin -e MINIO_ROOT_PASSWORD=clinicpass123 abhishekbatra123/minio:latest server /data --console-address ":9001"
```

#### Goal:
ClinicDesk must remain 100% operational offline on a standalone Windows laptop without requiring Docker or an active MinIO daemon.

#### Key Files:
- `src/main/java/com/clinicdesk/storage/DocumentStorageService.java`
- `src/main/java/com/clinicdesk/storage/local/LocalDocumentStorageService.java` *(to create)*
- `src/main/java/com/clinicdesk/storage/minio/MinioDocumentStorageService.java`
- `src/main/java/com/clinicdesk/service/DocumentService.java`
- `src/main/java/com/clinicdesk/config/StorageConfig.java`

#### Actionable Implementation Steps:
1. Create `LocalDocumentStorageService implements DocumentStorageService`:
   - Store documents under: `System.getProperty("user.home") + File.separator + ".clinicdesk" + File.separator + "documents"`
   - Ensure the directory is created automatically on startup.
   - `upload(File, contentType)`: Copies file with unique key (`UUID + "_" + file.getName()`) into local storage.
   - `download(objectKey)`: Returns `FileInputStream` for the local file.
   - `delete(objectKey)`: Deletes local file.
   - `generatePresignedUrl(objectKey, expiry)`: Returns local file URI (`file.toURI().toString()`).
   - `isAvailable()`: Returns `true` if storage directory exists and is writable.
2. Update `StorageConfig` / `DocumentService`:
   - Check `MinioDocumentStorageService.isAvailable()`.
   - If MinIO is reachable, use MinIO.
   - If MinIO is down or unreachable, seamlessly fall back to `LocalDocumentStorageService` with a log notice:
     `"MinIO unavailable. Storing document locally at: <path>"`
3. Verify prescription capture saves cleanly to disk and database even when MinIO is killed.

---

### Task 3: Doctor Consultation & Prescription Linkage Workflow

#### Goal:
Ensure the end-to-end clinical workflow operates smoothly between the Medical Records view and the Camera Capture dialog.

#### Key Files:
- `src/main/java/com/clinicdesk/ui/controller/MedicalRecordsController.java`
- `src/main/java/com/clinicdesk/ui/controller/CameraCaptureController.java`
- `src/main/java/com/clinicdesk/service/MedicalRecordService.java`
- `src/main/java/com/clinicdesk/service/DocumentService.java`

#### Actionable Implementation Steps:
1. When a Doctor clicks **"Capture Prescription"** from a Patient's Medical Record:
   - Pass the `patientId` and optional `medicalRecordId` to `CameraCaptureController`.
   - After the photo is captured and saved via `DocumentService`, automatically link the generated `document_id` to the consultation record.
2. Update the Patient Document list and Medical Record timeline immediately via `Platform.runLater()` without requiring a manual screen refresh.
3. Verify that the saved prescription can be viewed full-screen directly from the Medical Record card.

---

### Task 4: Windows Standalone Launcher & Environment Verification

#### Goal:
Enable any Windows developer to run ClinicDesk with a single click.

#### Actionable Implementation Steps:
1. Create `run-clinicdesk.bat` in the repository root:
   ```cmd
   @echo off
   echo ========================================================
   echo   ClinicDesk - Clinic & Patient Records Management
   echo ========================================================
   where mvn >nul 2>nul
   if %errorlevel% neq 0 (
       echo [ERROR] Maven not found in PATH. Please install Maven or add it to PATH.
       pause
       exit /b 1
   )
   echo Starting ClinicDesk...
   mvn clean javafx:run
   pause
   ```
2. Verify MySQL connectivity fallback or auto-detection.

---

## 4. 🗂️ Key Source Tree Reference

```text
src/
├── main/
│   ├── java/com/clinicdesk/
│   │   ├── Main.java                          # JavaFX Desktop Application entrypoint
│   │   ├── backup/
│   │   │   ├── BackupData.java                # Serializable backup envelope with SHA-256
│   │   │   └── BackupService.java             # ObjectOutputStream export & JDBC restore
│   │   ├── camera/
│   │   │   ├── CameraService.java             # Interface for camera capture
│   │   │   ├── OpenCVCameraService.java       # JavaCV OpenCV camera grabber + fallback
│   │   │   └── ImageProcessingService.java    # Pure Java AWT 90/180/270 rotate & flip
│   │   ├── config/
│   │   │   ├── AppConfig.java                 # Config property resolver
│   │   │   ├── DatabaseConfig.java            # HikariCP connection pool
│   │   │   └── StorageConfig.java             # MinIO client initialization
│   │   ├── dao/                               # BaseDAO and entity DAOs
│   │   │   └── mysql/                         # Raw JDBC PreparedStatements
│   │   ├── model/                             # Domain POJOs (Patient, Appointment, etc.)
│   │   ├── security/
│   │   │   └── SessionContext.java            # ThreadLocal authenticated user & RBAC
│   │   ├── service/                           # Business logic layer
│   │   ├── storage/                           # Storage interfaces and MinIO implementation
│   │   ├── ui/
│   │   │   ├── controller/                    # 11 JavaFX FXML controllers
│   │   │   └── util/FxImageUtils.java         # AWT BufferedImage -> JavaFX Image
│   │   └── util/
│   │       ├── IdGenerator.java               # UUID v4 generator
│   │       ├── PasswordUtil.java              # BCrypt + PBKDF2 hasher
│   │       └── ValidationUtil.java            # Email/phone/DOB regex validators
│   └── resources/
│       ├── config/application.properties      # Database & Storage properties
│       ├── css/styles.css                     # Modern Healthcare CSS theme
│       └── fxml/                              # 11 Declarative FXML views
└── test/java/com/clinicdesk/test/
    └── ComprehensiveSystemVerificationRunner.java  # Master 8-module test harness
```

---

## 5. 🛠️ Execution & Testing Commands (Windows PowerShell)

### Launch Database & MinIO (Docker)
```powershell
docker compose up -d
```
*(If Docker is not installed on Windows, install MySQL 8 Community Server and execute `infrastructure/mysql/init/01-schema.sql` followed by `02-seed.sql`)*.

### Build & Run the Application
```powershell
mvn clean javafx:run
```

### Run All Unit & Integration Tests
```powershell
mvn test
```

### Run Master Verification Runner (Headless)
```powershell
java -ea -cp "target/classes;target/test-classes" com.clinicdesk.test.ComprehensiveSystemVerificationRunner
```
*(Ensure all 8 modules pass with 100% assertions)*.

---

## 6. ✅ Definition of Done for Handover Tasks

1. **Real-Time Camera Feed:**
   - Clicking "Camera Capture" in Overview, Medical Records, or Documents opens the dialog and shows live, smooth 30 FPS video from the physical webcam (NO synthetic `[Camera Device In Use / Fallback]` graphic).
2. **Snapshot & Transformation:**
   - Clicking "Capture Photograph" freezes the live feed. Rotation buttons (90°/180°/270°) and flip buttons (Horizontal/Vertical) transform the captured frame in real-time.
3. **Resilient Document Storage:**
   - Captured photos and uploaded documents persist cleanly to MinIO (if running) OR to local disk storage (`~/.clinicdesk/documents/` if MinIO is offline).
4. **End-to-End Clinical Flow:**
   - Capturing a prescription during a doctor consultation links the document to the patient's medical record, updating the timeline immediately.
5. **Master Test Suite Passing:**
   - `ComprehensiveSystemVerificationRunner` completes with `ALL 8/8 MODULE VERIFICATION SUITES COMPLETED SUCCESSFULLY! (100%)`.
