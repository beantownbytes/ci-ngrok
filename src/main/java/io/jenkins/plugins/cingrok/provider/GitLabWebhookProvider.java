package io.jenkins.plugins.cingrok.provider;

import hudson.Extension;
import java.util.Map;

@Extension
public class GitLabWebhookProvider implements WebhookProvider {

    private static final String HEADER = "X-Gitlab-Token";

    @Override
    public String getName() {
        return "GitLab";
    }

    @Override
    public String getSignatureHeader() {
        return HEADER;
    }

    @Override
    public boolean validate(Map<String, String> headers, byte[] body, String secret) {
        String token = headers.get(HEADER);
        if (token == null) {
            return false;
        }
        return WebhookProvider.constantTimeEquals(token, secret);
    }
}
