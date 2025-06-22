package machinum.controller;

import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.ChromeConfig;
import machinum.model.ScenarioResult;
import machinum.model.SessionInfo;
import machinum.service.ContainerManagerService;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static machinum.Config.RECORDING_DIRECTORY_PARAM;

@Slf4j
@Path("/api")
@RequiredArgsConstructor
public class SessionController {

    private final ContainerManagerService containerManager;
    private final String recordingDirectory;


    @POST("/sessions")
    public SessionResponse createSession(ChromeConfig config, Context ctx) {
        log.info("Got request to create session: {}", config);
        var sessionId = containerManager.createInstance(config);
        ctx.setResponseCode(StatusCode.CREATED);
        return new SessionResponse(sessionId);
    }

    @GET("/sessions")
    public List<SessionInfo> listSessions() {
        return containerManager.getActiveSessions();
    }

    @DELETE("/sessions/{id}")
    public void deleteSession(@PathParam("id") String id, Context ctx) {
        containerManager.terminateInstance(id);
        ctx.setResponseCode(StatusCode.NO_CONTENT);
    }

    public static SessionController_ sessionController(Jooby application) {
        return new SessionController_(application.require(ContainerManagerService.class), application.getConfig().getString(RECORDING_DIRECTORY_PARAM));
    }

    private static File getLastModified(String directoryFilePath, String hash) {
        var directory = new File(directoryFilePath);
        var array = directory.listFiles(File::isFile);
        var files = Arrays.stream(Objects.requireNonNull(array))
                .filter(Objects::nonNull)
                .filter(file -> file.getName().contains(hash))
                .toArray(File[]::new);
        long lastModifiedTime = Long.MIN_VALUE;
        File chosenFile = null;

        for (var file : files) {
            if (file.lastModified() > lastModifiedTime) {
                chosenFile = file;
                lastModifiedTime = file.lastModified();
            }
        }

        return chosenFile;
    }

    @GET("/health")
    public HealthResponse health() {
        return new HealthResponse(containerManager.getActiveSessions().size());
    }

    @POST("/sessions/{id}/execute")
    public ScenarioResult startSession(@PathParam("id") String id, StartRequest request) {
        var instance = containerManager.getInstance(id);
        return instance.executeScript(request.script(), request.params(), request.timeout());
    }

    record SessionResponse(String id) {}

    record HealthResponse(int activeSessions) {}

    @GET("/video/{fileName}")
    public void startSession(@PathParam("fileName") String fileName, Context context) {
        File file = getLastModified(recordingDirectory, fileName);
        if (Objects.nonNull(file) && file.exists()) {
            context.send(file.toPath());
            return;
        }

        context.setResponseCode(404);
    }

    record StartRequest(String script, int timeout, Map<String, Object> params) {
    }

}
