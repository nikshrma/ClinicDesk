package com.clinicdesk.dao;

import com.clinicdesk.model.Role;
import com.clinicdesk.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for User entities.
 */
public interface UserDAO extends BaseDAO<User, String> {

    Optional<User> findByEmail(String email) throws SQLException;

    List<User> findAll(boolean includeInactive) throws SQLException;

    List<User> findByRole(Role role) throws SQLException;

    void updatePassword(String userId, String newPasswordHash) throws SQLException;

    void setActiveStatus(String userId, boolean active) throws SQLException;
}
