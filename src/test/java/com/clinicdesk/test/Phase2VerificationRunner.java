package com.clinicdesk.test;

import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.AuthenticationException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.service.AuthService;
import com.clinicdesk.util.PasswordUtil;

import java.util.*;

public class Phase2VerificationRunner {

    public static void main(String[] args) {
        System.out.println("Starting Phase 2 Verification...");

        // 1. Test Password Hashing and Verification
        String raw = "DemoPass123!";
        String hash = PasswordUtil.hashPassword(raw);
        assert hash != null && !hash.equals(raw) : "Password hashing failed";
        assert PasswordUtil.verifyPassword(raw, hash) : "Password verification should succeed";
        assert !PasswordUtil.verifyPassword("WrongPass", hash) : "Password verification should fail on wrong pass";
        System.out.println("✓ PasswordUtil hashing & verification verified.");

        // 2. Test AuthService and SessionContext
        Map<String, User> userDb = new HashMap<>();
        UserDAO mockDao = new UserDAO() {
            @Override public void save(User entity) { userDb.put(entity.getId(), entity); }
            @Override public Optional<User> findById(String id) { return Optional.ofNullable(userDb.get(id)); }
            @Override public Optional<User> findByEmail(String email) {
                return userDb.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
            }
            @Override public List<User> findAll(boolean inc) { return new ArrayList<>(userDb.values()); }
            @Override public List<User> findByRole(Role r) {
                return userDb.values().stream().filter(u -> u.getRole() == r).toList();
            }
            @Override public void update(User entity) { userDb.put(entity.getId(), entity); }
            @Override public void updatePassword(String id, String h) { userDb.get(id).setPasswordHash(h); }
            @Override public void setActiveStatus(String id, boolean a) { userDb.get(id).setActive(a); }
            @Override public void delete(String id) { userDb.remove(id); }
        };

        AuthService authService = new AuthService(mockDao);

        // Seed users
        User admin = new User("1", "Admin", "admin@clinic.local", PasswordUtil.hashPassword("AdminPass!"), Role.ADMIN, true);
        User doctor = new User("2", "Dr. Dave", "dave@clinic.local", PasswordUtil.hashPassword("DocPass!"), Role.DOCTOR, true);
        User inactive = new User("3", "Inactive", "off@clinic.local", PasswordUtil.hashPassword("OffPass!"), Role.RECEPTIONIST, false);
        userDb.put(admin.getId(), admin);
        userDb.put(doctor.getId(), doctor);
        userDb.put(inactive.getId(), inactive);

        // Test login success
        User loggedIn = authService.login("admin@clinic.local", "AdminPass!");
        assert loggedIn.getId().equals("1") : "User ID mismatch";
        assert SessionContext.isAuthenticated() : "SessionContext should be authenticated";
        assert SessionContext.hasRole(Role.ADMIN) : "Should have ADMIN role";
        System.out.println("✓ AuthService login & SessionContext verified.");

        // Test login failure on wrong pass
        try {
            authService.login("admin@clinic.local", "Wrong!");
            throw new AssertionError("Should have failed on wrong pass");
        } catch (AuthenticationException e) {
            System.out.println("✓ Login rejected on wrong password.");
        }

        // Test login failure on deactivated user
        try {
            authService.login("off@clinic.local", "OffPass!");
            throw new AssertionError("Should have failed on inactive user");
        } catch (AuthenticationException e) {
            System.out.println("✓ Login rejected on deactivated account.");
        }

        // Test RBAC: Doctor trying to create staff
        SessionContext.setCurrentUser(doctor);
        try {
            authService.createStaff("Nurse Nina", "nina@clinic.local", "NinaPass123!", Role.RECEPTIONIST);
            throw new AssertionError("Doctor must not be able to create staff");
        } catch (UnauthorizedAccessException e) {
            System.out.println("✓ Non-admin prevented from creating staff (RBAC enforced).");
        }

        // Test Admin creating staff
        SessionContext.setCurrentUser(admin);
        User created = authService.createStaff("Nurse Nina", "nina@clinic.local", "NinaPass123!", Role.RECEPTIONIST);
        assert created != null && created.getEmail().equals("nina@clinic.local") : "Staff creation failed";
        System.out.println("✓ Admin created staff successfully.");

        // Test logout
        authService.logout();
        assert !SessionContext.isAuthenticated() : "Logout should clear session";
        System.out.println("✓ Logout verified.");

        System.out.println("\nALL PHASE 2 VERIFICATIONS PASSED SUCCESSFULLY!");
    }
}
