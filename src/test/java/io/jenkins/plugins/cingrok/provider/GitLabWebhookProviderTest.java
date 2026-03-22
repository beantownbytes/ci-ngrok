package io.jenkins.plugins.cingrok.provider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitLabWebhookProviderTest {

    private final GitLabWebhookProvider provider = new GitLabWebhookProvider();
    private static final String SECRET = "gitlab-webhook-token-xyz";

    @Test
    void validTokenAccepted() {
        byte[] body = "{\"event_type\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Gitlab-Token", SECRET);
        assertTrue(provider.validate(headers, body, SECRET));
    }

    @Test
    void wrongTokenRejected() {
        byte[] body = "{\"event_type\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Gitlab-Token", "wrong-token");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void missingHeaderRejected() {
        byte[] body = "{\"event_type\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of();
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void similarTokenRejected() {
        byte[] body = "{\"event_type\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Gitlab-Token", SECRET + "x");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void providerMetadata() {
        assertEquals("GitLab", provider.getName());
        assertEquals("X-Gitlab-Token", provider.getSignatureHeader());
    }
}
