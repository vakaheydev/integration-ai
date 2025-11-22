package ai.agent.swagger.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SwaggerSearchResult {
    private boolean present;
    private SwaggerDocument document;
    private String modelResponse;
}
