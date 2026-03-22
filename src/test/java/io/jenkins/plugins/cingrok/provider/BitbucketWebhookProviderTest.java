package io.jenkins.plugins.cingrok.provider;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BitbucketWebhookProviderTest {

    private final BitbucketWebhookProvider provider = new BitbucketWebhookProvider();
    private static final String SECRET = "bitbucket-secret-789";

    @Test
    void validSignatureAccepted() throws Exception {
        byte[] body = "{\"push\":{}}".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(body, SECRET);

        Map<String, String> headers = Map.of("X-Hub-Signature", signature);
        assertTrue(provider.validate(headers, body, SECRET));
    }

    @Test
    void invalidSignatureRejected() {
        byte[] body = "{\"push\":{}}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Hub-Signature", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void missingHeaderRejected() {
        byte[] body = "{\"push\":{}}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of();
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void wrongPrefixRejected() {
        byte[] body = "{\"push\":{}}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Hub-Signature", "sha1=abc123");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void tamperedBodyRejected() throws Exception {
        byte[] originalBody = "{\"push\":{}}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"push\":{\"malicious\":true}}".getBytes(StandardCharsets.UTF_8);
        String signature = computeSignature(originalBody, SECRET);

        Map<String, String> headers = Map.of("X-Hub-Signature", signature);
        assertFalse(provider.validate(headers, tamperedBody, SECRET));
    }

    @Test
    void providerMetadata() {
        assertEquals("Bitbucket", provider.getName());
        assertEquals("X-Hub-Signature", provider.getSignatureHeader());
    }

    private static String computeSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
