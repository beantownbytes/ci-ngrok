package io.jenkins.plugins.cingrok.provider;

import hudson.Extension;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@Extension
public class BitbucketWebhookProvider implements WebhookProvider {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER = "X-Hub-Signature";

    @Override
    public String getName() {
        return "Bitbucket";
    }

    @Override
    public String getSignatureHeader() {
        return HEADER;
    }

    @Override
    public boolean validate(Map<String, String> headers, byte[] body, String secret) {
        String signature = headers.get(HEADER);
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(body);
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return WebhookProvider.constantTimeEquals(expected, signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }
}
