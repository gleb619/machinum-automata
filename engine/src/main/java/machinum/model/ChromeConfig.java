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
    private Map<String, Object> preferences = new HashMap<>();

    @Builder.Default
    private boolean headless = true;

    @Builder.Default
    private int timeoutSeconds = 30;

    private String userAgent;

    @Builder.Default
    private int implicitWaitSeconds = 10;

    @Builder.Default
    private int pageLoadTimeoutSeconds = 30;

    @Builder.Default
    private int scriptTimeoutSeconds = 30;

}