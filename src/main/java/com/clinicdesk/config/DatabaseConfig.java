package com.clinicdesk.config;

import com.clinicdesk.exception.DatabaseException;
import com.clinicdesk.logging.AppLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connectivity using HikariCP connection pool and raw JDBC.
 * Fully configurable via AppConfig.
 */
public final class DatabaseConfig {

    private static final Logger LOGGER = AppLogger.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;
    private static boolean initialized = false;

    private DatabaseConfig() {
        // Utility singleton
    }

    public static synchronized void initialize() {
        if (initialized && dataSource != null && !dataSource.isClosed()) {
            return;
        }

        try {
            String jdbcUrl = AppConfig.get("db.url", "jdbc:mysql://localhost:3306/clinicdesk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            String username = AppConfig.get("db.username", "clinicdesk");
            String password = AppConfig.get("db.password", "clinicdesk123");
            int maxPoolSize = AppConfig.getInt("db.pool.maximumPoolSize", 10);
            int minIdle = AppConfig.getInt("db.pool.minimumIdle", 2);
            long connTimeout = AppConfig.getInt("db.pool.connectionTimeout", 10000);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(connTimeout);
            config.setPoolName("ClinicDesk-HikariPool");

            // Recommended MySQL performance properties
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
            initialized = true;
            LOGGER.info("Database connection pool initialized successfully: " + jdbcUrl);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Database connection pool", e);
            throw new DatabaseException("Could not initialize database connection pool: " + e.getMessage(), e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!initialized || dataSource == null) {
            initialize();
        }
        return dataSource.getConnection();
    }

    public static HikariDataSource getDataSource() {
        if (!initialized || dataSource == null) {
            initialize();
        }
        return dataSource;
    }

    public static synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.info("Closing database connection pool...");
            dataSource.close();
            initialized = false;
        }
    }

    public static boolean isHealthy() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
