package ai.agent.swagger.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveInputRequest {
    @NotBlank(message = "message must not be blank")
    private String message;
}
