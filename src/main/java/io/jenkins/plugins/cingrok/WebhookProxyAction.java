package io.jenkins.plugins.cingrok;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import io.jenkins.plugins.cingrok.provider.WebhookProvider;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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

    public HttpResponse doWebhook(StaplerRequest2 request) throws IOException, ServletException {
        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        if (config == null || !config.isEnabled()) {
            LOGGER.warning("Webhook received but plugin is disabled");
            return HttpResponses.error(503, "ci-ngrok is not enabled");
        }

        String secret = config.resolveSecret(config.getWebhookSecretCredentialId());
        if (secret == null || secret.isEmpty()) {
            LOGGER.warning("Webhook secret not configured");
            return HttpResponses.error(500, "Webhook secret not configured");
        }

        byte[] body = request.getInputStream().readAllBytes();
        Map<String, String> headers = extractHeaders(request);

        WebhookProvider provider = detectProvider(headers);
        if (provider == null) {
            LOGGER.warning("No matching webhook provider found for request headers");
            return HttpResponses.error(400, "Unsupported webhook source");
        }

        if (!provider.validate(headers, body, secret)) {
            LOGGER.warning("Invalid " + provider.getName() + " webhook signature");
            return HttpResponses.error(403, "Invalid signature");
        }

        LOGGER.info("Valid " + provider.getName() + " webhook received, forwarding to Generic Webhook Trigger");

        return forwardToGenericWebhookTrigger(request, body, headers);
    }

    private WebhookProvider detectProvider(Map<String, String> headers) {
        for (WebhookProvider provider : Jenkins.get().getExtensionList(WebhookProvider.class)) {
            if (headers.containsKey(provider.getSignatureHeader())) {
                return provider;
            }
        }
        return null;
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

    private HttpResponse forwardToGenericWebhookTrigger(
            StaplerRequest2 request, byte[] body, Map<String, String> headers)
            throws IOException, ServletException {

        String queryString = request.getQueryString();
        String forwardPath = "/generic-webhook-trigger/invoke";
        if (queryString != null && !queryString.isEmpty()) {
            forwardPath += "?" + queryString;
        }

        final String path = forwardPath;
        request.setAttribute("ci-ngrok.forwarded-body", body);

        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node)
                    throws IOException, ServletException {
                try {
                    Jenkins.get().getServletContext()
                            .getRequestDispatcher(path)
                            .forward(req, rsp);
                } catch (Exception e) {
                    LOGGER.severe("Failed to forward to Generic Webhook Trigger: " + e.getMessage());
                    rsp.setStatus(502);
                    rsp.getWriter().write("Failed to forward webhook");
                }
            }
        };
    }
}
