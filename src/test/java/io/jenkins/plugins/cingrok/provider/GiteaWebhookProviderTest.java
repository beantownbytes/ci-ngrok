package io.jenkins.plugins.cingrok.provider;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GiteaWebhookProviderTest {

    private final GiteaWebhookProvider provider = new GiteaWebhookProvider();
    private static final String SECRET = "gitea-secret-456";

    @Test
    void validSignatureAccepted() throws Exception {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeHmac(body, SECRET);

        Map<String, String> headers = Map.of("X-Gitea-Signature", signature);
        assertTrue(provider.validate(headers, body, SECRET));
    }

    @Test
    void invalidSignatureRejected() {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of("X-Gitea-Signature", "0000000000000000000000000000000000000000000000000000000000000000");
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void missingHeaderRejected() {
        byte[] body = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = Map.of();
        assertFalse(provider.validate(headers, body, SECRET));
    }

    @Test
    void tamperedBodyRejected() throws Exception {
        byte[] originalBody = "{\"action\":\"push\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"action\":\"delete\"}".getBytes(StandardCharsets.UTF_8);
        String signature = computeHmac(originalBody, SECRET);

        Map<String, String> headers = Map.of("X-Gitea-Signature", signature);
        assertFalse(provider.validate(headers, tamperedBody, SECRET));
    }

    @Test
    void providerMetadata() {
        assertEquals("Gitea", provider.getName());
        assertEquals("X-Gitea-Signature", provider.getSignatureHeader());
    }

    private static String computeHmac(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
