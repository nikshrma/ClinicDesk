package com.clinicdesk.security;

import com.clinicdesk.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void testHashAndVerifyPassword() {
        String rawPassword = "SecurePassword123!";
        String hash = PasswordUtil.hashPassword(rawPassword);

        assertNotNull(hash, "Password hash should not be null");
        assertNotEquals(rawPassword, hash, "Hash must not equal plaintext password");

        assertTrue(PasswordUtil.verifyPassword(rawPassword, hash), "Password verification should succeed for valid password");
        assertFalse(PasswordUtil.verifyPassword("WrongPassword123!", hash), "Password verification should fail for invalid password");
    }

    @Test
    void testVerifyRejectsNullOrEmpty() {
        assertFalse(PasswordUtil.verifyPassword(null, "somehash"));
        assertFalse(PasswordUtil.verifyPassword("password", null));
        assertFalse(PasswordUtil.verifyPassword("password", ""));
    }

    @Test
    void testHashRejectsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(""));
    }
}
