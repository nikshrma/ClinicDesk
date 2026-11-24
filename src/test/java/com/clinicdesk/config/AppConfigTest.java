package com.clinicdesk.config;

import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.exception.PatientNotFoundException;
import com.clinicdesk.logging.AppLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @BeforeAll
    static void setup() {
        AppLogger.init();
        AppConfig.load();
    }

    @Test
    void testAppConfigLoadsProperties() {
        String dbUrl = AppConfig.get("db.url", null);
        assertNotNull(dbUrl, "db.url property should be populated");
        assertTrue(dbUrl.contains("clinicdesk"), "db.url should reference clinicdesk database");

        int maxPool = AppConfig.getInt("db.pool.maximumPoolSize", 5);
        assertEquals(10, maxPool, "db.pool.maximumPoolSize should be 10 as configured in application.properties");

        int maxUpload = AppConfig.getInt("app.upload.maxFileSizeMb", 10);
        assertEquals(10, maxUpload, "app.upload.maxFileSizeMb should be 10MB");
    }

    @Test
    void testAppLoggerInitialization() {
        Logger logger = AppLogger.getLogger(AppConfigTest.class);
        assertNotNull(logger, "Logger instance should not be null");
        assertDoesNotThrow(() -> logger.info("Test log entry"));
    }

    @Test
    void testExceptionHierarchy() {
        PatientNotFoundException ex = new PatientNotFoundException("Patient CD-001 not found");
        assertInstanceOf(ClinicDeskException.class, ex);
        assertEquals("Patient CD-001 not found", ex.getMessage());
    }
}
