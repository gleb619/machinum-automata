package machinum.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import machinum.model.ChromeConfig
import machinum.model.ScenarioResult
import machinum.model.SessionInfo
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.lifecycle.TestDescription

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@CompileStatic
class BrowserInstance {

    private final String sessionId
    private final ChromeConfig config
    private final long createdAt = System.currentTimeMillis()
    private final AtomicInteger executionCount = new AtomicInteger(0)

    private volatile long lastAccessTime = System.currentTimeMillis()

    private BrowserWebDriverContainer container
    private WebDriver driver

    BrowserInstance(String sessionId, ChromeConfig config) {
        this.sessionId = sessionId
        this.config = config
    }

    BrowserInstance initialize() {
        log.info("Initializing browser instance: {}", sessionId)

        container = ContainerFactory.createChromeContainer(config)
        container.start()

        log.info("Selenium URL for session {}: {}", sessionId, container.getSeleniumAddress())

        initDriver()

        log.info("Browser instance initialized successfully: {}", sessionId)

        return this
    }

    private BrowserInstance initDriver() {
        RemoteWebDriver remoteDriver = new RemoteWebDriver(
                container.getSeleniumAddress(),
                ContainerFactory.buildChromeOptions(config)
        )

        // Configure timeouts
        remoteDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.implicitWaitSeconds))
        remoteDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.pageLoadTimeoutSeconds))
        remoteDriver.manage().timeouts().scriptTimeout(Duration.ofSeconds(config.scriptTimeoutSeconds))

        this.driver = remoteDriver

        return this
    }

    ScenarioResult executeScript(String groovyScript, Map<String, Object> params, int timeoutSeconds = 60) {
        if (this.driver == null) {
            initDriver()
        }

        lastAccessTime = System.currentTimeMillis()
        executionCount.incrementAndGet()

        Instant start = Instant.now()
        def videoFileName = "${groovyScript.md5()}-${System.nanoTime()}"

        try {
            log.debug("Executing script for session {}: [{}] {}...", sessionId, groovyScript.md5(), groovyScript.replaceAll("\n", " ").take(100))
            def parameters = params ?: Collections.<String, Object> emptyMap()
            ScenarioEngine engine = new ScenarioEngine(this, parameters)
            def result = engine.runScript(groovyScript, videoFileName, timeoutSeconds)

            log.info("Script execution completed for session {} in {}s", sessionId, Duration.between(start, Instant.now()).toSeconds())

            return ScenarioResult.success(result, videoFileName, start)
        } catch (Exception e) {
            log.error("Script execution failed for session {}: {}", sessionId, e.message, e)
            String screenshot = null
            try {
                screenshot = takeScreenshot()
            } catch (Exception ignored) {
                log.warn("Failed to capture screenshot for session {}", sessionId)
            }

            return ScenarioResult.failure(e.message, screenshot, videoFileName, start)
        } finally {
            //TODO maybe, we should clean driver?
            this.driver = null
        }
    }

    String takeScreenshot() {
        if (driver instanceof TakesScreenshot) {
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)
            return Base64.getEncoder().encodeToString(screenshot)
        }
        return null
    }

    String getPageSource() {
        return driver.getPageSource()
    }

    WebDriver getDriver() {
        lastAccessTime = System.currentTimeMillis()
        return driver
    }

    boolean isAlive() {
        try {
            return container?.isRunning() &&
                    (System.currentTimeMillis() - lastAccessTime) < TimeUnit.HOURS.toMillis(2)
        } catch (Exception e) {
            log.warn("Error checking if session {} is alive: {}", sessionId, e.message)
            return false
        }
    }

    SessionInfo getSessionInfo() {
        return SessionInfo.builder()
                .id(sessionId)
                .createdAt(createdAt)
                .config(config)
                .status(isAlive() ? "active" : "inactive")
                .executionCount(executionCount.get())
                .lastAccessTime(lastAccessTime)
                .build()
    }

    void cleanup() {
        log.info("Cleaning up browser instance: {}", sessionId)
        try {
            driver?.quit()
        } catch (Exception e) {
            log.warn("Error closing driver for session {}: {}", sessionId, e.message)
        }

        try {
            container?.stop()
        } catch (Exception e) {
            log.warn("Error stopping container for session {}: {}", sessionId, e.message)
        }
    }

    @Override
    String toString() {
        return "BrowserInstance{" +
                "container=" + container.getSeleniumAddress() +
                '}'
    }

    void saveVideo(String hash) {
        container.afterTest(createDescription(hash), Optional.empty())
    }

    private TestDescription createDescription(String hash) {
        return new TestDescription() {

            @Override
            String getTestId() {
                return "${BrowserInstance.this.getClass()}-${hash}"
            }

            @Override
            String getFilesystemFriendlyName() {
                return hash
            }

        }
    }

}
