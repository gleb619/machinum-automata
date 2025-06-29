package machinum.service

import groovy.transform.CompileStatic
import groovy.transform.Synchronized
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@CompileStatic
class BrowserInstance {

    private final long createdAt = System.currentTimeMillis()
    private final AtomicInteger executionCount = new AtomicInteger(0)
    private final AtomicBoolean started = new AtomicBoolean(false)

    private final String sessionId
    private final ChromeConfig config
    private final CacheMediator cacheMediator

    private volatile long lastAccessTime = System.currentTimeMillis()

    private BrowserWebDriverContainer container
    private WebDriver driver

    BrowserInstance(CacheMediator cacheMediator, String sessionId, ChromeConfig config) {
        this.sessionId = sessionId
        this.config = config
        this.cacheMediator = cacheMediator
    }

    BrowserInstance initialize() {
        log.info("Initializing browser instance: {}", sessionId)

        container = ContainerFactory.createChromeContainer(config)

        log.info("Browser instance initialized successfully: {}", sessionId)

        return this
    }

    private RemoteWebDriver initDriver() {
        log.info("Prepare to create selenium driver to work with {}: {}", sessionId, container.getSeleniumAddress())

        def remoteDriver = new RemoteWebDriver(
                container.getSeleniumAddress(),
                ContainerFactory.buildChromeOptions(config)
        )

        // Configure timeouts
        remoteDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.implicitWaitSeconds))
        remoteDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.pageLoadTimeoutSeconds))
        remoteDriver.manage().timeouts().scriptTimeout(Duration.ofSeconds(config.scriptTimeoutSeconds))

        return remoteDriver
    }

    @Synchronized
    //TODO add container recreation on driver exception
    ScenarioResult executeScript(String groovyScript, Map<String, Object> params, int timeoutSeconds = 60) {
        log.info("Prepare to execute script {}", sessionId)

        if (!container.isRunning()) {
            CompletableFuture.runAsync {
                container.start()
                started.getAndSet(Boolean.TRUE)
            }.get(config.scriptTimeoutSeconds, TimeUnit.SECONDS)
            log.info("Container has been started {} - {}", sessionId, container.getContainerId())
        }

        if (this.driver == null) {
            this.driver = CompletableFuture.supplyAsync {
                initDriver()
            }.get(config.scriptTimeoutSeconds, TimeUnit.SECONDS)
            log.info("Create driver, selenium URL for session {}: {}", sessionId, container.getSeleniumAddress())
        }

        lastAccessTime = System.currentTimeMillis()
        executionCount.incrementAndGet()

        def start = Instant.now()
        def videoFileName = "${groovyScript.md5()}-${System.nanoTime()}"

        try {
            log.debug("Executing script for session {}: [{}] {}...", sessionId, groovyScript.md5(), groovyScript.replaceAll("\n", " ").take(100))
            def parameters = params ?: Collections.<String, Object> emptyMap()
            def engine = new ScenarioEngine(this, cacheMediator, parameters)
            def result = engine.runScript(groovyScript, videoFileName, timeoutSeconds)

            log.info("Script execution completed for session {} in {}s", sessionId, Duration.between(start, Instant.now()).toSeconds())

            return ScenarioResult.success(result, videoFileName, start)
        } catch (Exception e) {
            log.error("Script execution failed for session {}: {}", sessionId, e.message, e)
            String screenshot
            try {
                screenshot = takeScreenshot()
            } catch (Exception ignored) {
                screenshot = null
                log.warn("Failed to capture screenshot for session {}", sessionId)
            }

            def pageHtml = capturePageHtml()
            def htmlFile = CompletableFuture.supplyAsync {
                def file = new File(config.getReportDirectory(), "${this.driver.getCurrentUrl().md5()}-${System.currentTimeMillis()}.html")
                file.write(pageHtml, "UTF-8")
                return file.getName()
            }.get(config.scriptTimeoutSeconds, TimeUnit.SECONDS)

            return ScenarioResult.failure(e.message, screenshot, videoFileName, htmlFile, start)
        }
    }

    String takeScreenshot() {
        if (driver instanceof TakesScreenshot) {
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)
            return Base64.getEncoder().encodeToString(screenshot)
        }

        return null
    }

    //TODO should we add the script code here?
    String capturePageHtml() {
        try {
            def driver = getDriver()
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            String currentUrl = driver.getCurrentUrl()
            String pageTitle = driver.getTitle()
            String pageSource = driver.getPageSource()

            StringBuilder htmlCapture = new StringBuilder()
            htmlCapture.append("<!-- ERROR CAPTURE REPORT -->\n")
            htmlCapture.append("<!-- Timestamp: ${timestamp} -->\n")
            htmlCapture.append("<!-- URL: ${currentUrl} -->\n")
            htmlCapture.append("<!-- Page Title: ${pageTitle} -->\n")
            htmlCapture.append("<!-- ======================== -->\n\n")
            htmlCapture.append(pageSource)

            return htmlCapture.toString()
        } catch (Exception captureError) {
            return "<!-- FAILED TO CAPTURE PAGE HTML -->\n<!-- Capture Error: ${captureError.getMessage()} -->"
        }
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
            if (started.get()) {
                return container?.isRunning() &&
                        (System.currentTimeMillis() - lastAccessTime) < TimeUnit.MINUTES.toMillis(10)
            } else {
                return true
            }
        } catch (Exception e) {
            log.warn("Error checking if session {} is alive: {}", sessionId, e.message)
            return false
        }
    }

    String getSessionId() {
        return sessionId
    }

    ChromeConfig getConfig() {
        return config
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
        container.afterTest(new TestDescription() {

            @Override
            String getTestId() {
                return "${BrowserInstance.this.getClass()}-${hash}"
            }

            @Override
            String getFilesystemFriendlyName() {
                return hash
            }

        }, Optional.empty())
    }

}
