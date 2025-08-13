package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.model.ChromeConfig;
import machinum.model.ScenarioResult;
import machinum.model.SessionInfo;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a remote browser instance managed by an external service.
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteBrowserInstance implements BrowserInstance {

    private final String sessionId;
    private final String remoteApiBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private SessionInfo lastKnownInfo;

    /**
     * Initializes the browser instance. In this case, it's a no-op as initialization is handled by the remote server.
     *
     * @return The current instance of RemoteBrowserInstance
     */
    @Override
    public BrowserInstance initialize() {
        return this;
    }

    /**
     * Executes a Groovy script on the remote browser instance.
     *
     * @param groovyScript   The Groovy script to execute.
     * @param params         Parameters for the script execution.
     * @param timeoutSeconds Timeout in seconds for the script execution.
     * @return Result of the executed scenario.
     */
    @Override
    public ScenarioResult executeScript(String groovyScript, Map<String, Object> params, int timeoutSeconds) {
        log.info("Executing remote script for session {}", sessionId);
        try {
            ScriptExecutionRequest payload = new ScriptExecutionRequest(groovyScript, params, timeoutSeconds);
            String requestBody = objectMapper.writeValueAsString(payload);

            URI uri = URI.create("%s/api/remote/sessions/%s/execute".formatted(remoteApiBaseUrl, sessionId));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug(">> POST {}", uri);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< POST {} {}", uri, response.statusCode());

            if (response.statusCode() != 200) {
                throw new AppException("Failed to execute remote script for session " + sessionId + ", status: " + response.statusCode() + ", body: " + response.body());
            }
            return objectMapper.readValue(response.body(), ScenarioResult.class);
        } catch (IOException | InterruptedException e) {
            log.error("Error executing remote script for session {}", sessionId, e);
            throw new AppException("Error executing remote script for session " + sessionId, e);
        }
    }

    /**
     * Checks if the browser instance is alive. In this proxy implementation, it's assumed to be alive
     * as long as the manager holds it.
     *
     * @return Always returns true.
     */
    @Override
    public boolean isAlive() {
        return true;
    }

    /**
     * Cleans up the browser instance. The actual cleanup action is triggered by RemoteContainerManager.terminateInstance.
     */
    @Override
    public void cleanup() {
        log.debug("Cleanup for remote instance {} called, but action is taken by manager", sessionId);
    }

    /**
     * Returns the session ID of the remote browser instance.
     *
     * @return The session ID.
     */
    @Override
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Retrieves the configuration for the Chrome browser used in this session.
     *
     * @return The ChromeConfig object.
     */
    @Override
    public ChromeConfig getConfig() {
        if (Objects.nonNull(lastKnownInfo)) {
            return lastKnownInfo.getConfig();
        }

        return getSessionInfo().getConfig();
    }

    /**
     * Fetches the current session information from the remote server.
     *
     * @return The SessionInfo object.
     */
    @Override
    public SessionInfo getSessionInfo() {
        log.debug("Fetching remote session info for {}", sessionId);
        try {
            URI uri = URI.create("%s/api/remote/sessions/%s".formatted(remoteApiBaseUrl, sessionId));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .GET()
                    .build();

            log.debug(">> GET {}", uri);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< GET {} {}", uri, response.statusCode());

            if (response.statusCode() != 200) {
                throw new AppException("Failed to get remote session info for " + sessionId + ", status: " + response.statusCode());
            }

            this.lastKnownInfo = objectMapper.readValue(response.body(), SessionInfo.class);
            return this.lastKnownInfo;
        } catch (IOException | InterruptedException e) {
            log.error("Error getting remote session info for {}", sessionId, e);
            throw new AppException("Error getting remote session info for " + sessionId, e);
        }
    }

    /**
     * Retrieves the WebDriver object. This operation is not supported in this proxy implementation.
     *
     * @return Throws UnsupportedOperationException as it's not possible to get a remote WebDriver directly from a proxy instance.
     */
    @Override
    public WebDriver getDriver() {
        throw new UnsupportedOperationException("Cannot get a remote WebDriver object directly from a proxy instance.");
    }

    /**
     * Represents the request payload for executing a script on the remote browser instance.
     */
    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class ScriptExecutionRequest {

        private String groovyScript;

        @ToString.Include
        private Map<String, Object> params;

        @ToString.Include
        private int timeoutSeconds;

    }

}