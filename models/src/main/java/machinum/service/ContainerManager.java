package machinum.service;

import machinum.model.ChromeConfig;
import machinum.model.ScenarioResult;
import machinum.model.SessionInfo;

import java.util.List;
import java.util.function.Function;

public interface ContainerManager {

    void init();

    String createInstance(ChromeConfig config);

    BrowserInstance getInstance(String sessionId);

    BrowserInstance getLastInstance();

    void terminateInstance(String sessionId);

    List<SessionInfo> getActiveSessions();

    void shutdown();

    ScenarioResult execute(Function<BrowserInstance, ScenarioResult> scenario);

}