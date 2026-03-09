package ai.agent.swagger.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    private TaskType type = TaskType.ANALYZE;

    @NotBlank(message = "Description is required")
    private String description;
}

