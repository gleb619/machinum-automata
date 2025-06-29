package machinum.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Script {

    private String id;
    private String name;
    private String text;
    private Integer timeout;
    @Builder.Default
    private List<Map<String, Object>> uiConfig = new ArrayList<>();

}
