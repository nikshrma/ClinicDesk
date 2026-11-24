# ClinicDesk — AI Agent & Windows Developer Onboarding Guide

> **Directive for AI Agents & Developers:**
> You are picking up development of **ClinicDesk** on a Windows workstation. Read this entire document before executing commands or modifying code.

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
- **Configuration File:** [src/main/resources/config/application.properties](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/resources/config/application.properties)
- **Pre-Seeded Login Accounts:**

| Role | Email | Password | Permissions |
| :--- | :--- | :--- | :--- |
| **System Admin** | `admin@clinicdesk.local` | `password123` | Full system access, Staff Management, Settings & `.cdbak` Backup/Restore |
| **Doctor** | `dr.smith@clinicdesk.local` | `password123` | Patient Charts, Medical Records (Diagnosis only), Prescription Camera Capture |
| **Receptionist** | `reception@clinicdesk.local` | `password123` | Patient Directory (Registration/Search), Appointment Scheduling & Check-In |

*(Note: `PasswordUtil.java` contains a development bypass allowing `password123`, `Admin123!`, `Doctor123!`, or `Reception123!`)*.

---

## 3. 🎯 Priority Tasks to Complete

### Task 1: Real-Time Laptop Camera Capture on Windows

#### The Problem:
On macOS, `OpenCVCameraService` defaulted to a synthetic test pattern graphic (`Patient: [Camera Device In Use / Fallback]`) due to macOS AVFoundation's asynchronous TCC privacy gate.

#### Key Files:
- [src/main/java/com/clinicdesk/camera/OpenCVCameraService.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/camera/OpenCVCameraService.java)
- [src/main/java/com/clinicdesk/camera/ImageProcessingService.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/camera/ImageProcessingService.java)
- [src/main/java/com/clinicdesk/ui/controller/CameraCaptureController.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/ui/controller/CameraCaptureController.java)
- [src/main/resources/fxml/CameraCaptureDialog.fxml](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/resources/fxml/CameraCaptureDialog.fxml)

#### Implementation Instructions for Windows:
1. Windows uses **DirectShow (`DSHOW`)** or **Media Foundation (`MSMF`)** via JavaCV / OpenCV.
2. In `OpenCVCameraService.startLiveStream()`:
   - Instantiate `grabber = new OpenCVFrameGrabber(deviceIndex)`.
   - On Windows, test default resolution or set standard resolution (e.g. `grabber.setImageWidth(1280); grabber.setImageHeight(720);`).
   - Implement a **sensor warm-up loop**: DirectShow/MediaFoundation webcams often produce 1–3 blank/null frames while auto-exposure and sensor hardware power up. Add a loop grabbing up to 5 warm-up frames before publishing to the UI stream:
     ```java
     grabber.start();
     for (int i = 0; i < 5; i++) {
         Frame warmUp = grabber.grab();
         if (warmUp != null && warmUp.image != null) break;
         Thread.sleep(50);
     }
     ```
   - Only set `hardwareActive = false` if all retries throw an exception.
3. Verify live 30 FPS video streaming in `CameraCaptureDialog.fxml`, verify snapshot capture freezes the live webcam image, and confirm rotate (90°/180°/270°) and flip (H/V) work on the captured frame.

---

### Task 2: Dual-Mode Document Storage (Local Disk Fallback when MinIO is Offline)

#### The Problem:
When capturing or uploading a prescription/document, `DocumentService` attempts to upload the binary blob to MinIO (`http://localhost:9000`). If MinIO is offline, it throws:
`Failed to verify or create MinIO bucket: clinicdesk-documents`

#### Key Files:
- [src/main/java/com/clinicdesk/storage/DocumentStorageService.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/storage/DocumentStorageService.java)
- [src/main/java/com/clinicdesk/storage/minio/MinioDocumentStorageService.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/storage/minio/MinioDocumentStorageService.java)
- [src/main/java/com/clinicdesk/service/DocumentService.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/service/DocumentService.java)
- [src/main/java/com/clinicdesk/config/StorageConfig.java](file:///Users/nikhilsharma/nikshrma/Projects/ClinicDesk/src/main/java/com/clinicdesk/config/StorageConfig.java)

#### Implementation Instructions:
1. Create a `LocalDocumentStorageService` implementing `DocumentStorageService`:
   - Package: `com.clinicdesk.storage.local`
   - Root directory: `System.getProperty("user.home") + File.separator + ".clinicdesk" + File.separator + "documents"`
   - `upload(File file, String contentType)`: Generates a unique key (`UUID + "-" + filename`), copies file to root directory, and returns the key.
   - `download(String objectKey)`: Returns `new FileInputStream(new File(rootDir, objectKey))`.
   - `delete(String objectKey)`: Deletes file from local root directory.
   - `generatePresignedUrl(String objectKey, int expirySeconds)`: Returns `new File(rootDir, objectKey).toURI().toString()`.
   - `isAvailable()`: Returns `true` if root directory exists or is writable.
2. In `StorageConfig` or `DocumentService`:
   - Check if `MinioDocumentStorageService.isAvailable()` is true.
   - If MinIO is offline / unreachable, automatically fall back to `LocalDocumentStorageService`.
   - Log an informative message: `MinIO unavailable. Operating with LocalDocumentStorageService at <path>`.
3. Verify that capturing a prescription photograph saves successfully to MySQL metadata and local storage without throwing an error.

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

### Launch Database
```powershell
docker compose up -d
```
*(If Docker is not installed on Windows, install standard MySQL 8 Community Server and execute `infrastructure/mysql/init/01-schema.sql` followed by `02-seed.sql`)*.

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

1. **Camera Feed:** Clicking "Camera Capture" in the Overview Dashboard or Document Library opens the dialog and shows live, smooth 30 FPS video of the real webcam on Windows.
2. **Snapshot & Transformation:** Clicking "Capture Photograph" freezes the live feed. Rotation buttons (90°/180°/270°) and flip buttons (Horizontal/Vertical) transform the snapshot in real-time.
3. **Save & Confirmation:** Clicking "Confirm & Save" uploads the prescription image (to MinIO or Local Disk Fallback), records the metadata in MySQL, and associates it with the patient's record without errors.
4. **All Tests Green:** `ComprehensiveSystemVerificationRunner` completes with `ALL 8/8 MODULE VERIFICATION SUITES COMPLETED SUCCESSFULLY! (100%)`.
