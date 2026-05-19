package ai.agent.swagger.service.ai;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.service.SwaggerServiceAi;
import ai.agent.swagger.service.SwaggerServiceDocument;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SwaggerTools {
    private final SwaggerServiceDocument swaggerServiceDocument;
    private final SwaggerServiceAi swaggerServiceAi;

    public SwaggerTools(SwaggerServiceDocument swaggerServiceDocument, SwaggerServiceAi swaggerServiceAi) {
        this.swaggerServiceDocument = swaggerServiceDocument;
        this.swaggerServiceAi = swaggerServiceAi;
    }

    @Tool("Метод для получения информации об OpenAPI документах текущего пользователя. На вход не принимает параметров. На выходе - строка с перечнем документов, их краткое описание и их id.")
    String getUserSwaggerDocuments() {
        log.debug("AI tool getUserDocuments() called");
        try {
            List<String> documents = getUserDocumentIds();
            return documents.isEmpty() ? "No documents found for the current user." : String.join("\n", documents);
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("getUserDocuments failed: {}", e.getMessage());
            return e.getMessage();
        }
    }

    @Tool("Метод для получения информации о методе API из swagger документа. На вход приходит id документа, apiPath и httpMethod. На выходе - строка с информацией о методе или сообщение об ошибке.")
    String getSwaggerMethod(String documentId, String apiPath, String httpMethod) {
        log.debug("AI tool getSwaggerMethod() called with documentId '{}', apiPath '{}', httpMethod '{}'", documentId, apiPath, httpMethod);
        try {
            SwaggerDocument document = getDocumentForCurrentUser(documentId);
            log.info("Retrieving swagger method for documentId {}, apiPath {}, httpMethod {}", documentId, apiPath, httpMethod);
            return swaggerServiceDocument.extractMethod(document.getContent(), apiPath, httpMethod)
                    .map(method -> "Method definition for " + httpMethod.toUpperCase() + " " + apiPath + ":\n" + method)
                    .orElse("Method " + httpMethod.toUpperCase() + " " + apiPath + " not found in document " + documentId);
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("getSwaggerMethod failed: {}", e.getMessage());
            return e.getMessage();
        }
    }

    @Tool("Метод для получения информации о схеме данных из OpenAPI документа. Принимает id документа и путь к схеме (например, '#/components/schemas/Pet' или просто 'Pet'). Возвращает описание схемы или сообщение об ошибке.")
    String getSwaggerSchema(String documentId, String schemaPath) {
        log.debug("AI tool getSwaggerSchema() called with documentId {}, schemaPath {}", documentId, schemaPath);
        try {
            SwaggerDocument document = getDocumentForCurrentUser(documentId);
            log.info("Retrieving swagger schema for documentId {}, schemaPath {}", documentId, schemaPath);
            return swaggerServiceDocument.extractSchema(document.getContent(), schemaPath)
                    .map(schema -> "Schema definition for '" + schemaPath + "':\n" + schema)
                    .orElse("Schema '" + schemaPath + "' not found in document " + documentId);
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("getSwaggerSchema failed: {}", e.getMessage());
            return e.getMessage();
        }
    }

    @Tool("Метод для получения подробной информации о swagger документе. Принимает id документа. Возвращает название, краткое описание и сводку методов документа.")
    String getSwaggerDocumentInfo(String documentId) {
        log.debug("AI tool getSwaggerDocumentInfo() called with documentId '{}'", documentId);
        try {
            SwaggerDocument document = getDocumentForCurrentUser(documentId);
            return "Document name: " + document.getName() + "\n" +
                    "Document summary: " + (document.getDocumentSummary() != null ? document.getDocumentSummary() : "N/A") + "\n" +
                    "Method summary: " + (document.getMethodSummary() != null ? document.getMethodSummary() : "N/A");
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("getSwaggerDocumentInfo failed: {}", e.getMessage());
            return e.getMessage();
        }
    }

    @Tool("Семантический (векторный) поиск по OpenAPI документам текущего пользователя. Принимает поисковый запрос на естественном языке. Возвращает id и название наиболее релевантного документа, либо сообщение что ничего не найдено.")
    String searchSwaggerDocuments(String query) {
        log.debug("AI tool searchSwaggerDocuments() called with query '{}'", query);
        try {
            String userId = SwaggerToolsContext.get();
            SwaggerSearchResult result = swaggerServiceAi.findDocument(query, userId);
            if (!result.isPresent()) {
                return "No relevant documents found for query: " + query;
            }
            SwaggerDocument doc = result.getDocument();
            return "Found document id=" + doc.getId() + ", name=" + doc.getName() + ", summary=" + (doc.getDocumentSummary() != null ? doc.getDocumentSummary() : "N/A");
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("searchSwaggerDocuments failed: {}", e.getMessage());
            return e.getMessage();
        }
    }

    private SwaggerDocument getDocumentForCurrentUser(String documentId) {
        String userId = SwaggerToolsContext.get();
        return swaggerServiceDocument.getDocumentAndValidateOwner(documentId, userId);
    }

    private List<String> getUserDocumentIds() {
        String userId = SwaggerToolsContext.get();
        return swaggerServiceDocument.getSwaggersByUserId(userId).stream()
                .map(x -> x.getId() + ": " + x.getName() + (x.getDocumentSummary() != null ? " - " + x.getDocumentSummary() : ""))
                .collect(Collectors.toList());
    }
}
