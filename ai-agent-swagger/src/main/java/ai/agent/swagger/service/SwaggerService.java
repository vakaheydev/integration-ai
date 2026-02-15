package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.model.SwaggerVectorSearchResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SwaggerService {
    private final AiChatService aiChatService;
    private final VectorStorageService vectorStorageService;
    private final DocumentStorageService documentStorageService;
    private final PromptBuilderService promptBuilderService;

    public SwaggerService(AiChatService aiChatService, VectorStorageService vectorStorageService,
                          DocumentStorageService documentStorageService, PromptBuilderService promptBuilderService) {
        this.aiChatService = aiChatService;
        this.vectorStorageService = vectorStorageService;
        this.documentStorageService = documentStorageService;
        this.promptBuilderService = promptBuilderService;
    }

    public SwaggerSearchResult search(String query) {
        String extractKeywordsPrompt = promptBuilderService.getExtractKeywordsPrompt(query);
        String extractedKeywords = aiChatService.chat(extractKeywordsPrompt);

        SwaggerVectorSearchResponse searchResult = vectorStorageService.search(extractedKeywords);
        SwaggerDocument swgDocument = null;
        String prompt;
        if (!searchResult.isPresent()) {
            log.info("Data not found in store by keywords {}", extractedKeywords);
            prompt = promptBuilderService.getVectorSearchNotFoundPrompt(query);
        } else {
            swgDocument = getSwaggerById(searchResult.getDocumentId()).orElseThrow();
            log.info("Found relevant data in store by keywords {}", extractedKeywords);
            prompt = promptBuilderService.getDocumentChatAnalyze(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        }

        String response = aiChatService.chat(prompt);

        if (!searchResult.isPresent()) {
            return SwaggerSearchResult.builder()
                    .present(false)
                    .modelResponse(response)
                    .build();
        }

        return SwaggerSearchResult.builder()
                .present(true)
                .modelResponse(response)
                .document(swgDocument)
                .build();
    }

    public SwaggerSearchResult search(String query, String userId) {
        String extractKeywordsPrompt = promptBuilderService.getExtractKeywordsPrompt(query);
        String extractedKeywords = aiChatService.chat(extractKeywordsPrompt);

        SwaggerVectorSearchResponse searchResult = vectorStorageService.search(extractedKeywords, userId);
        SwaggerDocument swgDocument = null;
        String prompt;
        if (!searchResult.isPresent()) {
            log.info("Data not found in store by keywords {}", extractedKeywords);
            prompt = promptBuilderService.getVectorSearchNotFoundPrompt(query);
        } else {
            swgDocument = getSwaggerById(searchResult.getDocumentId()).orElseThrow();
            log.info("Found relevant data in store by keywords {}", extractedKeywords);
            prompt = promptBuilderService.getDocumentChatAnalyze(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        }

        String response = aiChatService.chat(prompt);

        if (!searchResult.isPresent()) {
            return SwaggerSearchResult.builder()
                    .present(false)
                    .modelResponse(response)
                    .build();
        }

        return SwaggerSearchResult.builder()
                .present(true)
                .modelResponse(response)
                .document(swgDocument)
                .build();
    }

    public String chatByDocumentId(String documentId, String userId, String query, String role) {
        SwaggerDocument swgDocument = getSwaggerById(documentId).orElseThrow();
        String prompt = "";
        if (role.equalsIgnoreCase("analytic")) {
            prompt += promptBuilderService.getDocumentChatAnalyze(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        } else if (role.equalsIgnoreCase("programmer")) {
            prompt += promptBuilderService.getDocumentChatCode(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        } else {
            throw new IllegalArgumentException("Unknown role: " + role);
        }

        String response = aiChatService.chat(prompt);
        return response;
    }

    public Optional<SwaggerDocument> getSwaggerById(String id) {
        return documentStorageService.findById(id);
    }

    public List<SwaggerDocument> getSwaggersByUserId(String userId) {
        return documentStorageService.findByUserId(userId);
    }

    public Map<String, String> uploadSwagger(String swaggerContent, String userId, String name) {
        String swaggerMethodSummary = resolveSwaggerMethodSummary(swaggerContent);
        String swaggerSummaryPrompt = promptBuilderService.getDocumentChatUpload(swaggerMethodSummary);
        String swaggerSummaryResponse = aiChatService.chat(swaggerSummaryPrompt);
        Map<String, String> metadata = new HashMap<>();

        metadata.put("swagger_summary", swaggerMethodSummary);
        metadata.put("user_id", userId);

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
        return Map.of("swagger_summary", swaggerSummaryResponse, "document_id", documentId);
    }

    public void deleteDocumentById(String id) {
        documentStorageService.deleteDocument(id);
        vectorStorageService.deleteById(id);
    }

    public String resolveSwaggerMethodSummary(String swaggerContent) {
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(swaggerContent).getOpenAPI();
        StringBuilder sb = new StringBuilder();
        for (String path : openAPI.getPaths().keySet()) {
            PathItem pathItem = openAPI.getPaths().get(path);
            Map<PathItem.HttpMethod, Operation> operationsMap = pathItem.readOperationsMap();
            operationsMap.forEach((httpMethod, operation) -> sb.append(httpMethod).append(" ").append(path).append(": ").append(operation.getDescription()).append("\n"));
        }

        return sb.toString();
    }
}
