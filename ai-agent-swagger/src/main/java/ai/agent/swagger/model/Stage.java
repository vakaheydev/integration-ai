package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Stage {
    private int id;
    private String name;
    private String description;
    private Instant instantStart;
    private Instant instantEnd;

    public Duration getDuration() {
        if (instantStart == null || instantEnd == null) {
            return null;
        }
        return Duration.between(instantStart, instantEnd);
    }
}
