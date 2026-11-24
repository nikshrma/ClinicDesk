package com.clinicdesk.security;

import com.clinicdesk.exception.UnauthorizedAccessException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;

import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Thread-safe session context holding the currently authenticated user
 * and providing role-based security assertions.
 */
public final class SessionContext {

    private static final Logger LOGGER = AppLogger.getLogger(SessionContext.class);
    private static volatile User currentUser = null;

    private SessionContext() {
        // Utility
    }

    public static synchronized void setCurrentUser(User user) {
        currentUser = user;
        if (user != null) {
            LOGGER.info("Session started for: " + user.getEmail() + " [" + user.getRole() + "]");
        } else {
            LOGGER.info("Session cleared (logged out).");
        }
    }

    public static Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public static boolean isAuthenticated() {
        return currentUser != null && currentUser.isActive();
    }

    public static void clear() {
        setCurrentUser(null);
    }

    /**
     * Checks if the currently logged-in user possesses any of the required roles.
     *
     * @param requiredRoles one or more permissible roles
     * @return true if authorized, false otherwise
     */
    public static boolean hasRole(Role... requiredRoles) {
        if (!isAuthenticated()) {
            return false;
        }
        Role userRole = currentUser.getRole();
        return Arrays.stream(requiredRoles).anyMatch(r -> r == userRole);
    }

    /**
     * Enforces that an authenticated user is present and possesses at least one of the specified roles.
     * Throws UnauthorizedAccessException if violated.
     *
     * @param requiredRoles permissible roles
     */
    public static void requireRole(Role... requiredRoles) {
        if (!isAuthenticated()) {
            throw new UnauthorizedAccessException("Authentication required to perform this action.");
        }
        if (!hasRole(requiredRoles)) {
            throw new UnauthorizedAccessException("Access denied. Required role(s): " + Arrays.toString(requiredRoles) +
                    ", current role: " + currentUser.getRole());
        }
    }
}
