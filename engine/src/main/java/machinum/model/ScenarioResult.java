package machinum.model;

import lombok.*;

import java.time.Duration;
import java.time.Instant;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ScenarioResult {

    private boolean success;
    private String error;
    private Object data;
    private String screenshot;
    private String videoFile;
    private String htmlFile;
    private long executionTime;

    public static ScenarioResult success(Object data, String video, Instant start) {
        return ScenarioResult.builder()
                .success(true)
                .data(data)
                .executionTime(Duration.between(start, Instant.now()).toSeconds())
                .videoFile(video)
                .build();
    }

    public static ScenarioResult failure(String error, String screenshot, String video, String html, Instant start) {
        return ScenarioResult.builder()
                .success(false)
                .error(error)
                .screenshot(screenshot)
                .executionTime(Duration.between(start, Instant.now()).toSeconds())
                .videoFile(video)
                .htmlFile(html)
                .build();
    }

}
