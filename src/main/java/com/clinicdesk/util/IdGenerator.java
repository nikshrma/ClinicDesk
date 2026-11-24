package com.clinicdesk.util;

import java.util.UUID;

/**
 * Utility for generating UUIDs and domain-specific identifiers.
 */
public final class IdGenerator {

    private IdGenerator() {
        // Utility
    }

    public static String nextId() {
        return UUID.randomUUID().toString();
    }
}
