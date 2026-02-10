package ai.agent.swagger.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    @NotBlank(message = "Query is required")
    private String query;

    @Pattern(regexp = "^(analytic|programmer)$", message = "Role must be either 'analytic' or 'programmer'")
    private String role = "analytic";
}

