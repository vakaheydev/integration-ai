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

    public String chatByDocumentId(String documentId, String userId, String query, String role) {
        SwaggerDocument swgDocument = getSwaggerById(documentId).orElseThrow();
        String prompt;
        if (role.equalsIgnoreCase("analytic")) {
            prompt = "Ты - лучший в мире системный аналитик.\n" +
                "Проанализируй этот документ и исходя из полученной информации ответь ему максимально кратко. Максимум 5 предложений.\n";
        } else if (role.equalsIgnoreCase("programmer")) {
            prompt = "Ты - лучший в мире программист.\n" +
                    "Проанализируй этот документ и исходя из полученной информации напиши код на Python. Код должен быть легкочитаемым и поддерживаемым, " +
                    "соответствовать всем принципам SOLID и паттернам чистой архитектуры. Необязательно писать все в одном файле, можешь разделять логику, если требуется.";
        } else {
            throw new IllegalArgumentException("No such role: " + role);
        }
        prompt += "Пользователь тебя спрашивает - " + query + "\n" +
                "Вот краткое описание OpenAPI документа: -----------\n" + swgDocument.getDocumentSummary() + "-----------\n" +
                "Вот его методы:: -----------\n" + swgDocument.getMethodSummary() + "-----------\n";

//        prompt += """
//                !!! STRICT OUTPUT RULES !!!
//                1. Your entire response MUST be ONLY a v\n" + swgDocument.getDocumentSummary() + "-----------\n" +
//                "Вот его методы: -----------\n" + swgDocument.getMetalid JSON object.
//                2. Do NOT use markdown code fences.
//                3. Do NOT write "json", explanations, comments, or formatting hints.
//                4. The first character MUST be "{" and the last MUST be "}".
//                5. If you violate these rules — the output is invalid.
//                """;
//        String template = """
//
//            SYSTEM: You are a JSON-only assistant. Strict rules below.
//
//            TASK:
//            You are an expert <ROLE> (analytic | programmer) analyzing an OpenAPI summary and methods.
//            User question: "<USER_QUERY>"
//
//            CONTEXT:
//            Document summary:
//            <SWG_DOCUMENT_SUMMARY>
//            Methods summary:
//            <SWG_METHOD_SUMMARY>
//
//            OUTPUT SPEC (MUST follow exactly):
//            - If you can fully and reliably answer using ONLY the provided CONTEXT, return EXACTLY this JSON (and nothing else):
//              {"answer":"<concise answer text>"}
//              • "answer" — кратко, максимум 5 предложений для analytic; для programmer — возвращай только код inside string OR, если нужен файл structure, return code as string.
//
//            - If the provided CONTEXT IS NOT SUFFICIENT to answer reliably, you MUST return EXACTLY this JSON (and nothing else):
//              {
//                "need_more_context": true,
//                "required": [
//                  {
//                    "type": "<one of: path, schema, parameter, example, description>",
//                    "method": "<HTTP method or null>",
//                    "path": "<API path or null>",
//                    "schemaName": "<name if type=schema or null>",
//                    "reason": "<short reason why this piece is needed>"
//                  },
//                  ...
//                ]
//              }
//
//            - The "required" array must contain 1..N objects describing **конкретные куски документа**, enough for backend to fetch them automatically (no vague text).
//            - Fields that are not applicable must be null.
//            - Do NOT return any additional fields.
//
//            STRICT OUTPUT RULES:
//            1) Entire response MUST be ONLY a valid JSON object (first char '{', last char '}'), no markdown, no explanation, no extra text.
//            2) Do NOT ask clarifying questions in natural language.
//            3) Do NOT output partial JSON or escape JSON inside markdown.
//            4) If you are uncertain about answer completeness, return the "need_more_context" JSON above (do NOT guess).
//
//            EXAMPLES (must follow formats exactly):
//
//            Example A — enough context (analytic):
//            Input: question + context that contains path /payments POST and description.
//            Output:
//            {"answer":"Этот API поддерживает создание платежа через POST /payments. Требуется поля amount и currency. Ограничения: сумма > 0. Максимум 3 попытки при ошибке."}
//
//            Example B — not enough context (must request specific pieces):
//            Output:
//            {
//              "need_more_context": true,
//              "required": [
//                {"type":"path","method":"POST","path":"/payments","schemaName":null,"reason":"Нужна структура request body (поля и типы)"},
//                {"type":"schema","method":null,"path":null,"schemaName":"Payment","reason":"Нужны поля схемы Payment для валидации"}
//              ]
//            }
//
//            END: If you violate the rules, your output will be considered invalid. Now produce the JSON response for the given question.
//
//        """;
//        String prompt = template
//                .replace("<ROLE>", role)
//                .replace("<USER_QUERY>", query)
//                .replace("<SWG_DOCUMENT_SUMMARY>", swgDocument.getDocumentSummary())
//                .replace("<SWG_METHOD_SUMMARY>", swgDocument.getMethodSummary());

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
