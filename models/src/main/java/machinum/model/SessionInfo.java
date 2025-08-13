package machinum.model;

import lombok.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SessionInfo {

    private String id;
    private long createdAt;
    private ChromeConfig config;
    private String status;
    private int executionCount;
    private long lastAccessTime;

    public boolean isActive() {
        return "active".equals(status);
    }

    public long getIdleTimeMs() {
        return System.currentTimeMillis() - lastAccessTime;
    }

}
