package machinum.service


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.regex.Pattern

/**
 * A custom exception for wrapping errors that occur during cache operations.
 */
@CompileStatic
class CacheMediationException extends RuntimeException {
    CacheMediationException(String message, Throwable cause) {
        super(message, cause)
    }
}

/**
 * Internal representation of a cached entry, holding the value and metadata.
 */
@Canonical
class CacheEntry {
    Object value
    Instant lastAccessTime
    Duration ttl
}

/**
 * Trait handling the periodic cleanup of expired and oversized cache entries.
 */
@Slf4j
@CompileStatic
trait CleanupTrait {
    ConcurrentHashMap<String, CacheEntry> cache
    Long maxSize
    boolean isCleaning = false

    void cleanupExpiredEntries() {
        if (isCleaning) {
            log.warn("Cleanup is already in progress. Skipping this run.")
            return
        }
        isCleaning = true
        try {
            log.info("Starting cache cleanup...")
            def now = Instant.now()
            def expiredKeys = []

            cache.each { key, entry ->
                if (now.isAfter(entry.lastAccessTime.plus(entry.ttl))) {
                    expiredKeys.add(key)
                }
            }

            if (expiredKeys) {
                log.info("Removing ${expiredKeys.size()} expired entries.")
                expiredKeys.each { cache.remove(it) }
            }

            // If maxSize is enforced, evict least recently used entries
            if (maxSize > 0 && cache.size() > maxSize) {
                def overflow = cache.size() - maxSize
                log.info("Cache size (${cache.size()}) exceeds max size ($maxSize). Evicting $overflow LRU entries.")

                def keysToEvict = cache.entrySet()
                        .stream()
                        .sorted(Comparator.comparing { Map.Entry<String, CacheEntry> it -> it.value.lastAccessTime })
                        .limit(overflow)
                        .map { it.key }
                        .toList()

                keysToEvict.each { cache.remove(it) }
                log.info("Evicted ${keysToEvict.size()} entries to meet max size.")
            }
            log.info("Cache cleanup finished.")
        } catch (Exception e) {
            log.error("Error during cache cleanup", e)
        } finally {
            isCleaning = false
        }
    }
}

/**
 * Trait handling filesystem persistence for the cache.
 */
@Slf4j
@CompileStatic
trait PersistenceTrait {
    ConcurrentHashMap<String, CacheEntry> cache
    Path persistenceDir
    ExecutorService persistenceExecutor
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB

    /**
     * Schedules an asynchronous persistence of the current cache state.
     */
    void schedulePersist() {
        if (persistenceDir == null) return
        persistenceExecutor.submit { persistNow() }
    }

    /**
     * Persists the current cache state to the filesystem synchronously.
     */
    void persistNow() {
        if (persistenceDir == null) return
        log.info("Persisting cache state to directory: $persistenceDir")
        try {
            def snapshot = new ConcurrentHashMap<String, CacheEntry>(cache)
            if (snapshot.isEmpty()) {
                log.info("Cache is empty. Nothing to persist.")
                return
            }

            def baseFileName = "${java.time.LocalDate.now()}"
            def files = findLatestPersistenceFiles(baseFileName)
            Path targetFile

            if (files && Files.size(files.last()) < MAX_FILE_SIZE_BYTES) {
                targetFile = files.last()
            } else {
                def nextIndex = files ? (files.size()) : 0
                targetFile = persistenceDir.resolve("${baseFileName}-${nextIndex}.json")
            }

            log.info("Writing cache snapshot to: $targetFile")
            mapper.writeValue(targetFile.toFile(), snapshot)
            log.info("Successfully persisted ${snapshot.size()} entries.")

        } catch (IOException e) {
            log.error("Failed to persist cache state.", e)
            throw new CacheMediationException("Failed to persist cache state.", e)
        }
    }

    private List<Path> findLatestPersistenceFiles(String baseFileName) {
        try {
            return Files.list(persistenceDir)
                    .filter { it.fileName.toString().startsWith(baseFileName) && it.fileName.toString().endsWith(".json") }
                    .sorted()
                    .toList()
        } catch (IOException e) {
            log.warn("Could not list persistence files for base name '$baseFileName'", e)
            return []
        }
    }


    /**
     * Attempts to load a key from the filesystem on a cache miss.
     * Scans files from newest to oldest.
     */
    CacheEntry loadFromDisk(String key) {
        if (persistenceDir == null || !Files.exists(persistenceDir)) {
            return null
        }
        log.debug("Cache miss for key '$key'. Attempting to load from disk.")

        try {
            def files = Files.list(persistenceDir)
                    .filter { it.toString().endsWith(".json") }
                    .sorted(Comparator.reverseOrder())
                    .toList()

            for (Path file in files) {
                try {
                    def mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, CacheEntry.class)
                    Map<String, CacheEntry> fileContent = mapper.readValue(file.toFile(), mapType)
                    if (fileContent.containsKey(key)) {
                        def entry = fileContent[key]
                        log.info("Found key '$key' in persistence file: $file")
                        // Refresh timestamp on load
                        entry.lastAccessTime = Instant.now()
                        return entry
                    }
                } catch (IOException e) {
                    log.error("Failed to read or parse persistence file: $file", e)
                }
            }
        } catch (IOException e) {
            log.error("Could not list persistence directory.", e)
        }

        log.debug("Key '$key' not found in any persistence files.")
        return null
    }
}

/**
 * A cache mediator that provides a simple interface for caching operations
 * with ConcurrentHashMap storage and optional filesystem persistence.
 */
@Slf4j
@CompileStatic
class CacheMediator implements Closeable, CleanupTrait, PersistenceTrait {

