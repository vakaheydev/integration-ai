package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SwaggerService {

    private final SwaggerServiceAi swaggerServiceAi;
    private final SwaggerServiceDocument swaggerServiceDocument;

    public SwaggerService(SwaggerServiceAi swaggerServiceAi,
                          SwaggerServiceDocument swaggerServiceDocument) {
        this.swaggerServiceAi = swaggerServiceAi;
        this.swaggerServiceDocument = swaggerServiceDocument;
    }

    public SwaggerSearchResult search(String query, String userId) {
        return swaggerServiceAi.search(query, userId);
    }

    public String chatByDocumentId(String documentId, String userId, String query, String role) {
        return swaggerServiceAi.chatByDocumentId(documentId, userId, query, role);
    }

    public Map<String, String> uploadSwagger(String swaggerContent, String userId, String name) {
        return swaggerServiceAi.uploadSwagger(swaggerContent, userId, name);
    }

    public Optional<SwaggerDocument> getSwaggerById(String id) {
        return swaggerServiceDocument.getSwaggerById(id);
    }

    public List<SwaggerDocument> getSwaggersByUserId(String userId) {
        return swaggerServiceDocument.getSwaggersByUserId(userId);
    }

    public void deleteDocumentById(String id) {
        swaggerServiceDocument.deleteDocumentById(id);
    }

    public String resolveSwaggerMethodSummary(String swaggerContent) {
        return swaggerServiceDocument.resolveSwaggerMethodSummary(swaggerContent);
    }
}
