package com.clinicdesk.dao;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Generic Data Access Object interface defining standard CRUD operations.
 *
 * @param <T>  Entity domain type
 * @param <ID> Primary key identifier type
 */
public interface BaseDAO<T, ID> {

    void save(T entity) throws SQLException;

    Optional<T> findById(ID id) throws SQLException;

    void update(T entity) throws SQLException;

    void delete(ID id) throws SQLException;
}
