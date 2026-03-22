package io.jenkins.plugins.cingrok.provider;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitHubWebhookProviderTest {

    private final GitHubWebhookProvider provider = new GitHubWebhookProvider();
    private static final String SECRET = "test-webhook-secret-12345";

    @Test
    void validSignatureAccepted() throws Exception {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeGitHubSignature(body, SECRET);

        Map<String, String> headers = Map.of("X-Hub-Signature-256", signature);
        assertTrue(provider.validate(headers, body, SECRET));
    }

    @Test
    void invalidSignatureRejected() {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void missingHeaderRejected() {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of();
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void wrongPrefixRejected() {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Hub-Signature-256", "sha1=abc123");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void wrongSecretRejected() throws Exception {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeGitHubSignature(body, "wrong-secret");

        Map<String, String> headers = Map.of("X-Hub-Signature-256", signature);
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void tamperedBodyRejected() throws Exception {
        byte[] originalBody = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"action\":\"delete\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeGitHubSignature(originalBody, SECRET);

        Map<String, String> headers = Map.of("X-Hub-Signature-256", signature);
        assertFalse(provider.validate(headers, tamperedBody, SECRET));
    }

    @Test
    void emptyBodyValidatesCorrectly() throws Exception {
        byte[] body = new byte[0];
        String signature = computeGitHubSignature(body, SECRET);

        Map<String, String> headers = Map.of("X-Hub-Signature-256", signature);
        assertTrue(provider.validate(headers, body, SECRET));
    }

    @Test
    void providerMetadata() {
        assertEquals("GitHub", provider.getName());
        assertEquals("X-Hub-Signature-256", provider.getSignatureHeader());
    }

    private static String computeGitHubSignature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
