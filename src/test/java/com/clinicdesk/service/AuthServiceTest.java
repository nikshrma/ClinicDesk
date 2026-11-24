package com.clinicdesk.service;

import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.AuthenticationException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private InMemoryUserDAO userDAO;
    private AuthService authService;

    @BeforeEach
    void setup() {
        SessionContext.clear();
        userDAO = new InMemoryUserDAO();
        authService = new AuthService(userDAO);

        // Seed a sample admin user
        String adminHash = PasswordUtil.hashPassword("AdminPass123!");
        User admin = new User("admin-1", "System Admin", "admin@clinicdesk.local", adminHash, Role.ADMIN, true);
        userDAO.users.put(admin.getId(), admin);

        // Seed a sample doctor
        String docHash = PasswordUtil.hashPassword("DoctorPass123!");
        User doctor = new User("doc-1", "Dr. Jane Smith", "dr.smith@clinicdesk.local", docHash, Role.DOCTOR, true);
        userDAO.users.put(doctor.getId(), doctor);

        // Seed a deactivated receptionist
        String recHash = PasswordUtil.hashPassword("ReceptionPass123!");
        User inactiveReceptionist = new User("rec-1", "Bob Inactive", "bob@clinicdesk.local", recHash, Role.RECEPTIONIST, false);
        userDAO.users.put(inactiveReceptionist.getId(), inactiveReceptionist);
    }

    @Test
    void testSuccessfulLogin() {
        User loggedIn = authService.login("admin@clinicdesk.local", "AdminPass123!");

        assertNotNull(loggedIn);
        assertEquals("admin@clinicdesk.local", loggedIn.getEmail());
        assertTrue(SessionContext.isAuthenticated());
        assertEquals(Role.ADMIN, SessionContext.getCurrentUser().get().getRole());
    }

    @Test
    void testLoginInvalidPasswordFails() {
        assertThrows(AuthenticationException.class, () ->
                authService.login("admin@clinicdesk.local", "WrongPassword!")
        );
        assertFalse(SessionContext.isAuthenticated());
    }

    @Test
    void testLoginUnknownEmailFails() {
        assertThrows(AuthenticationException.class, () ->
                authService.login("unknown@clinicdesk.local", "SomePass123!")
        );
        assertFalse(SessionContext.isAuthenticated());
    }

    @Test
    void testLoginDeactivatedAccountFails() {
        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                authService.login("bob@clinicdesk.local", "ReceptionPass123!")
        );
        assertTrue(ex.getMessage().contains("deactivated"));
        assertFalse(SessionContext.isAuthenticated());
    }

    @Test
    void testLogoutClearsSession() {
        authService.login("admin@clinicdesk.local", "AdminPass123!");
        assertTrue(SessionContext.isAuthenticated());

        authService.logout();
        assertFalse(SessionContext.isAuthenticated());
        assertTrue(SessionContext.getCurrentUser().isEmpty());
    }

    @Test
    void testCreateStaffRequiresAdminRole() {
        // Log in as doctor
        authService.login("dr.smith@clinicdesk.local", "DoctorPass123!");

        // Doctor attempting to create staff should fail with UnauthorizedAccessException
        assertThrows(UnauthorizedAccessException.class, () ->
                authService.createStaff("Nurse Joy", "joy@clinicdesk.local", "NursePass123!", Role.RECEPTIONIST)
        );

        // Log in as admin
        authService.login("admin@clinicdesk.local", "AdminPass123!");

        User newStaff = authService.createStaff("Nurse Joy", "joy@clinicdesk.local", "NursePass123!", Role.RECEPTIONIST);
        assertNotNull(newStaff);
        assertEquals("joy@clinicdesk.local", newStaff.getEmail());
        assertEquals(Role.RECEPTIONIST, newStaff.getRole());
    }

    @Test
    void testDeactivateStaffRequiresAdminRole() {
        // Doctor trying to deactivate someone
        authService.login("dr.smith@clinicdesk.local", "DoctorPass123!");
        assertThrows(UnauthorizedAccessException.class, () -> authService.deactivateStaff("doc-1"));

        // Admin deactivates staff
        authService.login("admin@clinicdesk.local", "AdminPass123!");
        authService.deactivateStaff("doc-1");
        assertFalse(userDAO.users.get("doc-1").isActive());
    }

    // In-Memory Test Stub for UserDAO
    private static class InMemoryUserDAO implements UserDAO {
        private final Map<String, User> users = new HashMap<>();

        @Override
        public void save(User entity) {
            users.put(entity.getId(), entity);
        }

        @Override
        public Optional<User> findById(String id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return users.values().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        }

        @Override
        public List<User> findAll(boolean includeInactive) {
            return users.values().stream()
                    .filter(u -> includeInactive || u.isActive())
                    .toList();
        }

        @Override
        public List<User> findByRole(Role role) {
            return users.values().stream()
                    .filter(u -> u.getRole() == role && u.isActive())
                    .toList();
        }

        @Override
        public void update(User entity) {
            users.put(entity.getId(), entity);
        }

        @Override
        public void updatePassword(String userId, String newPasswordHash) {
            User u = users.get(userId);
            if (u != null) {
                u.setPasswordHash(newPasswordHash);
            }
        }

        @Override
        public void setActiveStatus(String userId, boolean active) {
            User u = users.get(userId);
            if (u != null) {
                u.setActive(active);
            }
        }

        @Override
        public void delete(String id) {
            users.remove(id);
        }
    }
}
