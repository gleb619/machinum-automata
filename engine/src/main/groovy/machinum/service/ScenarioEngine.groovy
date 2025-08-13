package machinum.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import machinum.exception.AppException
import machinum.exception.SessionExpiredException
import org.apache.groovy.internal.util.UncheckedThrow
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.NoSuchSessionException
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.function.Supplier

@Slf4j
@CompileStatic
class ScenarioEngine {

    private final BrowserInstance browserInstance
    private final GroovyShell shell
    private final Instant start = Instant.now()

    ScenarioEngine(BrowserInstance browserInstance, CacheMediator cacheMediator, Map<String, Object> params) {
        this.browserInstance = browserInstance

        def utils
        if (browserInstance instanceof LocalBrowserInstance) {
            utils = new ScenarioUtils(browserInstance)
        } else {
            utils = new ScenarioUtils.Noop()
        }

        // Secure binding with limited scope
        Binding binding = new Binding([
                driver: browserInstance.getDriver(),
                utils: utils,
                By: By,
                Keys: Keys,
                WebDriverWait: WebDriverWait,
                ExpectedConditions: ExpectedConditions,
                log   : log,
                params: params,
                cache : cacheMediator,
        ])

        // Create sandboxed shell with security restrictions
        CompilerConfiguration config = new CompilerConfiguration()

        // Basic security customizer to limit dangerous operations
        SecureASTCustomizer secureCustomizer = new SecureASTCustomizer()

        // Blacklist dangerous imports and packages
        secureCustomizer.setDisallowedImports([
                'java.io.*', 'java.nio.*', 'java.lang.reflect.*',
                'java.lang.Runtime', 'java.lang.ProcessBuilder'
        ] as String[] as List<String>)

        secureCustomizer.setDisallowedStarImports([
                'java.io', 'java.nio', 'java.lang.reflect'
        ] as String[] as List<String>)

        config.addCompilationCustomizers(secureCustomizer)

        this.shell = new GroovyShell(binding, config)
        log.debug("Created groovy engine from: {}", browserInstance)
    }

    Object runScript(String script, String videoFileName, int timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor()

        def future = CompletableFuture.supplyAsync({
            log.debug("Engine is working with: [{}] {}...", script.md5(), script.replaceAll("\n", " ").take(100))
            return shell.evaluate(script)
        } as Supplier<Object>, executor)

        future.whenComplete { result, ex ->
            def duration = Duration.between(start, Instant.now())
            if (duration.toMinutes() < 6) {
                CompletableFuture.runAsync {
                    if (browserInstance instanceof LocalBrowserInstance) {
                        ((LocalBrowserInstance) browserInstance).saveVideo(videoFileName)
                    }
                }
            }

            if (Objects.nonNull(ex)) {
                log.error("Found error: ", ex)
                UncheckedThrow.rethrow(ex)
            }
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (ExecutionException e) {
            def cause = e.getCause()
            if (cause instanceof NoSuchSessionException) {
                throw new SessionExpiredException("Got execution error: ${e.getMessage()}", cause != null ? cause : e)
            } else {
                throw new AppException("Got execution error: ${e.getMessage()}", cause != null ? cause : e)
            }
        } catch (TimeoutException ignore) {
            throw new AppException("Script execution timeout after ${timeoutSeconds} seconds")
        } finally {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (InterruptedException ignore) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

}
