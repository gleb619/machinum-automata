package machinum.model;

import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ChromeConfig {

    @Builder.Default
    private String version = "latest";

    @Builder.Default
    private List<String> arguments = new ArrayList<>();

    @Builder.Default
    private boolean headless = false;

    @Builder.Default
    private int timeoutSeconds = 30;

    private String userAgent;

    @Builder.Default
    private int implicitWaitSeconds = 10;

    @Builder.Default
    private int pageLoadTimeoutSeconds = 30;

    @Builder.Default
    private int scriptTimeoutSeconds = 30;

    @Builder.Default
    private boolean videoRecordingEnabled = false;

    @Builder.Default
    private String recordingMode = "RECORD_ALL";

    @Builder.Default
    @Deprecated(forRemoval = true)
    private String recordingDirectory = "./recordings";

    @Builder.Default
    private String reportDirectory = "./html-reports";

    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    @Builder.Default
    private Map<String, Object> experimentalOptions = new HashMap<>();

    @Builder.Default
    private boolean acceptInsecureCerts = true;

    @Builder.Default
    private String pageLoadStrategy = "EAGER";

    public static ChromeConfig defaultOne(Boolean videoRecordingEnabled) {
        return ChromeConfig.builder()
                .arguments(List.of(
                        "--window-size=1920,1080",
                        "--lang=en,en-US",
                        "--accept-lang=ru-RU",
                        "--accept-language=ru-RU",
                        "--disable-translate"
                ))
                .environmentVariables(Map.of(
                        "LANG", "ru_RU",
                        "LANGUAGE", "ru_RU"
                ))
                .experimentalOptions(Map.of("prefs", Map.of(
                        "intl.accept_languages", "ru,ru_RU",
                        "intl.selected_languages", "ru,ru_RU"
                )))
                .videoRecordingEnabled(videoRecordingEnabled)
                .build();
    }

    public static ChromeConfig defaultOne() {
        return defaultOne(false);
    }

}
