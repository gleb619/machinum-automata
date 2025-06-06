package machinum.model;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ScenarioResult {

    private boolean success;
    private String error;
    private Object data;
    private String screenshot;
    private long executionTimeMs;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static ScenarioResult success(Object data, long executionTimeMs) {
        return ScenarioResult.builder()
                .success(true)
                .data(data)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    public static ScenarioResult failure(String error, String screenshot, long executionTimeMs) {
        return ScenarioResult.builder()
                .success(false)
                .error(error)
                .screenshot(screenshot)
                .executionTimeMs(executionTimeMs)
                .build();
    }

}
