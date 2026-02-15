package ai.agent.swagger.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class SwaggerVectorSearchResponse {
    private boolean present;
    private String content;
    private String documentId;
    private Map<String, Object> metadata;
}
