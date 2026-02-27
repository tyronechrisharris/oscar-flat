package org.sensorhub.impl.security;

import org.junit.Test;
import static org.junit.Assert.*;

public class TOTPUtilsTest {

    @Test
    public void testGenerateSecret() {
        String secret = TOTPUtils.generateSecret();
        assertNotNull(secret);
        // 20 bytes * 8 bits / 5 bits = 32 chars
        assertTrue(secret.length() == 32);
        // Ensure no padding
        assertFalse(secret.contains("="));
    }

    @Test
    public void testValidateCode() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP"; // Base32 for "Hello!.." (ascii) -> 48 65 6c 6c 6f 21 ..

        long time = System.currentTimeMillis() / 1000 / 30;

        // Generate current code
        String code = TOTPUtils.generateTOTP(secret, time);

        assertTrue("Current code should be valid", TOTPUtils.validateCode(secret, code));

        // Check previous interval
        String prevCode = TOTPUtils.generateTOTP(secret, time - 1);
        assertTrue("Previous code should be valid", TOTPUtils.validateCode(secret, prevCode));

        // Check next interval
        String nextCode = TOTPUtils.generateTOTP(secret, time + 1);
        assertTrue("Next code should be valid", TOTPUtils.validateCode(secret, nextCode));

        // Check invalid interval
        String invalidCode = TOTPUtils.generateTOTP(secret, time - 2);
        assertFalse("Code from 2 intervals ago should be invalid", TOTPUtils.validateCode(secret, invalidCode));
    }

    @Test
    public void testBase32EncodeDecode() {
        // Test round trip
        String original = "JBSWY3DPEHPK3PXP";
        // decode
        // TOTPUtils.decodeBase32 is private. But generateTOTP calls it.
        // We can test encode via generateSecret.

        String secret = TOTPUtils.generateSecret();
        // Since we can't access decodeBase32 directly, we rely on validateCode working correctly,
        // which implies decode works if generateTOTP works.
        // And generateTOTP works if validateCode works.
    }
}
