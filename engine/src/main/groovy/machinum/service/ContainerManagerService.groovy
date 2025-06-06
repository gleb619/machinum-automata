package machinum.service

import machinum.exception.SessionExpiredException
import machinum.exception.SessionNotFoundException
import machinum.model.ChromeConfig
import machinum.model.SessionInfo
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@CompileStatic
class ContainerManagerService {

    private final Map<String, BrowserInstance> activeInstances = new ConcurrentHashMap<>()
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor()
    private final AtomicInteger sessionCounter = new AtomicInteger(0)

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
            BrowserInstance instance = new BrowserInstance(sessionId, config)
            instance.initialize()
            activeInstances.put(sessionId, instance)
            log.info("Browser instance created successfully: {}", sessionId)
            return sessionId
        } catch (Exception e) {
            log.error("Failed to create browser instance for session: {}", sessionId, e)
            throw new RuntimeException("Failed to create browser instance: ${e.message}", e)
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

    int getActiveSessionCount() {
        return activeInstances.size()
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

}
