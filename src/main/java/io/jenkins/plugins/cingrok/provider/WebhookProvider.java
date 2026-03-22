package io.jenkins.plugins.cingrok.provider;

import hudson.ExtensionPoint;
import java.util.Map;

public interface WebhookProvider extends ExtensionPoint {

    String getName();

    String getSignatureHeader();

    boolean validate(Map<String, String> headers, byte[] body, String secret);

    static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
