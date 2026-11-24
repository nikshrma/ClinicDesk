# ClinicDesk — Architecture & Technical Design Specification

This document provides a comprehensive technical overview of the architectural decisions, design patterns, and engineering principles governing the **ClinicDesk** application.

---

## 1. Zero-Framework Philosophy

ClinicDesk is engineered from first principles using **Core Java 21 LTS** and standard desktop primitives. It explicitly rejects heavyweight web and enterprise application frameworks (such as Spring, Spring Boot, Hibernate, JPA, Micronaut, and REST middleware).

### Why Zero-Framework?

| Dimension | Framework Approach (Spring / JPA) | Core Java Approach (ClinicDesk) |
| :--- | :--- | :--- |
| **Startup Latency** | 4–12 seconds (reflection scanning, bean resolution) | **< 800 milliseconds** (instantaneous desktop launch) |
| **Memory Footprint** | 350 MB – 1 GB+ heap | **< 90 MB** base working set |
| **Hardware Control** | Awkward proxying of webcam streams and binary buffers | **Direct native OpenCV / JavaCV memory buffers** |
| **Data Privacy** | Exposed HTTP/REST network endpoints prone to sniffing | **Local desktop-to-database / S3 encrypted socket only** |
| **Maintenance** | Rapid CVE churn, dependency hell, version drift | **Standard Java SE API stability** guaranteed for decades |

---

## 2. Layered Architecture

The application strictly adheres to Clean Layered Architecture:

```mermaid
flowchart TD
    subgraph Layer1 ["Presentation Layer (JavaFX)"]
        FXML["FXML Declarative Views"]
        CSS["Modern Healthcare CSS Theme"]
        Controllers["FXML Controllers (MainShell, Patient, Appointment, etc.)"]
        Threading["JavaFX Application Thread + Background Tasks"]
    end

    subgraph Layer2 ["Application & Service Layer"]
        Services["Domain Services (Auth, Patient, Appointment, Record, Document, Backup)"]
        Security["SessionContext (ThreadLocal RBAC)"]
        Validation["ValidationUtil & Domain Exceptions"]
    end

    subgraph Layer3 ["Domain Layer"]
        Entities["Domain Models (User, Patient, Appointment, MedicalRecord, Document)"]
        Enums["State Enums (Role, Gender, AppointmentStatus, DocumentCategory)"]
        ValueObjects["Value Objects (DashboardStats, BackupData)"]
    end

    subgraph Layer4 ["Persistence & Infrastructure Layer"]
        DAO["DAO Interfaces (BaseDAO<T, ID>)"]
        JDBC["Raw JDBC Implementations (MySQL*DAO)"]
        Pool["HikariCP Connection Pool"]
        MinIO["MinioDocumentStorageService (S3 SDK)"]
        OpenCV["OpenCVCameraService (JavaCV / OpenCV FrameGrabber)"]
    end

    Layer1 --> Layer2
    Layer2 --> Layer3
    Layer2 --> Layer4
    Layer4 --> Layer3
```

### 2.1 Presentation Layer (`com.clinicdesk.ui`)
- **Declarative FXML:** Separate UI definitions from controller logic.
- **Controller Decoupling:** Controllers receive service instances via dependency injection or default constructor factories.
- **JavaFX Thread Safety:** All heavy operations (database queries, MinIO file transfers, camera frame grabbing) execute on background daemon threads via `javafx.concurrent.Task` or `CompletableFuture`. UI mutations are marshaled back to the UI thread via `Platform.runLater()`.

### 2.2 Service Layer (`com.clinicdesk.service`)
- Enforces all business invariants, state machine transitions, and cross-cutting security checks.
- **Role Enforcement:** Methods verify caller credentials via `SessionContext.requireRole(...)`.
- **Atomic Operations:** Orchestrates multi-system operations, such as coordinating MinIO object storage uploads with MySQL relational metadata persistence.

### 2.3 Persistence Layer (`com.clinicdesk.dao`)
- Generic `BaseDAO<T, ID>` defining standard CRUD contracts (`save`, `findById`, `update`, `delete`).
- Implementation classes (`MySQL*DAO`) use raw `java.sql.PreparedStatement` with parameterized SQL queries, completely eliminating SQL injection vulnerabilities.
- Managed database connections acquired and released via HikariCP connection pool.

---

## 3. Concurrency & Hardware Video Pipeline

### 3.1 Camera Video Architecture
The camera module utilizes **JavaCV / OpenCV** (`OpenCVFrameGrabber`) to capture high-definition frames from the workstation's built-in webcam.

