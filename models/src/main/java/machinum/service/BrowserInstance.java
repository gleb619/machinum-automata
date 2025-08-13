package machinum.service;

import machinum.model.ChromeConfig;
import machinum.model.ScenarioResult;
import machinum.model.SessionInfo;
import org.openqa.selenium.WebDriver;

import java.util.Map;

public interface BrowserInstance {

    BrowserInstance initialize();

    ScenarioResult executeScript(String groovyScript, Map<String, Object> params, int timeoutSeconds);

    boolean isAlive();

    void cleanup();

    String getSessionId();

    ChromeConfig getConfig();

    SessionInfo getSessionInfo();

    WebDriver getDriver();

}