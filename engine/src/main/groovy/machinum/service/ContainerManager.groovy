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

@Slf4j
@CompileStatic
class ContainerManager {

    private final Map<String, BrowserInstance> activeInstances = new ConcurrentHashMap<>()
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor()
    private final AtomicInteger sessionCounter = new AtomicInteger(0)
    private final CacheMediator cacheMediator
    private final String recordingDirectory
    private final String reportDirectory

    ContainerManager(CacheMediator cacheMediator, String recordingDirectory, String reportDirectory) {
        this.cacheMediator = cacheMediator
        this.recordingDirectory = recordingDirectory
        this.reportDirectory = reportDirectory
    }

    void init() {
        // Cleanup inactive sessions every 5 minutes
        cleanupExecutor.scheduleAtFixedRate({ cleanupStaleInstances() }, 5, 5, TimeUnit.MINUTES)
        log.info("ContainerManagerService initialized")
    }

    String createInstance(ChromeConfig config) {
        config = config ?: ChromeConfig.builder().build()
        String sessionId = "session-${System.currentTimeMillis()}-${sessionCounter.incrementAndGet()}"

        try {
            log.info("Creating browser instance for session: {}", sessionId)
            BrowserInstance instance = new BrowserInstance(cacheMediator, sessionId, config.toBuilder()
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

    //TODO add ENV for default configuration of ChromeConfig
    BrowserInstance getLastInstance() {
        if (!activeInstances.isEmpty()) {
            return getInstance(activeInstances.keySet().last())
        } else {
            def id = createInstance(ChromeConfig.defaultOne())
            return getInstance(id)
        }
    }

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

    void terminateInstance(String sessionId) {
        BrowserInstance instance = activeInstances.remove(sessionId)
        if (instance) {
            log.info("Terminating browser instance: {}", sessionId)
            instance.cleanup()
        }
    }

    List<SessionInfo> getActiveSessions() {
        return activeInstances.values().collect { it.getSessionInfo() }
    }

    private void cleanupStaleInstances() {
        List<String> staleIds = activeInstances.entrySet()
                .findAll { !it.value.isAlive() }
                .collect { it.key }

        if (staleIds) {
            log.info("Cleaning up {} stale sessions: {}", staleIds.size(), staleIds)
            staleIds.each { terminateInstance(it) }
        }
    }

    void shutdown() {
        log.info("Shutting down ContainerManagerService")
        activeInstances.keySet().each { terminateInstance(it) }
        cleanupExecutor.shutdown()
        try {
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    ScenarioResult execute(Function<BrowserInstance, ScenarioResult> supplier) {
        var instance = getLastInstance()

        try {
            return supplier.apply(instance)
        } catch (SessionExpiredException ignore) {
            terminateInstance(instance.sessionId)

            def id = createInstance(instance.config)
            return supplier.apply(getInstance(id))
        }
    }
}