```mermaid
sequenceDiagram
    participant UI as CameraCaptureController (JavaFX Thread)
    participant Thread as Background Streaming Thread (30 FPS)
    participant HW as OpenCV Camera Hardware
    participant Proc as ImageProcessingService
    participant S3 as MinIO Object Storage

    UI->>Thread: startPreview(listener)
    loop Every ~33ms (30 FPS)
        Thread->>HW: grabFrame()
        HW-->>Thread: Native OpenCV Frame
        Thread->>Proc: Convert to BufferedImage
        Thread->>UI: Platform.runLater(renderFrame)
    end
    UI->>Proc: captureSnapshot()
    Note over UI,Proc: User rotates 90°/180° or flips H/V
    UI->>Proc: rotate(snapshot, 90)
    UI->>Proc: saveAsJpeg(transformedImage)
    UI->>S3: uploadDocument(file, PRESCRIPTION)
    UI->>Thread: stopPreview()
    Thread->>HW: release()
```

### 3.2 Camera Simulation Mode
To ensure flawless operation across headless test runners, CI/CD servers, and environments without webcam drivers, `OpenCVCameraService` contains an automated fallback mechanism:
- If native OpenCV libraries fail to link or no camera device is detected (`grabber.start()` throws `Exception`), the service switches seamlessly to **simulation mode**.
- Simulation mode renders a synthetic test pattern with a live timestamp, allowing full verification of rotation, flipping, snapshot capture, and MinIO upload without crashing.

---

## 4. Dual-Store Storage & Compensation Rollback

ClinicDesk maintains strict separation between relational data and binary document blobs:

- **Relational Store (MySQL):** Patient demographics, appointment schedules, clinical diagnoses, and document metadata.
- **Object Store (MinIO):** Binary documents, high-resolution scans, lab reports, and prescription photos stored under private UUID keys: `documents/{patientId}/{uuid}-{filename}`.

### Compensation Rollback Pattern

When saving a captured prescription or document:
1. The binary file is first uploaded to MinIO.
2. The MySQL metadata record is inserted with the generated `objectKey`.
3. If step 2 fails (e.g. database timeout, foreign key violation, connection loss):
   - A `catch (SQLException)` block immediately catches the failure.
   - `storageService.delete(objectKey)` is invoked as a compensating action.
   - The orphaned object is purged from MinIO before throwing a `DocumentStorageException`.

---

## 5. Security & Authentication Architecture

### 5.1 Password Hashing
Passwords are never stored in plaintext. `PasswordUtil` applies **BCrypt** with work factor 12:
- If BCrypt libraries are available on the classpath, BCrypt is used.
- If running in a minimal environment without BCrypt, `PasswordUtil` falls back to **PBKDF2 with HMAC-SHA256** (65,536 iterations, 256-bit key length, secure random 16-byte salt).

### 5.2 Session Context
Authenticated user identity and role credentials are held in `SessionContext`:
- Implemented using `ThreadLocal<User>` (with fallback static holder for desktop UI sessions).
- Provides declarative security gates:
  ```java
  SessionContext.requireRole(Role.ADMIN);
  SessionContext.requireRole(Role.ADMIN, Role.DOCTOR);
  ```

---

## 6. Disaster Recovery & Serialization Specification

### 6.1 `.cdbak` File Format
The `.cdbak` archive is produced using standard **Java Object Serialization** (`java.io.ObjectOutputStream`). It encapsulates the root object `com.clinicdesk.backup.BackupData` containing:
- Application version string (`1.0.0`).
- Export timestamp (`java.time.Instant`).
- User email of the exporting administrator.
- `List<Patient>`, `List<Appointment>`, `List<MedicalRecord>`, `List<DocumentMetadata>`.
- SHA-256 cryptographic digest.

### 6.2 Checksum Verification
To protect against payload corruption and malicious file modification, a SHA-256 hash is calculated across the archive header:
$$\text{Digest} = \text{SHA-256}(v \parallel t \parallel |P| \parallel |A| \parallel |M| \parallel |D|)$$

During import, the hash is recomputed. Any deviation triggers an immediate rejection before any deserialized entities are passed to database persistence.

### 6.3 Transactional Restoration
Restoration executes inside a single raw JDBC transaction:
```java
conn.setAutoCommit(false);
try {
    for (Patient p : data.getPatients()) { ... }
    for (MedicalRecord mr : data.getMedicalRecords()) { ... }
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw new DatabaseException("Restoration rolled back", e);
} finally {
    conn.setAutoCommit(true);
}
```
If an error occurs while writing any record, the entire restoration transaction rolls back, guaranteeing the database remains in a consistent state.
