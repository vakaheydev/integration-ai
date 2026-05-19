package ai.agent.swagger.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String name;
    @Size(max = 10_000, message = "Document summary can't be more than 10.000 characters")
    private String documentSummary;
    @Size(max = 30_000, message = "Method summary can't be more than 30.000 characters")
    private String methodSummary;
    @Size(max = 5_000_000, message = "Content can't be more than 5.000.000 characters")
    private String content;
}
