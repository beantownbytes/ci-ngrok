package io.jenkins.plugins.cingrok;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Logger;

@Extension
public class CiNgrokGlobalConfiguration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(CiNgrokGlobalConfiguration.class.getName());

    private boolean enabled;
    private String ngrokAuthtokenCredentialId;
    private String webhookSecretCredentialId;

    public CiNgrokGlobalConfiguration() {
        load();
    }

    public static CiNgrokGlobalConfiguration get() {
        return GlobalConfiguration.all().get(CiNgrokGlobalConfiguration.class);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        boolean wasEnabled = this.enabled;
        req.bindJSON(this, json);
        save();

        if (this.enabled && !wasEnabled) {
            NgrokTunnelManager.get().start();
        } else if (!this.enabled && wasEnabled) {
            NgrokTunnelManager.get().stop();
        } else if (this.enabled) {
            NgrokTunnelManager.get().restart();
        }

        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNgrokAuthtokenCredentialId() {
        return ngrokAuthtokenCredentialId;
    }

    @DataBoundSetter
    public void setNgrokAuthtokenCredentialId(String ngrokAuthtokenCredentialId) {
        this.ngrokAuthtokenCredentialId = ngrokAuthtokenCredentialId;
    }

    public String getWebhookSecretCredentialId() {
        return webhookSecretCredentialId;
    }

    @DataBoundSetter
    public void setWebhookSecretCredentialId(String webhookSecretCredentialId) {
        this.webhookSecretCredentialId = webhookSecretCredentialId;
    }

    public String resolveSecret(String credentialId) {
        if (credentialId == null || credentialId.isEmpty()) {
            return null;
        }
        StringCredentials cred = CredentialsProvider.findCredentialById(
                credentialId, StringCredentials.class, null, Collections.emptyList());
        return cred != null ? cred.getSecret().getPlainText() : null;
    }

    public ListBoxModel doFillNgrokAuthtokenCredentialIdItems() {
        Jenkins jenkins = Jenkins.get();
        if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
            return new StandardListBoxModel();
        }
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM2, jenkins, StringCredentials.class);
    }

    public ListBoxModel doFillWebhookSecretCredentialIdItems() {
        Jenkins jenkins = Jenkins.get();
        if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
            return new StandardListBoxModel();
        }
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM2, jenkins, StringCredentials.class);
    }
}
