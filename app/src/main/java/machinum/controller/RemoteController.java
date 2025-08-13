package machinum.controller;

import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.SessionExpiredException;
import machinum.model.ChromeConfig;
import machinum.model.ScenarioResult;
import machinum.model.SessionInfo;
import machinum.service.BrowserInstance;
import machinum.service.ContainerManager;
import machinum.service.RemoteBrowserInstance.ScriptExecutionRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Controller for managing container sessions and script execution.
 */
@Slf4j
@Path("/api/remote")
@RequiredArgsConstructor
public class RemoteController {

    private final ContainerManager containerManager;

    /**
     * Creates a new instance of the ContainerController.
     *
     * @param application The Jooby application to use for dependency injection.
     * @return A new {@link RemoteController} object.
     */
    public static RemoteController_ remoteController(Jooby application) {
        return new RemoteController_(application.require(ContainerManager.class));
    }

    /**
     * Creates a new instance of the container with the specified configuration.
     *
     * @param config The Chrome configuration to use for creating the instance.
     * @return A string representing the session ID of the created instance.
     */
    @POST("/sessions")
    public String createInstance(ChromeConfig config) {
        log.info("Creating new container instance with config: {}", config);
        return containerManager.createInstance(config);
    }

    /**
     * Retrieves a list of active sessions.
     *
     * @param id  The session ID (not used in this method).
     * @param ctx The Jooby context.
     * @return A list of {@link SessionInfo} objects representing the active sessions.
     */
    @GET("/sessions")
    public List<SessionInfo> getActiveSessions(@PathParam("id") String id, Context ctx) {
        log.info("Retrieving all active sessions");
        return containerManager.getActiveSessions();
    }

    /**
     * Retrieves information about a specific session by its ID.
     *
     * @param id  The session ID to retrieve information for.
     * @param ctx The Jooby context.
     * @return A {@link SessionInfo} object representing the specified session.
     */
    @GET("/sessions/{id}")
    public SessionInfo getSessionInfo(@PathParam("id") String id, Context ctx) {
        log.info("Retrieving session info for ID: {}", id);
        return containerManager.getInstance(id).getSessionInfo();
    }

    /**
     * Terminates a specific session by its ID.
     *
     * @param id  The session ID to terminate.
     * @param ctx The Jooby context.
     * @return A {@link Context} object with the response code set to 204 (No Content).
     */
    @DELETE("/sessions/{id}")
    public Context terminateInstance(@PathParam("id") String id, Context ctx) {
        log.info("Terminating session with ID: {}", id);
        containerManager.terminateInstance(id);
        return ctx.setResponseCode(204);
    }

    /**
     * Executes a script in the specified session.
     *
     * @param id      The session ID to execute the script in.
     * @param request The script execution request containing the Groovy script, parameters, and timeout.
     * @return A {@link ScenarioResult} object representing the result of the script execution.
     */
    @POST("/sessions/{id}/execute")
    public ScenarioResult executeScript(@PathParam("id") String id, ScriptExecutionRequest request) {
        log.info("Executing script in session ID: {} with request: {}", id, request);
        var instance = containerManager.getInstance(id);

        try {
            var result = instance.executeScript(request.getGroovyScript(), request.getParams(), request.getTimeoutSeconds());

            if (!result.isSuccess()) {
                log.debug("Local execution failed, trying to execute a new one...");
                var newInstance = processFallback(id, instance);
                return newInstance.executeScript(request.getGroovyScript(), request.getParams(), request.getTimeoutSeconds());
            }

            return result;
        } catch (SessionExpiredException e) {
            var newInstance = processFallback(id, instance);
            return newInstance.executeScript(request.getGroovyScript(), request.getParams(), request.getTimeoutSeconds());
        }
    }

    /**
     * Processes a fallback scenario when the original session has expired.
     *
     * @param id       The session ID that has expired.
     * @param instance The original browser instance that has expired.
     * @return A new {@link BrowserInstance} object representing the recreated session.
     */
    private BrowserInstance processFallback(String id, BrowserInstance instance) {
        log.warn("Session expired for ID: {}. Creating a new session.", id);
        terminateInstanceQuietly(instance);

        var newSessionId = containerManager.createInstance(instance.getConfig());
        return containerManager.getInstance(newSessionId);
    }

    /**
     * Terminates an instance quietly without throwing exceptions.
     *
     * @param instance The browser instance to terminate.
     */
    private void terminateInstanceQuietly(BrowserInstance instance) {
        try {
            CompletableFuture.runAsync(() -> containerManager.terminateInstance(instance.getSessionId())).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            log.warn("Error terminating local instance %s, omit proceedings".formatted(instance.getSessionId()), e);
        }
    }

}