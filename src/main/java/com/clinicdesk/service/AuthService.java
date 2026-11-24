package com.clinicdesk.service;

import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.AuthenticationException;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;
import com.clinicdesk.security.SessionContext;
import com.clinicdesk.util.IdGenerator;
import com.clinicdesk.util.PasswordUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating authentication, authorization, and staff user management.
 */
public class AuthService {

    private static final Logger LOGGER = AppLogger.getLogger(AuthService.class);
    private final UserDAO userDAO;

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Authenticates a user with email and plaintext password.
     * Sets the authenticated user in SessionContext on success.
     */
    public User login(String email, String rawPassword) {
        if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new AuthenticationException("Email and password are required.");
        }

        try {
            Optional<User> userOpt = userDAO.findByEmail(email.trim().toLowerCase());
            if (userOpt.isEmpty()) {
                LOGGER.warning("Authentication failed: user not found for email " + email);
                throw new AuthenticationException("Invalid email or password.");
            }

            User user = userOpt.get();
            if (!user.isActive()) {
                LOGGER.warning("Authentication rejected: account is deactivated for " + email);
                throw new AuthenticationException("Account has been deactivated. Please contact an administrator.");
            }

            if (!PasswordUtil.verifyPassword(rawPassword, user.getPasswordHash())) {
                LOGGER.warning("Authentication failed: invalid password for " + email);
                throw new AuthenticationException("Invalid email or password.");
            }

            SessionContext.setCurrentUser(user);
            LOGGER.info("User logged in successfully: " + user.getEmail() + " (" + user.getRole() + ")");
            return user;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during login", e);
            throw new DatabaseException("Database error during authentication: " + e.getMessage(), e);
        }
    }

    public void logout() {
        SessionContext.clear();
    }

    /**
     * Creates a new staff account. Only administrators can execute this.
     */
    public User createStaff(String fullName, String email, String rawPassword, Role role) {
        SessionContext.requireRole(Role.ADMIN);

        if (fullName == null || fullName.isBlank() || email == null || email.isBlank() ||
                rawPassword == null || rawPassword.isBlank() || role == null) {
            throw new IllegalArgumentException("All staff account fields are mandatory.");
        }

        try {
            if (userDAO.findByEmail(email.trim().toLowerCase()).isPresent()) {
                throw new IllegalArgumentException("A user with this email already exists: " + email);
            }

            String passwordHash = PasswordUtil.hashPassword(rawPassword);
            User newUser = new User(IdGenerator.nextId(), fullName.trim(), email.trim().toLowerCase(), passwordHash, role, true);
            userDAO.save(newUser);
            LOGGER.info("Admin created new staff member: " + newUser.getEmail() + " (" + role + ")");
            return newUser;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create staff account", e);
            throw new DatabaseException("Failed to create staff account: " + e.getMessage(), e);
        }
    }

    /**
     * Deactivates a staff account. Only administrators can execute this.
     */
    public void deactivateStaff(String userId) {
        SessionContext.requireRole(Role.ADMIN);
        try {
            userDAO.setActiveStatus(userId, false);
            LOGGER.info("Staff member deactivated: " + userId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to deactivate staff member", e);
            throw new DatabaseException("Failed to deactivate staff: " + e.getMessage(), e);
        }
    }

    /**
     * Activates a staff account. Only administrators can execute this.
     */
    public void activateStaff(String userId) {
        SessionContext.requireRole(Role.ADMIN);
        try {
            userDAO.setActiveStatus(userId, true);
            LOGGER.info("Staff member activated: " + userId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to activate staff member", e);
            throw new DatabaseException("Failed to activate staff: " + e.getMessage(), e);
        }
    }

    /**
     * Returns all active doctors for appointment scheduling.
     */
    public List<User> getActiveDoctors() {
        try {
            return userDAO.findByRole(Role.DOCTOR);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch doctors", e);
            throw new DatabaseException("Failed to load doctor list: " + e.getMessage(), e);
        }
    }

    /**
     * Returns all staff members. Only administrators can access this.
     */
    public List<User> getAllStaff() {
        SessionContext.requireRole(Role.ADMIN);
        try {
            return userDAO.findAll(true);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load staff list", e);
            throw new DatabaseException("Failed to load staff members: " + e.getMessage(), e);
        }
    }
}
