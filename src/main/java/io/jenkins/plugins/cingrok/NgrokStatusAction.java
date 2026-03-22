package io.jenkins.plugins.cingrok;

import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;

@Extension
public class NgrokStatusAction implements RootAction {

    @Override
    public String getIconFileName() {
        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        if (config != null && config.isEnabled()) {
            return "symbol-cloud";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return "ngrok Tunnel";
    }

    @Override
    public String getUrlName() {
        return "ci-ngrok-status";
    }

    public boolean isEnabled() {
        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        return config != null && config.isEnabled();
    }

    public boolean isRunning() {
        return NgrokTunnelManager.get().isRunning();
    }

    public String getTunnelUrl() {
        return NgrokTunnelManager.get().getTunnelUrl();
    }

    public String getWebhookUrl() {
        String tunnel = getTunnelUrl();
        if (tunnel == null) {
            return null;
        }
        return tunnel + "/ci-ngrok/webhook";
    }
}
