package ai.agent.swagger.service;

import ai.agent.swagger.config.CacheConfig;
import ai.agent.swagger.model.SwaggerDocument;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SwaggerServiceDocument {

    private final VectorStorageService vectorStorageService;
    private final DocumentStorageService documentStorageService;
    private final CacheManager cacheManager;

    public SwaggerServiceDocument(VectorStorageService vectorStorageService,
                                  DocumentStorageService documentStorageService,
                                  CacheManager cacheManager) {
        this.vectorStorageService = vectorStorageService;
        this.documentStorageService = documentStorageService;
        this.cacheManager = cacheManager;
    }

    @Cacheable(value = CacheConfig.SWAGGER_DOCUMENTS_CACHE, key = "#a0")
    public Optional<SwaggerDocument> getSwaggerById(String id) {
        log.debug("Cache miss for document id={}, loading from DB", id);
        return documentStorageService.findById(id);
    }

    public List<SwaggerDocument> getSwaggersByUserId(String userId) {
        return documentStorageService.findByUserId(userId);
    }

    public String saveDocument(String swaggerSummaryResponse, String swaggerMethodSummary,
                               String swaggerContent, String userId, String name) {
        Map<String, String> metadata = Map.of(
                "swagger_summary", swaggerMethodSummary,
                "user_id", userId
        );
        String documentId = vectorStorageService.saveDocument(swaggerSummaryResponse, metadata);
        SwaggerDocument swaggerDocument = SwaggerDocument.builder()
                .id(documentId)
                .userId(userId)
                .name(name)
                .documentSummary(swaggerSummaryResponse)
                .methodSummary(swaggerMethodSummary)
                .content(swaggerContent)
                .build();
        documentStorageService.saveDocument(swaggerDocument);
        var cache = cacheManager.getCache(CacheConfig.SWAGGER_DOCUMENTS_CACHE);
        if (cache != null) {
            cache.put(documentId, Optional.of(swaggerDocument));
            log.debug("Cached document id={} after save", documentId);
        }
        return documentId;
    }

    @CacheEvict(value = CacheConfig.SWAGGER_DOCUMENTS_CACHE, key = "#a0")
    public void deleteDocumentById(String id) {
        log.debug("Evicting cache for document id={}", id);
        documentStorageService.deleteDocument(id);
        vectorStorageService.deleteById(id);
    }

    public String resolveSwaggerMethodSummary(String swaggerContent) {
        OpenAPI openAPI = parseOpenAPI(swaggerContent);

        StringBuilder sb = new StringBuilder();
        for (String path : openAPI.getPaths().keySet()) {
            PathItem pathItem = openAPI.getPaths().get(path);
            Map<PathItem.HttpMethod, Operation> operationsMap = pathItem.readOperationsMap();
            operationsMap.forEach((httpMethod, operation) ->
                    sb.append(httpMethod).append(" ").append(path)
                            .append(": ").append(operation.getDescription()).append("\n"));
        }

        return sb.toString();
    }

    public Optional<String> extractMethod(String swaggerContent, String apiPath, String httpMethod) {
        OpenAPI openAPI;
        try {
            openAPI = parseOpenAPI(swaggerContent);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse OpenAPI content: {}", e.getMessage());
            return Optional.empty();
        }

        if (openAPI.getPaths() == null) {
            log.warn("OpenAPI document has no paths");
            return Optional.empty();
        }

        PathItem pathItem = openAPI.getPaths().get(apiPath);
        if (pathItem == null) {
            log.warn("Path '{}' not found in OpenAPI document", apiPath);
            return Optional.empty();
        }

        PathItem.HttpMethod method;
        try {
            method = PathItem.HttpMethod.valueOf(httpMethod.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown HTTP method: {}", httpMethod);
            return Optional.empty();
        }

        Operation operation = pathItem.readOperationsMap().get(method);
        if (operation == null) {
            log.warn("Method {} not found for path '{}'", httpMethod, apiPath);
            return Optional.empty();
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append(httpMethod.toUpperCase()).append(" ").append(apiPath).append("\n");

            if (operation.getSummary() != null) {
                sb.append("Summary: ").append(operation.getSummary()).append("\n");
            }
            if (operation.getDescription() != null) {
                sb.append("Description: ").append(operation.getDescription()).append("\n");
            }
            if (operation.getOperationId() != null) {
                sb.append("OperationId: ").append(operation.getOperationId()).append("\n");
            }
            if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
                sb.append("Parameters:\n");
                operation.getParameters().forEach(param ->
                        sb.append("  - ").append(param.getName())
                                .append(" (").append(param.getIn()).append(")")
                                .append(Boolean.TRUE.equals(param.getRequired()) ? " [required]" : "")
                                .append(param.getDescription() != null ? ": " + param.getDescription() : "")
                                .append("\n"));
            }
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                sb.append("Request body:\n");
                operation.getRequestBody().getContent().forEach((mediaType, content) -> {
                    sb.append("  Content-Type: ").append(mediaType).append("\n");
                    if (content.getSchema() != null) {
                        String ref = content.getSchema().get$ref();
                        if (ref != null) {
                            String schemaName = resolveSchemaName(ref);
                            sb.append("  Schema: ").append(schemaName);
                        } else if (content.getSchema().getType() != null) {
                            sb.append("  Schema type: ").append(content.getSchema().getType()).append("\n");
                        }
                    }
                });
            }
            if (operation.getResponses() != null) {
                sb.append("Responses:\n");
                operation.getResponses().forEach((statusCode, response) ->
                        sb.append("  ").append(statusCode)
                                .append(response.getDescription() != null ? ": " + response.getDescription() : "")
                                .append("\n"));
            }
            return Optional.of(sb.toString());
        } catch (Exception e) {
            log.error("Error serializing operation details for {} {}", httpMethod, apiPath, e);
            return Optional.empty();
        }
    }

    /**
     * Извлекает схему из OpenAPI документа по JSON Pointer-пути.
     * Поддерживает пути вида "#/components/schemas/Pet" или просто "Pet".
     */
    public Optional<String> extractSchema(String swaggerContent, String schemaPath) {
        OpenAPI openAPI;
        try {
            openAPI = parseOpenAPI(swaggerContent);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse OpenAPI content: {}", e.getMessage());
            return Optional.empty();
        }

        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            log.warn("OpenAPI document has no components/schemas");
            return Optional.empty();
        }

        String schemaName = resolveSchemaName(schemaPath);
        @SuppressWarnings("rawtypes")
        Schema schema = openAPI.getComponents().getSchemas().get(schemaName);
        if (schema == null) {
            log.warn("Schema '{}' not found in components/schemas", schemaName);
            return Optional.empty();
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Schema: ").append(schemaName).append("\n");

            if (schema.getDescription() != null) {
                sb.append("Description: ").append(schema.getDescription()).append("\n");
            }
            if (schema.getType() != null) {
                sb.append("Type: ").append(schema.getType()).append("\n");
            }
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                sb.append("Required fields: ").append(schema.getRequired()).append("\n");
            }
            if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                sb.append("Properties:\n");
                @SuppressWarnings("unchecked")
                Map<String, Schema> properties = schema.getProperties();
                properties.forEach((propName, propSchema) -> {
                    sb.append("  - ").append(propName);
                    if (propSchema.getType() != null) {
                        sb.append(" (").append(propSchema.getType()).append(")");
                    }
                    if (propSchema.get$ref() != null) {
                        sb.append(" (ref: ").append(resolveSchemaName(propSchema.get$ref())).append(")");
                    }
                    if (propSchema.getDescription() != null) {
                        sb.append(": ").append(propSchema.getDescription());
                    }
                    sb.append("\n");
                });
            }
            if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
                sb.append("Enum values: ").append(schema.getEnum()).append("\n");
            }
            return Optional.of(sb.toString());
        } catch (Exception e) {
            log.error("Error serializing schema '{}'", schemaName, e);
            return Optional.empty();
        }
    }

    /**
     * Общая логика для AI-инструментов: получает документ по id и проверяет принадлежность пользователю.
     * Возвращает документ или бросает исключение с понятным сообщением для AI-агента.
     */
    public SwaggerDocument getDocumentAndValidateOwner(String documentId, String userId) {
        SwaggerDocument document = documentStorageService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document with id " + documentId + " not found"));

        if (!document.getUserId().equals(userId)) {
            throw new SecurityException("Document with id " + documentId
                    + " doesn't belong to user with id " + userId + ". Access denied.");
        }
        return document;
    }

    // --- private ---

    private OpenAPI parseOpenAPI(String swaggerContent) {
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(swaggerContent).getOpenAPI();
        if (openAPI == null) {
            throw new IllegalArgumentException("Failed to parse OpenAPI content");
        }
        return openAPI;
    }

    private String resolveSchemaName(String schemaPath) {
        if (schemaPath.contains("/")) {
            return schemaPath.substring(schemaPath.lastIndexOf('/') + 1);
        }
        return schemaPath;
    }
}

