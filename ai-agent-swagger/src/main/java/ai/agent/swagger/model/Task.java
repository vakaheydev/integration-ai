package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document(collection = "tasks")
public class Task {
    @Id
    private String id;
    private String documentId;
    private String userId;
    private TaskType type;
    private String description;

    @Builder.Default
    private TaskStatus status = TaskStatus.CREATED;
    private Stage currentStage;
    private String statusDescription;
    private Instant completedDatetime;
    private String result;

    @Builder.Default
    private List<Stage> stageHistory = new ArrayList<>();
}
