package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.model.SwaggerVectorSearchResponse;
import ai.agent.swagger.service.ai.AiChatService;
import ai.agent.swagger.service.ai.AiSwaggerGraphService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class SwaggerServiceAi {

    private final AiChatService aiChatService;
    private final VectorStorageService vectorStorageService;
    private final SwaggerServiceDocument swaggerServiceDocument;
    private final PromptBuilderService promptBuilderService;
    private final AiSwaggerGraphService aiSwaggerGraphService;

    public SwaggerServiceAi(AiChatService aiChatService,
                            VectorStorageService vectorStorageService,
                            SwaggerServiceDocument swaggerServiceDocument,
                            PromptBuilderService promptBuilderService, AiSwaggerGraphService aiSwaggerGraphService) {
        this.aiChatService = aiChatService;
        this.vectorStorageService = vectorStorageService;
        this.swaggerServiceDocument = swaggerServiceDocument;
        this.promptBuilderService = promptBuilderService;
        this.aiSwaggerGraphService = aiSwaggerGraphService;
    }

    /**
     * Только векторный поиск — находит наиболее релевантный документ без генерации ответа ИИ.
     * modelResponse будет null.
     */
    public SwaggerSearchResult findDocument(String query, String userId) {
        String extractKeywordsPrompt = promptBuilderService.getExtractKeywordsPrompt(query);
        String extractedKeywords = aiChatService.chat(userId, extractKeywordsPrompt);

        SwaggerVectorSearchResponse searchResult = vectorStorageService.search(extractedKeywords, userId);
        if (!searchResult.isPresent()) {
            log.info("findDocument: nothing found by keywords '{}'", extractedKeywords);
            return SwaggerSearchResult.builder().present(false).build();
        }

        SwaggerDocument swgDocument = swaggerServiceDocument.getSwaggerById(searchResult.getDocumentId()).orElseThrow();
        log.info("findDocument: found document id={}", swgDocument.getId());
        return SwaggerSearchResult.builder().present(true).document(swgDocument).build();
    }

    /**
     * Векторный поиск + аналитический ответ ИИ на основе найденного документа.
     */
    public SwaggerSearchResult search(String query, String userId) {
        String extractKeywordsPrompt = promptBuilderService.getExtractKeywordsPrompt(query);
        String extractedKeywords = aiChatService.chat(userId, extractKeywordsPrompt);

        SwaggerVectorSearchResponse searchResult = vectorStorageService.search(extractedKeywords, userId);
        SwaggerDocument swgDocument = null;
        String prompt;
        if (!searchResult.isPresent()) {
            log.info("Data not found in store by keywords {}", extractedKeywords);
            prompt = promptBuilderService.getVectorSearchNotFoundPrompt(query);
        } else {
            swgDocument = swaggerServiceDocument.getSwaggerById(searchResult.getDocumentId()).orElseThrow();
            log.info("Found relevant data in store by keywords {}", extractedKeywords);
            prompt = promptBuilderService.getDocumentChatAnalyze(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        }

        String response = aiChatService.chat(userId, prompt);

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
        SwaggerDocument swgDocument = swaggerServiceDocument.getSwaggerById(documentId).orElseThrow();
        String prompt;
        if (role.equalsIgnoreCase("analytic")) {
            prompt = promptBuilderService.getDocumentChatAnalyze(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        } else if (role.equalsIgnoreCase("programmer")) {
            prompt = promptBuilderService.getDocumentChatCode(query, swgDocument.getDocumentSummary(), swgDocument.getMethodSummary());
        } else {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
        try {
            return aiSwaggerGraphService.runGraphDocumentChat(userId, prompt, documentId).get("result").toString();
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> uploadSwagger(String swaggerContent, String userId, String name) {
        String swaggerMethodSummary = swaggerServiceDocument.resolveSwaggerMethodSummary(swaggerContent);
        String swaggerInfoSummary = swaggerServiceDocument.resolveSwaggerInfoSummary(swaggerContent);

        String swaggerSummaryPrompt = promptBuilderService.getDocumentChatUpload(swaggerInfoSummary, swaggerMethodSummary);
        String aiSummary = aiChatService.chat(userId, swaggerSummaryPrompt);

        // documentSummary = структурированная инфа (info/servers/security) + AI-резюме
        String documentSummary = swaggerInfoSummary.isBlank()
                ? aiSummary
                : swaggerInfoSummary + "\n\n" + aiSummary;

        String documentId = swaggerServiceDocument.saveDocument(
                documentSummary, swaggerMethodSummary, swaggerContent, userId, name);

        return Map.of("swagger_summary", documentSummary, "document_id", documentId);
    }
}

