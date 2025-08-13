package machinum.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.type.MapType
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

/**
 * A cache mediator providing a simple interface for caching operations
 * with ConcurrentHashMap storage and optional filesystem persistence.
 *
 * This version is refactored to use composition over traits, delegating
 * responsibilities to dedicated, static inner helper classes for improved
 * separation of concerns (SRP) and clarity.
 */
@Slf4j
@CompileStatic
class CacheMediator implements Closeable {

    private final Config config
    private final ConcurrentHashMap<String, CacheEntry> cache
    private final PersistenceManager persistenceManager
    private final ScheduledExecutorService scheduler
    private final ExecutorService persistenceExecutor
    private final Lock cleanupLock = new ReentrantLock()

    /**
     * Factory method to create and initialize a CacheMediator instance.
     * @param options A map of configuration options.
     * - defaultTtl (Duration): Default time-to-live. Default: 7 days.
     * - maxSize (long): Maximum number of entries. Default: 10,000.
     * - persistenceDir (String): Path for persistence. If null, persistence is disabled.
     * - asyncPersistence (boolean): Run persistence in a background thread. Default: true.
     * - cleanupInterval (Duration): How often to run cleanup. Default: 1 hour.
     * @return An initialized CacheMediator instance.
     */
    static CacheMediator create(Map<String, ?> options = [:]) {
        def config = new Config(options)
        return new CacheMediator(config).init()
    }

    private CacheMediator(Config config) {
        this.config = config
        this.cache = new ConcurrentHashMap<>()
        this.scheduler = Executors.newSingleThreadScheduledExecutor({ r -> new Thread(r, "cache-cleanup-scheduler") })

        if (config.persistenceDir) {
            this.persistenceManager = new PersistenceManager(config.persistenceDir)
            this.persistenceExecutor = Executors.newSingleThreadExecutor({ r -> new Thread(r, "cache-persistence-executor") })
        } else {
            this.persistenceManager = null
            this.persistenceExecutor = null
        }
    }

    CacheMediator init() {
        log.info("Initializing CacheMediator. Config: {}", config)
        if (persistenceManager) {
            try {
                Files.createDirectories(config.persistenceDir)
            } catch (IOException e) {
                throw new CacheMediationException("Failed to create persistence directory: ${config.persistenceDir}", e)
            }
        }
        scheduler.scheduleAtFixedRate(
                { Cleaner.run(cache, config.maxSize, cleanupLock) },
                config.cleanupInterval.toMillis(),
                config.cleanupInterval.toMillis(),
                TimeUnit.MILLISECONDS
        )
        log.info("CacheMediator initialized.")
        return this
    }

    Object get(String key, Closure<?> fallback) {
        try {
            // 1. Check for a valid entry in memory
            CacheEntry entry = getValidEntry(key)
            if (entry) {
                return entry.value
            }

            // 2. Entry not in memory or expired, try loading from disk (if configured)
            // This I/O happens outside any cache-level lock.
            if (persistenceManager) {
                entry = persistenceManager.load(key)
                if (entry) {
                    // Loaded from disk, put it into the cache. Another thread might beat us, which is fine.
                    cache.put(key, entry)
                    return entry.value
                }
            }

            // 3. Not in memory or disk, compute the value using the fallback
            // computeIfAbsent ensures the fallback is only executed once per key concurrently.
            def computedEntry = cache.computeIfAbsent(key, { k ->
                def value = fallback.call()
                if (ValueValidator.isInvalid(value)) {
                    log.debug("Fallback for key '{}' returned an empty value. Not caching.", k)
                    return null // Returning null removes the key from the map atomically.
                }
                log.debug("Caching new value for key '{}'", k)
                return new CacheEntry(value: value, lastAccessTime: Instant.now(), ttl: config.defaultTtl)
            })

            // If the entry was created, trigger persistence
            if (computedEntry != null && cache.containsKey(key)) {
                schedulePersistence()
            }

            return computedEntry?.value
        } catch (Exception e) {
            throw new CacheMediationException("Cache 'get' operation failed for key '$key'.", e)
        }
    }

    void set(String key, Object value, Duration ttl = null) {
        if (ValueValidator.isInvalid(value)) {
            log.warn("Attempted to cache an invalid (null or empty) value for key '{}'. Ignoring.", key)
            return
        }
        def entryTtl = ttl ?: config.defaultTtl
        def entry = new CacheEntry(value: value, lastAccessTime: Instant.now(), ttl: entryTtl)
        cache.put(key, entry)
        schedulePersistence()
    }

