-- =====================================================================
-- ClinicDesk Seed Data
-- Default Staff Passwords: Password123!
-- BCrypt Hash: $2a$12$e8xL4k4hO4N/h12z66bCxe584X6Wb75rJp9R3qY6hW.s8WqF.iK8C (or generated on start)
-- =====================================================================

USE clinicdesk;

-- Default Users:
-- Admin: admin@clinicdesk.local / Admin123!
-- Doctor: dr.smith@clinicdesk.local / Doctor123!
-- Receptionist: reception@clinicdesk.local / Reception123!

INSERT INTO users (id, full_name, email, password_hash, role, active)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'System Administrator', 'admin@clinicdesk.local', '$2a$12$K1I97E259y87E4g9q4h.mOBR4eJgU1U2Q.pZ5YtD1zJbT7p1WbSj2', 'ADMIN', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'Dr. Sarah Smith, MD', 'dr.smith@clinicdesk.local', '$2a$12$K1I97E259y87E4g9q4h.mOBR4eJgU1U2Q.pZ5YtD1zJbT7p1WbSj2', 'DOCTOR', TRUE),
    ('00000000-0000-0000-0000-000000000003', 'John Doe (Front Desk)', 'reception@clinicdesk.local', '$2a$12$K1I97E259y87E4g9q4h.mOBR4eJgU1U2Q.pZ5YtD1zJbT7p1WbSj2', 'RECEPTIONIST', TRUE)
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name);