    final Duration defaultTtl
    final boolean asyncPersistence
    private ScheduledExecutorService scheduler

    /**
     * Constructor with configuration options.
     * @param options A map of configuration options.
     * - defaultTtl (Duration): The default time-to-live for cache entries. Default is 7 days.
     * - maxSize (long): The maximum number of entries in the cache. Default is 0 (unlimited).
     * - persistenceDir (String): Path to the directory for filesystem persistence. If null, persistence is disabled.
     * - asyncPersistence (boolean): Whether persistence operations should run in a background thread. Default is true.
     */
    CacheMediator(Map options = [:]) {
        this.defaultTtl = options.getOrDefault('defaultTtl', Duration.ofDays(7)) as Duration
        this.maxSize = options.getOrDefault('maxSize', 10_000L) as Long
        this.asyncPersistence = options.getOrDefault('asyncPersistence', true) as boolean

        String persistenceDirPath = options.getOrDefault('persistenceDir', null) as String
        if (persistenceDirPath) {
            this.persistenceDir = Paths.get(persistenceDirPath)
            this.persistenceExecutor = Executors.newSingleThreadExecutor({ r -> new Thread(r, "cache-persistence-thread") })
        }

        this.cache = new ConcurrentHashMap<String, CacheEntry>()

        this.scheduler = Executors.newSingleThreadScheduledExecutor({ r -> new Thread(r, "cache-cleanup-thread") })

        log.info("CacheMediator initialized. Default TTL: $defaultTtl, Max Size: $maxSize, Persistence Dir: $persistenceDir")
    }

    // Start automatic cleanup scheduler
    CacheMediator init() {
        if (persistenceDir != null) {
            Files.createDirectories(persistenceDir)
        }
        scheduler.scheduleAtFixedRate({ cleanupExpiredEntries() }, 1, 1, TimeUnit.HOURS)
        log.info("CacheMediator initialized")

        return this
    }

    /**
     * Retrieves an entry from the cache. If the entry is not present, the fallback closure is executed,
     * and its result is stored in the cache before being returned.
     * @param key The cache key.
     * @param fallback The closure to execute on a cache miss.
     * @return The cached or newly computed value.
     */
    Object get(String key, Closure<?> fallback) {
        try {
            // Use compute to handle both miss and update access time on hit
            def entry = cache.compute(key) { k, existingEntry ->
                // HIT: Entry exists and is valid
                if (existingEntry != null && Instant.now().isBefore(existingEntry.lastAccessTime.plus(existingEntry.ttl))) {
                    existingEntry.lastAccessTime = Instant.now()
                    return existingEntry
                }

                // MISS: Try loading from disk first
                def loadedEntry = loadFromDisk(k)
                if (loadedEntry != null) {
                    return loadedEntry
                }

                // MISS: Execute fallback
                def value = fallback.call()
                if (value == null || (value instanceof Collection && value.isEmpty()) || (value instanceof Map && value.isEmpty())) {
                    return null // Don't cache null/empty results
                }
                new CacheEntry(value: value, lastAccessTime: Instant.now(), ttl: defaultTtl)
            }

            return entry?.value
        } catch (Exception e) {
            throw new CacheMediationException("Cache 'get' operation failed for key '$key'.", e)
        }
    }

    /**
     * Explicitly sets a value in the cache with an optional TTL.
     * @param key The cache key.
     * @param value The value to store. Should not be null or empty.
     * @param ttl An optional TTL for this specific entry, overriding the default.
     */
    void set(String key, Object value, Duration ttl = null) {
        if (value == null ||
                (value instanceof String && value.isEmpty()) ||
                (value instanceof Collection && value.isEmpty()) ||
                (value instanceof Map && value.isEmpty())) {
            log.warn("Attempted to cache a null or empty value for key '$key'. Ignoring.")
            return
        }
        def entryTtl = ttl ?: defaultTtl
        def entry = new CacheEntry(value: value, lastAccessTime: Instant.now(), ttl: entryTtl)
        cache.put(key, entry)
        if (asyncPersistence) {
            schedulePersist()
        }
    }

    /**
     * Removes an entry from the cache.
     * @param key The key to invalidate.
     */
    void invalidate(String key) {
        log.debug("Invalidating key: $key")
        cache.remove(key)
        if (asyncPersistence) {
            schedulePersist()
        }
    }

    /**
     * Removes all entries whose keys match the given pattern.
     * Note: This operation can be slow on very large caches as it iterates over all keys.
     * @param pattern A regex pattern to match against keys.
     */
    void invalidateMatching(String pattern) {
        def compiledPattern = Pattern.compile(pattern.replace("*", ".*"))
        def keysToRemove = []
        cache.keySet().each { key ->
            if (compiledPattern.matcher(key).matches()) {
                keysToRemove.add(key)
            }
        }
        if (keysToRemove) {
            log.info("Invalidating ${keysToRemove.size()} entries matching pattern '$pattern'")
            keysToRemove.each { cache.remove(it) }
            if (asyncPersistence) {
                schedulePersist()
            }
        }
    }

    /**
     * Gracefully shuts down the cache mediator, persisting any final state.
     */
    @Override
    void close() throws IOException {
        log.info("Shutting down CacheMediator...")
        scheduler?.shutdown()

        if (persistenceExecutor) {
            try {
                // Persist one last time synchronously
                persistNow()
                persistenceExecutor.shutdown()
                if (!persistenceExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.error("Persistence executor did not terminate in time.")
                    persistenceExecutor.shutdownNow()
                }
            } catch (InterruptedException e) {
                log.error("Shutdown was interrupted.", e)
                persistenceExecutor.shutdownNow()
            }
        }
        log.info("CacheMediator shutdown complete.")
    }

}

