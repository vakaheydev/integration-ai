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

    public SwaggerService(AiChatService aiChatService, VectorStorageService vectorStorageService,
                          DocumentStorageService documentStorageService) {
        this.aiChatService = aiChatService;
        this.vectorStorageService = vectorStorageService;
        this.documentStorageService = documentStorageService;
    }

    public SwaggerSearchResult search(String query) {
        String promptedQuery = aiChatService.chat(
                "Ты - лучший в мире системный аналитик.\n" +
                        "Твоя задача перефразировать запрос максимально кратко, извлечь из него несколько ключевых слов. Максимум 10 слов.\n" +
                        "Каждое слово поставь в именительный падеж, чтобы повысить точность семантического поиска.\n" +
                        "В ответе должно быть ТОЛЬКО перечисление ключевых слов через запятую, например:" +
                        "Утюг, покупка, продажа.\n" +
                        "Вот запрос:\n" + query
        );

        SwaggerVectorSearchResponse searchResult = vectorStorageService.search(promptedQuery);
        SwaggerDocument swgDocument = null;
        String prompt;
        if (!searchResult.isPresent()) {
            log.info("Data not found in store");
            prompt = "Ты - лучший в мире системный аналитик." +
                    "Пользователь тебя спрашивает - " + query + "\n" +
                    "К сожалению, в базе данных не нашлось OpenAPI документа по запросу пользователя." +
                    "Скажи ему об этом, попроси перефразировать запрос. Ответь очень коротко, максимум два предложения.\n";
        } else {
            swgDocument = getSwaggerById(searchResult.getDocumentId()).orElseThrow();
            log.info("Found relevant data in store");
            prompt = "Ты - лучший в мире системный аналитик.\n" +
                    "Пользователь тебя спрашивает - " + query + "\n" +
                    "Вот краткое описание OpenAPI документа: -----------\n" + swgDocument.getDocumentSummary() + "-----------\n" +
                    "Вот его методы:: -----------\n" + swgDocument.getMethodSummary() + "-----------\n" +
                    "Проанализируй этот документ и исходя из полученной информации ответь ему максимально кратко. Максимум 5 предложений.";
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

    public String chatByDocumentId(String documentId, String userId, String query) {
        SwaggerDocument swgDocument = getSwaggerById(documentId).orElseThrow();
        String prompt = "Ты - лучший в мире системный аналитик.\n" +
                "Пользователь тебя спрашивает - " + query + "\n" +
                "Вот краткое описание OpenAPI документа: -----------\n" + swgDocument.getDocumentSummary() + "-----------\n" +
                "Вот его методы:: -----------\n" + swgDocument.getMethodSummary() + "-----------\n" +
                "Проанализируй этот документ и исходя из полученной информации ответь ему максимально кратко. Максимум 5 предложений.";
        String response = aiChatService.chat(prompt);
        return response;
    }

    public Optional<SwaggerDocument> getSwaggerById(String id) {
        return documentStorageService.findById(id);
    }

    public List<SwaggerDocument> getSwaggersByUserId(String userId) {
        return documentStorageService.findByUserId(userId);
    }

    public Map<String, String> uploadSwagger(String swaggerContent, String userId) {
        String swaggerSummary = resolveSwaggerSummary(swaggerContent);
        String swaggerSummaryResponse = aiChatService.chat(
                "Ты - лучший в мире системный аналитик. Вот краткое описание OpenAPI документа: -----------" + swaggerSummary + "-----------\n" +
                        "Проанализируй его и предоставь очень краткую сводку (максимум 5 предложений) в которой ответь на вопросы - что это за АПИ? Что можно делать с помощью нее?\n" +
                        "Ни в коем случае не перечисляй все доступные методы! Обобщи в 1 предложение функциональность АПИ."
        );
        Map<String, String> metadata = new HashMap<>();

        metadata.put("swagger_summary", swaggerSummary);
        metadata.put("user_id", userId);

        String documentId = vectorStorageService.saveDocument(swaggerSummaryResponse, metadata);
        SwaggerDocument swaggerDocument = SwaggerDocument.builder()
                .id(documentId)
                .userId(userId)
                .documentSummary(swaggerSummaryResponse)
                .methodSummary(swaggerSummary)
                .content(swaggerContent)
                .build();
        documentStorageService.saveDocument(swaggerDocument);
        return Map.of("swagger_summary", swaggerSummaryResponse, "document_id", documentId);
    }

    public void deleteDocumentById(String id) {
        documentStorageService.deleteDocument(id);
        vectorStorageService.deleteById(id);
    }

    public String resolveSwaggerSummary(String swaggerContent) {
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
