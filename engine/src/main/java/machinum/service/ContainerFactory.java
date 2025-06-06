package machinum.service;

import lombok.extern.slf4j.Slf4j;
import machinum.model.ChromeConfig;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class ContainerFactory {

    public static BrowserWebDriverContainer<?> createChromeContainer(ChromeConfig config) {
        File recordingDirectory = new File("build");
        if(!recordingDirectory.exists()) {
            recordingDirectory.mkdirs();
            log.info("Created recording folder: {}", recordingDirectory.getAbsolutePath());
        }

        return (BrowserWebDriverContainer<?>) new BrowserWebDriverContainer()
                .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.RECORD_ALL, recordingDirectory)
                .withCapabilities(getPrefs())
                .withEnv("LANG", "ru_RU")
                .withEnv("LANGUAGE", "ru_RU");
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

        // Set preferences
        if (config.getPreferences() != null && !config.getPreferences().isEmpty()) {
            options.setExperimentalOption("prefs", config.getPreferences());
        }

        return options;
    }

    private static ChromiumOptions<?> getPrefs() {
        return new ChromeOptions()
                .setImplicitWaitTimeout(Duration.ofMillis(300))
                .setPageLoadStrategy(PageLoadStrategy.EAGER)
                .setAcceptInsecureCerts(true)
                .setExperimentalOption("prefs", Map.of(
                        "intl.accept_languages", "ru,ru_RU",
                        "intl.selected_languages", "ru,ru_RU"
                ))
                .addArguments("--lang=en,en-US")
                .addArguments("--accept-lang=ru-RU")
                .addArguments("--accept-language=ru-RU")
                .addArguments("--disable-translate");
    }

}
