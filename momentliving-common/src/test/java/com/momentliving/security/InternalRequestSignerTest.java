package com.momentliving.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalRequestSignerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void sameCanonicalRequestProducesVerifiableSignature() {
        String signature = InternalRequestSigner.sign(
                SECRET, "GET", "/shop/1", "1700000000", "nonce", "7", null, null, null);
        String expected = InternalRequestSigner.sign(
                SECRET, "GET", "/shop/1", "1700000000", "nonce", "7", null, null, null);

        assertTrue(InternalRequestSigner.constantTimeEquals(expected, signature));
    }

    @Test
    void changingPathOrIdentityInvalidatesSignature() {
        String signature = InternalRequestSigner.sign(
                SECRET, "GET", "/shop/1", "1700000000", "nonce", "7", null, null, null);
        String tampered = InternalRequestSigner.sign(
                SECRET, "GET", "/shop/2", "1700000000", "nonce", "8", null, null, null);

        assertFalse(InternalRequestSigner.constantTimeEquals(tampered, signature));
    }
}
