package com.clinicdesk.dao.mysql;

import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.dao.UserDAO;
import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct JDBC implementation of UserDAO using prepared statements and transactions.
 */
public class MySQLUserDAO implements UserDAO {

    private static final Logger LOGGER = AppLogger.getLogger(MySQLUserDAO.class);

    @Override
    public void save(User user) throws SQLException {
        String sql = "INSERT INTO users (id, full_name, email, password_hash, role, active, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getRole().name());
            ps.setBoolean(6, user.isActive());
            ps.setTimestamp(7, Timestamp.from(user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now()));
            ps.setTimestamp(8, Timestamp.from(user.getUpdatedAt() != null ? user.getUpdatedAt() : Instant.now()));

            ps.executeUpdate();
            LOGGER.info("User created: " + user.getEmail() + " (" + user.getRole() + ")");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to insert user: " + user.getEmail(), e);
            throw e;
        }
    }

    @Override
    public Optional<User> findById(String id) throws SQLException {
        String sql = "SELECT id, full_name, email, password_hash, role, active, created_at, updated_at FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find user by id: " + id, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT id, full_name, email, password_hash, role, active, created_at, updated_at FROM users WHERE email = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find user by email: " + email, e);
            throw e;
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll(boolean includeInactive) throws SQLException {
        String sql = includeInactive ?
                "SELECT id, full_name, email, password_hash, role, active, created_at, updated_at FROM users ORDER BY full_name ASC" :
                "SELECT id, full_name, email, password_hash, role, active, created_at, updated_at FROM users WHERE active = TRUE ORDER BY full_name ASC";

        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to list users", e);
            throw e;
        }
        return users;
    }

    @Override
    public List<User> findByRole(Role role) throws SQLException {
        String sql = "SELECT id, full_name, email, password_hash, role, active, created_at, updated_at FROM users WHERE role = ? AND active = TRUE ORDER BY full_name ASC";
        List<User> users = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to list users by role: " + role, e);
            throw e;
        }
        return users;
    }

    @Override
    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, email = ?, role = ?, active = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole().name());
            ps.setBoolean(4, user.isActive());
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.setString(6, user.getId());

            ps.executeUpdate();
            LOGGER.info("User updated: " + user.getId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update user: " + user.getId(), e);
            throw e;
        }
    }

    @Override
    public void updatePassword(String userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newPasswordHash);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, userId);

            ps.executeUpdate();
            LOGGER.info("Password updated for user: " + userId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update password for user: " + userId, e);
            throw e;
        }
    }

    @Override
    public void setActiveStatus(String userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET active = ?, updated_at = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, active);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, userId);

            ps.executeUpdate();
            LOGGER.info("Active status set to " + active + " for user: " + userId);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update active status for user: " + userId, e);
            throw e;
        }
    }

    @Override
    public void delete(String id) throws SQLException {
        // Soft delete preferred, but interface supports hard delete
        setActiveStatus(id, false);
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.fromString(rs.getString("role")));
        user.setActive(rs.getBoolean("active"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            user.setCreatedAt(created.toInstant());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            user.setUpdatedAt(updated.toInstant());
        }
        return user;
    }
}
