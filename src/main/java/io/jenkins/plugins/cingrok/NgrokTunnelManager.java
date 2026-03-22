package io.jenkins.plugins.cingrok;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NgrokTunnelManager {

    private static final Logger LOGGER = Logger.getLogger(NgrokTunnelManager.class.getName());
    private static final String NGROK_API = "http://127.0.0.1:4040/api/tunnels";
    private static final String NGROK_DOWNLOAD_URL_LINUX = "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-amd64.tgz";
    private static final NgrokTunnelManager INSTANCE = new NgrokTunnelManager();

    private Process ngrokProcess;
    private String tunnelUrl;

    public static NgrokTunnelManager get() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (ngrokProcess != null && ngrokProcess.isAlive()) {
            LOGGER.info("ngrok tunnel already running");
            return;
        }

        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        if (config == null || !config.isEnabled()) {
            return;
        }

        String authtoken = config.resolveSecret(config.getNgrokAuthtokenCredentialId());
        if (authtoken == null || authtoken.isEmpty()) {
            LOGGER.warning("ngrok authtoken not configured");
            return;
        }

        try {
            Path ngrokBinary = ensureNgrokBinary();
            configureAuthtoken(ngrokBinary, authtoken);

            int port = 8080;
            String rootUrl = Jenkins.get().getRootUrl();
            if (rootUrl != null && rootUrl.contains(":")) {
                try {
                    port = Integer.parseInt(rootUrl.replaceAll(".*:(\\d+).*", "$1"));
                } catch (NumberFormatException ignored) {
                }
            }

            ProcessBuilder pb = new ProcessBuilder(
                    ngrokBinary.toString(), "http", String.valueOf(port),
                    "--log", "stdout", "--log-format", "json"
            );
            pb.redirectErrorStream(true);
            ngrokProcess = pb.start();

            drainProcessOutput(ngrokProcess);

            Thread.sleep(3000);
            tunnelUrl = fetchTunnelUrl();

            if (tunnelUrl != null) {
                LOGGER.info("ngrok tunnel started: " + tunnelUrl);
            } else {
                LOGGER.warning("ngrok started but tunnel URL not available yet");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start ngrok tunnel", e);
        }
    }

    public synchronized void stop() {
        if (ngrokProcess != null && ngrokProcess.isAlive()) {
            ngrokProcess.destroy();
            try {
                ngrokProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ngrokProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            LOGGER.info("ngrok tunnel stopped");
        }
        ngrokProcess = null;
        tunnelUrl = null;
    }

    public synchronized void restart() {
        stop();
        start();
    }

    public String getTunnelUrl() {
        return tunnelUrl;
    }

    public boolean isRunning() {
        if (ngrokProcess != null && ngrokProcess.isAlive()) {
            return true;
        }
        return fetchTunnelUrl() != null;
    }

    public synchronized void ensureRunning() {
        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        if (config == null || !config.isEnabled()) {
            return;
        }
        String currentUrl = fetchTunnelUrl();
        if (currentUrl != null) {
            tunnelUrl = currentUrl;
            return;
        }
        LOGGER.info("ngrok tunnel not responding, restarting");
        start();
    }

    private Path ensureNgrokBinary() throws IOException, InterruptedException {
        Path jenkinsHome = Jenkins.get().getRootDir().toPath();
        Path ngrokDir = jenkinsHome.resolve("ci-ngrok");
        Path ngrokBinary = ngrokDir.resolve("ngrok");

        if (Files.exists(ngrokBinary)) {
            return ngrokBinary;
        }

        Files.createDirectories(ngrokDir);
        LOGGER.info("Downloading ngrok binary...");

        Path tgz = ngrokDir.resolve("ngrok.tgz");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NGROK_DOWNLOAD_URL_LINUX))
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tgz));

        ProcessBuilder extract = new ProcessBuilder("tar", "xzf", tgz.toString(), "-C", ngrokDir.toString());
        Process p = extract.start();
        p.waitFor(30, TimeUnit.SECONDS);
        Files.deleteIfExists(tgz);

        Files.setPosixFilePermissions(ngrokBinary, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        ));

        LOGGER.info("ngrok binary installed at " + ngrokBinary);
        return ngrokBinary;
    }

    private void configureAuthtoken(Path ngrokBinary, String authtoken)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                ngrokBinary.toString(), "config", "add-authtoken", authtoken
        );
        Process p = pb.start();
        p.waitFor(10, TimeUnit.SECONDS);
    }

    private String fetchTunnelUrl() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NGROK_API))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonObject json = new Gson().fromJson(response.body(), JsonObject.class);
            JsonArray tunnels = json.getAsJsonArray("tunnels");
            for (JsonElement t : tunnels) {
                String url = t.getAsJsonObject().get("public_url").getAsString();
                if (url.startsWith("https://")) {
                    return url;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not fetch tunnel URL", e);
        }
        return null;
    }

    private void drainProcessOutput(Process process) {
        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.fine("ngrok: " + line);
                }
            } catch (IOException ignored) {
            }
        }, "ngrok-output-drain");
        drainer.setDaemon(true);
        drainer.start();
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void onStartup() {
        CiNgrokGlobalConfiguration config = CiNgrokGlobalConfiguration.get();
        if (config != null && config.isEnabled()) {
            NgrokTunnelManager.get().start();
        }
    }

    @Extension
    public static class HealthCheck extends AsyncPeriodicWork {

        public HealthCheck() {
            super("ci-ngrok-health-check");
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(60);
        }

        @Override
        protected void execute(TaskListener listener) {
            NgrokTunnelManager.get().ensureRunning();
        }
    }
}
