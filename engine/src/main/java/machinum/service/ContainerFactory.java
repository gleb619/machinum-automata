package machinum.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.model.ChromeConfig;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.VncRecordingContainer;

import java.io.File;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class ContainerFactory {

    @SneakyThrows
    public static BrowserWebDriverContainer<?> createChromeContainer(ChromeConfig config) {
        var recordingDirectory = new File(config.getRecordingDirectory())
                .getCanonicalFile()
                .getAbsoluteFile();

        if(!recordingDirectory.exists()) {
            recordingDirectory.mkdirs();
            log.info("Created recording folder: {}", recordingDirectory.getAbsolutePath());
        }

        var container = new BrowserWebDriverContainer<>("selenium/standalone-chrome")
                .withRecordingMode(
                        BrowserWebDriverContainer.VncRecordingMode.valueOf(config.getRecordingMode()),
                        recordingDirectory,
                        VncRecordingContainer.VncRecordingFormat.MP4
                )
                .withCapabilities(buildChromeOptions(config))
                .withReuse(true);

        for (Map.Entry<String, String> env : config.getEnvironmentVariables().entrySet()) {
            container.withEnv(env.getKey(), env.getValue());
        }

        return container;
    }

    public static ChromeOptions buildChromeOptions(ChromeConfig config) {
        ChromeOptions options = new ChromeOptions();

        if (config.isHeadless()) {
            options.addArguments("--headless=new", "--disable-gpu");
        }

        // Essential Chrome arguments for containerized environments
        options.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-extensions",
                "--disable-plugins",
                "--disable-images",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding"
        );

        // Add custom arguments
        if (config.getArguments() != null) {
            config.getArguments().forEach(options::addArguments);
        }

        // Set user agent
        if (config.getUserAgent() != null) {
            options.addArguments("--user-agent=" + config.getUserAgent());
        }

        // Set experimental options
        for (Map.Entry<String, Object> option : config.getExperimentalOptions().entrySet()) {
            options.setExperimentalOption(option.getKey(), option.getValue());
        }

        options.setImplicitWaitTimeout(Duration.ofSeconds(config.getImplicitWaitSeconds()));
        options.setPageLoadTimeout(Duration.ofSeconds(config.getPageLoadTimeoutSeconds()));
        options.setScriptTimeout(Duration.ofSeconds(config.getScriptTimeoutSeconds()));

        return options;
    }

}
