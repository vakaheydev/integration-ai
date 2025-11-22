package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "documents")
public class SwaggerDocument {
    @Id
    private String id;
    private String userId;
    private String documentSummary;
    private String methodSummary;
    private String content;
}
