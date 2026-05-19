package ai.agent.swagger.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    private TaskType type = TaskType.ANALYZE;

    /** Тип сценария (если задан — создаётся сценарная задача из цепочки подзадач) */
    private ScenarioType scenarioType;

    @NotBlank(message = "Description is required")
    private String description;

    /** Идентификатор модели ИИ (null = дефолтная из конфига) */
    private String modelName;
}