    void invalidate(String key) {
        log.debug("Invalidating key: {}", key)
        if (cache.remove(key) != null) {
            schedulePersistence()
        }
    }

    void invalidateMatching(String pattern) {
        def compiledPattern = Pattern.compile(pattern.replace("*", ".*"))
        def keysToRemove = cache.keySet().findAll { key -> compiledPattern.matcher(key).matches() }

        if (keysToRemove) {
            log.info("Invalidating {} entries matching pattern '{}'", keysToRemove.size(), pattern)
            keysToRemove.each { cache.remove(it) }
            schedulePersistence()
        }
    }

    @Override
    void close() {
        log.info("Shutting down CacheMediator...")
        scheduler?.shutdown()

        if (persistenceManager && persistenceExecutor) {
            try {
                log.info("Performing final synchronous persistence...")
                persistenceManager.persist(new ConcurrentHashMap<>(cache))

                persistenceExecutor.shutdown()
                if (!persistenceExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("Persistence executor did not terminate in time.")
                    persistenceExecutor.shutdownNow()
                }
            } catch (InterruptedException e) {
                log.error("Shutdown was interrupted.", e)
                Thread.currentThread().interrupt()
                persistenceExecutor.shutdownNow()
            }
        }
        log.info("CacheMediator shutdown complete.")
    }

    private CacheEntry getValidEntry(String key) {
        def entry = cache.get(key)
        if (entry != null) {
            if (entry.isExpired()) {
                log.debug("Found expired entry for key '{}'. Invalidating.", key)
                cache.remove(key, entry) // Atomic remove
                return null
            }
            entry.updateAccessTime()
            return entry
        }
        return null
    }

    private void schedulePersistence() {
        if (persistenceManager && config.asyncPersistence) {
            persistenceExecutor.submit { persistenceManager.persist(new ConcurrentHashMap<>(cache)) }
        }
    }

    /**
     * Manages configuration properties for the cache.
     */
    @Canonical
    @CompileStatic
    private static class Config {
        final Duration defaultTtl
        final long maxSize
        final Path persistenceDir
        final boolean asyncPersistence
        final Duration cleanupInterval

        Config(Map<String, ?> options) {
            this.defaultTtl = options.getOrDefault('defaultTtl', Duration.ofDays(7)) as Duration
            this.maxSize = options.getOrDefault('maxSize', 10_000L) as Long
            String persistencePath = options.get('persistenceDir') as String
            this.persistenceDir = persistencePath ? Paths.get(persistencePath) : null
            this.asyncPersistence = options.getOrDefault('asyncPersistence', true) as boolean
            this.cleanupInterval = options.getOrDefault('cleanupInterval', Duration.ofHours(1)) as Duration
        }
    }

    /**
     * Validates cacheable values.
     */
    @CompileStatic
    private static class ValueValidator {
        static boolean isInvalid(Object value) {
            value == null ||
                    (value instanceof String && ((String) value).isEmpty()) ||
                    (value instanceof Collection && ((Collection) value).isEmpty()) ||
                    (value instanceof Map && ((Map) value).isEmpty())
        }
    }

    /**
     * Handles periodic cleanup of expired and oversized cache entries.
     */
    @Slf4j
    @CompileStatic
    private static class Cleaner {
        static void run(ConcurrentHashMap<String, CacheEntry> cache, long maxSize, Lock cleanupLock) {
            if (!cleanupLock.tryLock()) {
                log.warn("Cleanup is already in progress. Skipping this run.")
                return
            }
            try {
                log.info("Starting cache cleanup...")
                removeExpired(cache)
                evictLruIfOversized(cache, maxSize)
                log.info("Cache cleanup finished.")
            } catch (Exception e) {
                log.error("Error during cache cleanup", e)
            } finally {
                cleanupLock.unlock()
            }
        }

        private static void removeExpired(ConcurrentHashMap<String, CacheEntry> cache) {
            def expiredKeys = cache.entrySet().stream()
                    .filter { it.value.isExpired() }
                    .map { it.key }
                    .toList()

            if (expiredKeys) {
                log.info("Removing {} expired entries.", expiredKeys.size())
                expiredKeys.each { cache.remove(it) }
            }
        }

