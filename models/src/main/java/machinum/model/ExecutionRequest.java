package machinum.model;

import lombok.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ExecutionRequest {

    private String script;

    @Builder.Default
    private int timeoutSeconds = 60;

    @Builder.Default
    private boolean captureScreenshot = false;

    @Builder.Default
    private boolean capturePageSource = false;

}
