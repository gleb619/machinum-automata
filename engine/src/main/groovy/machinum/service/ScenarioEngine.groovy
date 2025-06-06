package machinum.service

import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Slf4j
class ScenarioEngine {

    private final BrowserInstance browserInstance
    private final GroovyShell shell

    ScenarioEngine(BrowserInstance browserInstance) {
        this.browserInstance = browserInstance

        // Secure binding with limited scope
        Binding binding = new Binding([
                driver: browserInstance.getDriver(),
                utils: new ScenarioUtils(browserInstance),
                By: By,
                Keys: Keys,
                WebDriverWait: WebDriverWait,
                ExpectedConditions: ExpectedConditions
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
    }

    Object runScript(String script, int timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor()

        try {
            Future<Object> future = executor.submit({
                return shell.evaluate(script)
            } as Callable<Object>)

            return future.get(timeoutSeconds, TimeUnit.SECONDS)

        } catch (TimeoutException e) {
            throw new RuntimeException("Script execution timeout after ${timeoutSeconds} seconds")
        } finally {
            executor.shutdownNow()
        }
    }

}
