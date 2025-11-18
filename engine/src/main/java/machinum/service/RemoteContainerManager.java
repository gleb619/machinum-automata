package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.model.ChromeConfig;
import machinum.model.ScenarioResult;
import machinum.model.SessionInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Manages browser instances remotely.
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteContainerManager implements ContainerManager {

    public static final int MAX_RETRIES = 100;
    public static final double DEFAULT_BACKOFF = 1.5;

    private final String remoteApiBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Initializes the RemoteContainerManager.
     */
    @Override
    public void init() {
        log.info("RemoteContainerManager initialized for remote API: {}", remoteApiBaseUrl);
        pingRemoteServer();
    }

    /**
     * Creates a new browser instance with the given configuration.
     *
     * @param config The ChromeConfig to use for creating the instance.
     * @return The session ID of the created instance.
     */
    @Override
    public String createInstance(ChromeConfig config) {
        try {
            String requestBody = objectMapper.writeValueAsString(config == null ? ChromeConfig.builder().build() : config);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/remote/sessions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug(">> POST {}", remoteApiBaseUrl + "/api/remote/sessions");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< POST {} {}", remoteApiBaseUrl + "/api/remote/sessions", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("Got error on instance creation from remote: {}", response.body());
                throw new AppException("Failed to create remote instance, status: " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            log.error("Error creating remote instance", e);
            throw new AppException("Error creating remote instance", e);
        }
    }

    /**
     * Terminates the browser instance with the given session ID.
     *
     * @param sessionId The ID of the session to terminate.
     */
    @Override
    public void terminateInstance(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/remote/sessions/" + sessionId))
                    .DELETE()
                    .build();

            log.debug(">> DELETE {}", remoteApiBaseUrl + "/api/remote/sessions/" + sessionId);
            HttpResponse<Void> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .get(30, TimeUnit.SECONDS);
            log.debug("<< DELETE {} {}", remoteApiBaseUrl + "/api/remote/sessions/" + sessionId, response.statusCode());

            if (response.statusCode() != 204) {
                log.warn("Failed to terminate remote instance {}, status: {}, body: {}", sessionId, response.statusCode(), response.body());
            }
        } catch (TimeoutException e) {
            log.warn("Timeout, can't terminate remote instance %s, omit proceedings".formatted(sessionId), e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error terminating remote instance %s".formatted(sessionId), e);
            throw new AppException("Error terminating remote instance", e);
        }
    }

    /**
     * Retrieves a list of active browser sessions.
     *
     * @return A list of SessionInfo objects representing the active sessions.
     */
    @Override
    public List<SessionInfo> getActiveSessions() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/remote/sessions"))
                    .GET()
                    .build();

            log.debug(">> GET {}", remoteApiBaseUrl + "/api/remote/sessions");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< GET {} {}", remoteApiBaseUrl + "/api/remote/sessions", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("Got error response from remote: {}", response.body());
                throw new AppException("Failed to get active sessions, status: " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (IOException | InterruptedException e) {
            log.error("Error getting active sessions", e);
            throw new AppException("Error getting active sessions", e);
        }
    }

    /**
     * Shuts down the RemoteContainerManager.
     */
    @Override
    public void shutdown() {
        log.info("Shutting down RemoteContainerManager. No remote action taken.");
        // No-op for remote client
    }

    /**
     * Creates a proxy for the browser instance with the given session ID.
     *
     * @param sessionId The ID of the session to create a proxy for.
     * @return A BrowserInstance object representing the remote instance.
     */
    @Override
    public BrowserInstance getInstance(String sessionId) {
        log.debug("Creating remote instance proxy for session: {}", sessionId);
        return new RemoteBrowserInstance(sessionId, remoteApiBaseUrl, httpClient, objectMapper);
    }

    /**
     * Retrieves the last active browser instance.
     *
     * @return A BrowserInstance object representing the most recently created active session.
     */
    @Override
    public BrowserInstance getLastInstance() {
        log.debug("Fetching last active remote instance");
        List<SessionInfo> sessions = getActiveSessions();
        if (sessions.isEmpty()) {
            log.info("No active remote sessions found, creating a new one.");
            String newId = createInstance(ChromeConfig.defaultOne());
            return getInstance(newId);
        }
        // Return the most recently created one
        return sessions.stream()
                .max(Comparator.comparing(SessionInfo::getCreatedAt))
                .map(info -> getInstance(info.getId()))
                .orElseThrow(() -> new AppException("Could not determine last instance despite having active sessions."));
    }

    /**
     * Executes a scenario using the last active browser instance.
     *
     * @param scenario The function representing the scenario to execute.
     * @return A ScenarioResult object containing the result of the executed scenario.
     */
    @Override
    public ScenarioResult execute(Function<BrowserInstance, ScenarioResult> scenario) {
        var instance = getLastInstance();
        var instanceConfig = instance.getConfig();
        AtomicInteger attempt = new AtomicInteger(0);

        while (attempt.get() < MAX_RETRIES) {
            log.debug("Remote execute try No: {}/{}", attempt.get() + 1, MAX_RETRIES);
            try {
                // The function now directly calls the remote execution method
                var result = scenario.apply(instance);

                if (!result.isSuccess()) {
                    log.debug("Remote execution failed, trying to execute a new one...");
                    instance = processFallback(new NullPointerException(), instance, attempt, instanceConfig);
                    return scenario.apply(instance);
                }

                return result;
            } catch (AppException e) {
                instance = processFallback(e, instance, attempt, instanceConfig);
            }
        }

        throw new AppException("Max retries reached, unable to execute remote scenario");
    }

    private BrowserInstance processFallback(Exception e, BrowserInstance instance, AtomicInteger attempt, ChromeConfig instanceConfig) {
        // A generic app exception could mean the session is gone.
        log.warn("Execution failed for remote session {}, attempting to recreate. Error: {}", instance.getSessionId(), e.getMessage());
        try {
            terminateInstance(instance.getSessionId()); // Attempt to clean up the old one
        } catch (Exception ex) {
            log.warn("Can't stop remote instance: ", e);
        }
        try {
            long millis = (long) (Math.pow(DEFAULT_BACKOFF, attempt.get()) * 1000);
            log.debug("Will wait next {} seconds", millis / 1000);
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during backoff", ie);
        }
        log.debug("Trying again...");
        String newId = createInstance(instanceConfig); // Recreate with old config
        var newInstance = getInstance(newId);
        attempt.getAndIncrement();

        return newInstance;
    }

    private void pingRemoteServer() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            log.debug("Pinging remote server at {}", remoteApiBaseUrl + "/health");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AppException("Remote server ping failed with status: " + response.statusCode());
            }

            var jsonMap = objectMapper.readValue(response.body(), Map.class);
            if (!Boolean.TRUE.equals(jsonMap.get("success"))) {
                throw new AppException("Remote server ping failed: health check returned false");
            }

            log.info("Remote server ping successful at {}", remoteApiBaseUrl);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to ping remote server", e);
            throw new AppException("Unable to ping remote server at %s/health".formatted(remoteApiBaseUrl), e);
        }
    }

}
