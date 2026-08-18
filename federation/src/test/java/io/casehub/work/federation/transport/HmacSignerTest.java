package io.casehub.work.federation.transport;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignerTest {

    private final byte[] secret = "test-secret-key-32bytes-long!!!!".getBytes(StandardCharsets.UTF_8);

    @Test
    void signAndVerifyRoundTrips() {
        String payload = "{\"type\":\"io.casehub.work.federation.created\",\"id\":\"abc-123\"}";
        String signature = HmacSigner.sign(payload, secret);
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        assertTrue(HmacSigner.verify(payload, signature, secret));
    }

    @Test
    void rejectsWrongSignature() {
        String payload = "some payload";
        assertFalse(HmacSigner.verify(payload, "wrong-signature", secret));
    }

    @Test
    void rejectsTamperedPayload() {
        String payload = "original payload";
        String signature = HmacSigner.sign(payload, secret);
        assertFalse(HmacSigner.verify("tampered payload", signature, secret));
    }

    @Test
    void rejectsWrongSecret() {
        String payload = "some payload";
        String signature = HmacSigner.sign(payload, secret);
        byte[] wrongSecret = "wrong-secret-key-32bytes-long!!!".getBytes(StandardCharsets.UTF_8);
        assertFalse(HmacSigner.verify(payload, signature, wrongSecret));
    }

    @Test
    void deterministicForSameInputs() {
        String payload = "deterministic test";
        String sig1 = HmacSigner.sign(payload, secret);
        String sig2 = HmacSigner.sign(payload, secret);
        assertEquals(sig1, sig2);
    }
}
