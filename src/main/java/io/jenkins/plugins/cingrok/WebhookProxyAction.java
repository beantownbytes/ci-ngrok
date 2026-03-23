package io.jenkins.plugins.cingrok;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.UnprotectedRootAction;
import io.jenkins.plugins.cingrok.provider.WebhookProvider;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Extension
public class WebhookProxyAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebhookProxyAction.class.getName());

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "ci-ngrok";
    }

    public HttpResponse doWebhook(StaplerRequest2 request) throws IOException {
        String remoteAddr = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String deliveryId = request.getHeader("X-GitHub-Delivery");

        LOGGER.info("Webhook request from ip=" + remoteAddr
                + " ua=" + userAgent
                + " delivery=" + deliveryId);

        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        if (config == null || !config.isEnabled()) {
            LOGGER.warning("REJECTED ip=" + remoteAddr + " reason=disabled");
            return HttpResponses.text("ci-ngrok is not enabled");
        }

        String secret = config.resolveSecret(config.getWebhookSecretCredentialId());
        if (secret == null || secret.isEmpty()) {
            LOGGER.warning("REJECTED ip=" + remoteAddr + " reason=no_secret_configured");
            return HttpResponses.text("Webhook secret not configured");
        }

        byte[] body = request.getInputStream().readAllBytes();
        Map<String, String> headers = extractHeaders(request);

        String event = headers.getOrDefault("X-GitHub-Event",
                headers.getOrDefault("X-Gitlab-Event",
                        headers.getOrDefault("X-Gitea-Event", "")));

        if ("ping".equalsIgnoreCase(event)) {
            LOGGER.info("ACCEPTED ip=" + remoteAddr + " event=ping");
            return HttpResponses.text("pong");
        }

        WebhookProvider provider = detectProvider(headers);
        if (provider == null) {
            LOGGER.warning("REJECTED ip=" + remoteAddr + " reason=unsupported_provider ua=" + userAgent);
            return HttpResponses.text("Unsupported webhook source");
        }

        if (!provider.validate(headers, body, secret)) {
            LOGGER.warning("REJECTED ip=" + remoteAddr + " reason=invalid_signature provider=" + provider.getName() + " ua=" + userAgent);
            return HttpResponses.text("Invalid signature");
        }

        LOGGER.info("ACCEPTED ip=" + remoteAddr + " provider=" + provider.getName() + " event=" + event);

        String repoFullName = extractRepoFullName(body);
        if (repoFullName == null || repoFullName.isEmpty()) {
            LOGGER.warning("Could not extract repository name from payload");
            return HttpResponses.text("Could not determine repository");
        }

        List<String> triggered = triggerMatchingJobs(repoFullName);

        if (triggered.isEmpty()) {
            LOGGER.info("No jobs matched repository " + repoFullName);
            return HttpResponses.text("No matching jobs for " + repoFullName);
        }

        String result = String.join(",", triggered);
        LOGGER.info("Triggered jobs: " + result);
        return HttpResponses.text("Triggered: " + result);
    }

    private WebhookProvider detectProvider(Map<String, String> headers) {
        WebhookProvider best = null;
        int bestLength = 0;
        for (WebhookProvider provider : Jenkins.get().getExtensionList(WebhookProvider.class)) {
            String header = provider.getSignatureHeader();
            if (headers.containsKey(header) && header.length() > bestLength) {
                best = provider;
                bestLength = header.length();
            }
        }
        return best;
    }

    private Map<String, String> extractHeaders(StaplerRequest2 request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private String extractRepoFullName(byte[] body) {
        try {
            JsonObject payload = new Gson().fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject repo = payload.getAsJsonObject("repository");
            if (repo != null && repo.has("full_name")) {
                return repo.get("full_name").getAsString();
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to parse webhook payload: " + e.getMessage());
        }
        return null;
    }

    private List<String> triggerMatchingJobs(String repoFullName) {
        List<String> triggered = new ArrayList<>();
        Jenkins jenkins = Jenkins.get();

        LOGGER.info("Scanning jobs for repo " + repoFullName);

        try (var ignored = hudson.security.ACL.as2(hudson.security.ACL.SYSTEM2)) {
        for (var item : jenkins.getItems()) {
            if (!(item instanceof hudson.model.AbstractItem)) {
                continue;
            }
            try {
                hudson.model.AbstractItem abstractItem = (hudson.model.AbstractItem) item;
                String configXml = abstractItem.getConfigFile().asString();
                LOGGER.info("Checking " + item.getFullName() + ": match=" + configXml.contains(repoFullName));

                if (!configXml.contains(repoFullName)) {
                    continue;
                }

                if (item instanceof hudson.model.Job) {
                    hudson.model.Job<?, ?> jobItem = (hudson.model.Job<?, ?>) item;
                    WebhookExcludeProperty prop = jobItem.getProperty(WebhookExcludeProperty.class);
                    if (prop != null && prop.isExcludeFromWebhook()) {
                        LOGGER.info("Skipping " + item.getFullName() + " (excluded from webhook triggers)");
                        continue;
                    }
                }

                if (item instanceof jenkins.model.ParameterizedJobMixIn.ParameterizedJob) {
                    @SuppressWarnings("unchecked")
                    var job = (jenkins.model.ParameterizedJobMixIn.ParameterizedJob<?, ?>) item;
                    LOGGER.info("Triggering " + job.getFullName() + " for repo " + repoFullName);
                    job.scheduleBuild2(0, new hudson.model.CauseAction(new WebhookCause(repoFullName)));
                    triggered.add(job.getFullName());
                }
            } catch (Exception e) {
                LOGGER.warning("Error checking " + item.getFullName() + ": " + e.getMessage());
            }
        }

        } // end ACL.as2
        return triggered;
    }

    public static class WebhookCause extends Cause {
        private final String repoFullName;

        public WebhookCause(String repoFullName) {
            this.repoFullName = repoFullName;
        }

        @Override
        public String getShortDescription() {
            return "Triggered by webhook from " + repoFullName + " via ci-ngrok";
        }
    }
}
