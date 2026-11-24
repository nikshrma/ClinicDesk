package com.clinicdesk.config;

import com.clinicdesk.logging.AppLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and manages application properties from config/application.properties
 * with fallback to environment variables.
 */
public final class AppConfig {

    private static final Logger LOGGER = AppLogger.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "/config/application.properties";
    private static final Properties PROPERTIES = new Properties();
    private static boolean loaded = false;

    private AppConfig() {
        // Singleton utility
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        try (InputStream in = AppConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                PROPERTIES.load(in);
                LOGGER.info("Configuration properties loaded successfully from " + CONFIG_FILE);
            } else {
                LOGGER.warning("Could not find " + CONFIG_FILE + " in classpath. Using defaults/env.");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read configuration file: " + CONFIG_FILE, e);
        }

        loaded = true;
    }

    public static String get(String key, String defaultValue) {
        if (!loaded) {
            load();
        }
        // 1. Check System Property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        // 2. Check Environment Variable (converted to UPPER_UNDERSCORE)
        String envKey = key.toUpperCase().replace('.', '_');
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        // 3. Check Properties file
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid integer value for key " + key + ": " + val + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, null);
        if (val == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }
}