        private static void evictLruIfOversized(ConcurrentHashMap<String, CacheEntry> cache, long maxSize) {
            if (maxSize <= 0 || cache.size() <= maxSize) {
                return
            }
            def overflow = cache.size() - maxSize
            log.info("Cache size ({}) exceeds max size ({}). Evicting {} LRU entries.", cache.size(), maxSize, overflow)

            def keysToEvict = cache.entrySet().stream()
                    .sorted(Comparator.comparing { Map.Entry<String, CacheEntry> it -> it.value.lastAccessTime })
                    .limit(overflow)
                    .map { it.key }
                    .toList()

            keysToEvict.each { cache.remove(it) }
            log.info("Evicted {} entries to meet max size.", keysToEvict.size())
        }
    }

    /**
     * Handles filesystem persistence for the cache.
     */
    @Slf4j
    @CompileStatic
    private static class PersistenceManager {
        private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
        private final Path persistenceDir
        private final ObjectMapper mapper
        private final MapType mapType

        PersistenceManager(Path persistenceDir) {
            this.persistenceDir = persistenceDir
            this.mapper = new ObjectMapper().findAndRegisterModules()
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(SerializationFeature.INDENT_OUTPUT)
            this.mapType = mapper.typeFactory.constructMapType(ConcurrentHashMap.class, String.class, CacheEntry.class)
        }

        void persist(Map<String, CacheEntry> snapshot) {
            if (snapshot.isEmpty()) {
                log.info("Cache is empty, nothing to persist.")
                return
            }
            log.info("Persisting {} cache entries to directory: {}", snapshot.size(), persistenceDir)
            try {
                Path targetFile = findTargetFile()
                log.info("Writing cache snapshot to: {}", targetFile)
                mapper.writeValue(targetFile.toFile(), snapshot)
                log.info("Successfully persisted snapshot.")
            } catch (IOException e) {
                log.error("Failed to persist cache state.", e)
                throw new CacheMediationException("Failed to persist cache state.", e)
            }
        }

        CacheEntry load(String key) {
            log.debug("Attempting to load key '{}' from disk.", key)
            try {
                List<Path> files = findPersistenceFiles()
                for (Path file in files) {
                    try {
                        Map<String, CacheEntry> fileContent = mapper.readValue(file.toFile(), mapType)
                        if (fileContent.containsKey(key)) {
                            CacheEntry entry = fileContent[key]
                            if (!entry.isExpired()) {
                                log.info("Found valid key '{}' in persistence file: {}", key, file)
                                entry.updateAccessTime()
                                return entry
                            }
                        }
                    } catch (IOException e) {
                        log.error("Failed to read or parse persistence file: {}", file, e)
                    }
                }
            } catch (IOException e) {
                log.error("Could not list persistence directory: {}", persistenceDir, e)
            }
            log.debug("Key '{}' not found in any persistence files.", key)
            return null
        }

        private Path findTargetFile() throws IOException {
            String baseFileName = LocalDate.now().toString()
            List<Path> filesToday = Files.list(persistenceDir)
                    .filter { it.fileName.toString().startsWith(baseFileName) && it.fileName.toString().endsWith(".json") }
                    .sorted()
                    .toList()

            if (filesToday && Files.size(filesToday.last()) < MAX_FILE_SIZE_BYTES) {
                return filesToday.last()
            }
            return persistenceDir.resolve("${baseFileName}-${filesToday.size()}.json")
        }

        private List<Path> findPersistenceFiles() throws IOException {
            if (!Files.exists(persistenceDir)) return []
            return Files.list(persistenceDir)
                    .filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                    .sorted(Comparator.reverseOrder())
                    .toList()
        }
    }

    /**
     * A custom exception for wrapping errors that occur during cache operations.
     */
    @CompileStatic
    static class CacheMediationException extends RuntimeException {
        CacheMediationException(String message, Throwable cause) {
            super(message, cause)
        }
    }

    /**
     * Internal representation of a cached entry, holding the value and metadata.
     */
    @Canonical
    @CompileStatic
    static class CacheEntry {
        Object value
        Instant lastAccessTime
        Duration ttl

        boolean isExpired() {
            return Instant.now().isAfter(lastAccessTime.plus(ttl))
        }

        void updateAccessTime() {
            this.lastAccessTime = Instant.now()
        }
    }

}

