package io.jenkins.plugins.cingrok;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

public class WebhookExcludeProperty extends JobProperty<Job<?, ?>> {

    private final boolean excludeFromWebhook;

    @DataBoundConstructor
    public WebhookExcludeProperty(boolean excludeFromWebhook) {
        this.excludeFromWebhook = excludeFromWebhook;
    }

    public boolean isExcludeFromWebhook() {
        return excludeFromWebhook;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "ci-ngrok Webhook";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public WebhookExcludeProperty newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            if (formData == null || formData.isNullObject()) {
                return new WebhookExcludeProperty(false);
            }
            boolean exclude = formData.optBoolean("excludeFromWebhook", false);
            return new WebhookExcludeProperty(exclude);
        }
    }
}
