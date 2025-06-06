package machinum.service

import machinum.model.ChromeConfig
import machinum.model.ScenarioResult
import machinum.model.SessionInfo
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.GenericContainer

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@CompileStatic
class BrowserInstance {

    private final String sessionId
    private final ChromeConfig config
    private BrowserWebDriverContainer container
    private WebDriver driver
    private final long createdAt = System.currentTimeMillis()
    private final AtomicInteger executionCount = new AtomicInteger(0)
    private volatile long lastAccessTime = System.currentTimeMillis()

    BrowserInstance(String sessionId, ChromeConfig config) {
        this.sessionId = sessionId
        this.config = config
    }

    void initialize() {
        log.info("Initializing browser instance: {}", sessionId)

        container = ContainerFactory.createChromeContainer(config)
        container.start()

        log.info("Selenium URL for session {}: {}", sessionId, container.getSeleniumAddress())

        RemoteWebDriver remoteDriver = new RemoteWebDriver(
                container.getSeleniumAddress(),
                ContainerFactory.buildChromeOptions(config)
        )

        // Configure timeouts
        remoteDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.implicitWaitSeconds))
        remoteDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.pageLoadTimeoutSeconds))
        remoteDriver.manage().timeouts().scriptTimeout(Duration.ofSeconds(config.scriptTimeoutSeconds))

        this.driver = remoteDriver
        log.info("Browser instance initialized successfully: {}", sessionId)
    }

    ScenarioResult executeScript(String groovyScript, int timeoutSeconds = 60) {
        lastAccessTime = System.currentTimeMillis()
        executionCount.incrementAndGet()

        long startTime = System.currentTimeMillis()

        try {
            log.debug("Executing script for session {}: {}", sessionId, groovyScript.take(100))
            ScenarioEngine engine = new ScenarioEngine(this)
            Object result = engine.runScript(groovyScript, timeoutSeconds)

            long executionTime = System.currentTimeMillis() - startTime
            log.info("Script execution completed for session {} in {}ms", sessionId, executionTime)

            return ScenarioResult.success(result, executionTime)

        } catch (Exception e) {
            log.error("Script execution failed for session {}: {}", sessionId, e.message, e)
            String screenshot = null
            try {
                screenshot = takeScreenshot()
            } catch (Exception ignored) {
                log.warn("Failed to capture screenshot for session {}", sessionId)
            }

            long executionTime = System.currentTimeMillis() - startTime
            return ScenarioResult.failure(e.message, screenshot, executionTime)
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
    
}
