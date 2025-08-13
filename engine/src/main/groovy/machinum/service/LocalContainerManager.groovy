package machinum.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import machinum.exception.AppException
import machinum.exception.SessionExpiredException
import machinum.exception.SessionNotFoundException
import machinum.model.ChromeConfig
import machinum.model.ScenarioResult
import machinum.model.SessionInfo

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Manages local browser instances for automated testing scenarios.
 */
@Slf4j
@CompileStatic
class LocalContainerManager implements ContainerManager {

    public static final int MAX_RETRIES = 100
    public static final double DEFAULT_BACKOFF = 1.5

    private final Map<String, BrowserInstance> activeInstances = new ConcurrentHashMap<>()
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor()
    private final AtomicInteger sessionCounter = new AtomicInteger(0)
    private final CacheMediator cacheMediator
    private final String recordingDirectory
    private final String reportDirectory

    /**
     * Constructs a LocalContainerManager with the specified dependencies and directories.
     *
     * @param cacheMediator The mediator for caching operations.
     * @param recordingDirectory Directory where recordings are stored.
     * @param reportDirectory Directory where reports are generated.
     */
    LocalContainerManager(CacheMediator cacheMediator, String recordingDirectory, String reportDirectory) {
        this.cacheMediator = cacheMediator
        this.recordingDirectory = recordingDirectory
        this.reportDirectory = reportDirectory
    }

    /**
     * Initializes the container manager by scheduling periodic cleanup of stale instances.
     */
    @Override
    void init() {
        // Cleanup inactive sessions every 5 minutes
        cleanupExecutor.scheduleAtFixedRate({ cleanupStaleInstances() }, 5, 5, TimeUnit.MINUTES)
        log.info("ContainerManagerService initialized")
    }

    /**
     * Creates a new browser instance with the specified configuration.
     *
     * @param config Configuration for the Chrome browser. Defaults to an empty configuration if null.
     * @return The session ID of the created browser instance.
     */
    @Override
    String createInstance(ChromeConfig config) {
        config = config ?: ChromeConfig.builder().build()
        String sessionId = "session-${System.currentTimeMillis()}-${sessionCounter.incrementAndGet()}"

        try {
            log.info("Creating browser instance for session: {}", sessionId)
            BrowserInstance instance = new LocalBrowserInstance(cacheMediator, sessionId, config.toBuilder()
                    .recordingDirectory(recordingDirectory)
                    .reportDirectory(reportDirectory)
                    .build())
                    .initialize()
            activeInstances.put(sessionId, instance)
            log.info("Browser instance created successfully: {}", sessionId)
            return sessionId
        } catch (Exception e) {
            log.error("Failed to create browser instance for session: {}", sessionId, e)
            throw new AppException("Failed to create browser instance: ${e.message}", e)
        }
    }

    /**
     * Retrieves the last active browser instance or creates a new one if none are available.
     *
     * @return The last active browser instance or a newly created one.
     */
    @Override
    BrowserInstance getLastInstance() {
        if (!activeInstances.isEmpty()) {
            return getInstance(activeInstances.keySet().last())
        } else {
            def id = createInstance(ChromeConfig.defaultOne())
            return getInstance(id)
        }
    }

    /**
     * Retrieves the browser instance associated with the specified session ID.
     *
     * @param sessionId The ID of the session to retrieve.
     * @return The browser instance for the given session ID.
     * @throws SessionNotFoundException If no active instance is found for the session ID.
     * @throws SessionExpiredException If the instance is not alive.
     */
    @Override
    BrowserInstance getInstance(String sessionId) {
        BrowserInstance instance = activeInstances.get(sessionId)
        if (!instance) {
            throw new SessionNotFoundException(sessionId)
        }
        if (!instance.isAlive()) {
            activeInstances.remove(sessionId)
            throw new SessionExpiredException(sessionId)
        }
        return instance
    }

    /**
     * Terminates the browser instance associated with the specified session ID.
     *
     * @param sessionId The ID of the session to terminate.
     */
    @Override
    void terminateInstance(String sessionId) {
        BrowserInstance instance = activeInstances.remove(sessionId)
        if (instance) {
            log.info("Terminating browser instance: {}", sessionId)
            instance.cleanup()
        }
    }

    /**
     * Retrieves a list of all active sessions.
     *
     * @return A list of session information for all active instances.
     */
    @Override
    List<SessionInfo> getActiveSessions() {
        return activeInstances.values().collect { it.getSessionInfo() }
    }

    /**
     * Shuts down the container manager and cleans up resources.
     */
    @Override
    void shutdown() {
        log.info("Shutting down ContainerManagerService")
        activeInstances.keySet().each { terminateInstance(it) }
        cleanupExecutor.shutdown()
        try {
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
        } catch (InterruptedException ignored) {
            cleanupExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Executes a scenario using the last active browser instance or creates a new one if necessary.
     *
     * @param supplier A function that takes a BrowserInstance and returns a ScenarioResult.
     * @return The result of the executed scenario.
     */
    @Override
    ScenarioResult execute(Function<BrowserInstance, ScenarioResult> supplier) {
        var instance = getLastInstance()
        int attempt = 0

        while (attempt < MAX_RETRIES) {
            log.debug("Execute try №: {}", attempt + 1)
            try {
                return supplier.apply(instance)
            } catch (SessionExpiredException ignore) {
                terminateInstance(instance.sessionId)

                try {
                    log.debug("Sleep...")
                    Thread.sleep((long) (Math.pow(DEFAULT_BACKOFF, attempt) * 1000))
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt()
                    throw new RuntimeException("Thread interrupted during backoff", e)
                }

                def id = createInstance(instance.config)
                instance = getInstance(id)
                attempt++
            }
        }

        throw new AppException("Max retries reached, unable to execute scenario")
    }

    /**
     * Cleans up stale browser instances that are no longer alive.
     */
    private void cleanupStaleInstances() {
        List<String> staleIds = activeInstances.entrySet()
                .findAll { !it.value.isAlive() }
                .collect { it.key }

        if (staleIds) {
            log.info("Cleaning up {} stale sessions: {}", staleIds.size(), staleIds)
            staleIds.each { terminateInstance(it) }
        }
    }

}