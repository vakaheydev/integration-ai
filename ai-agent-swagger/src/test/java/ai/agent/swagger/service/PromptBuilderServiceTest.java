package ai.agent.swagger.service;

import ai.agent.swagger.config.SwaggerPromptsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FT-005/FT-009/FT-010 - Валидация Swagger и генерация промптов")
public class PromptBuilderServiceTest {

    private PromptBuilderService promptBuilderService;

    @BeforeEach
    public void setUp() {
        SwaggerPromptsProperties props = new SwaggerPromptsProperties();

        SwaggerPromptsProperties.Roles roles = new SwaggerPromptsProperties.Roles();
        roles.setAnalyst("SYSTEM: You are the best expert system analyst.");
        roles.setProgrammer("SYSTEM: You are the best expert developer.");
        props.setRoles(roles);

        SwaggerPromptsProperties.Actions.DocumentChat docChat = new SwaggerPromptsProperties.Actions.DocumentChat();
        docChat.setAnalyze("Question: ${userPrompt}\nSummary: ${swaggerSummary}\nMethods: ${swaggerMethodSummary}");
        docChat.setUpload("Methods: ${swaggerMethodSummary}");
        docChat.setCode("Question: ${userPrompt}\nSummary: ${swaggerSummary}\nMethods: ${swaggerMethodSummary}");

        SwaggerPromptsProperties.Actions.VectorSearch vectorSearch = new SwaggerPromptsProperties.Actions.VectorSearch();
        vectorSearch.setExtractKeyWords("Extract keywords from: ${userPrompt}");
        vectorSearch.setNotFound("Not found for: ${userPrompt}");

        SwaggerPromptsProperties.Actions actions = new SwaggerPromptsProperties.Actions();
        actions.setDocumentChat(docChat);
        actions.setVectorSearch(vectorSearch);
        props.setActions(actions);

        promptBuilderService = new PromptBuilderService(props);
    }

    @Test
    @DisplayName("FT-009: все плейсхолдеры заменяются корректно в analyze-промпте")
    public void testGetDocumentChatAnalyze_allPlaceholdersReplaced() {
        String prompt = promptBuilderService.getDocumentChatAnalyze(
                "What endpoints exist?",
                "This is a user management API",
                "GET /users, POST /users"
        );

        assertNotNull(prompt);
        assertFalse(prompt.isEmpty(), "Промпт не должен быть пустым");
        assertTrue(prompt.contains("What endpoints exist?"), "Должен содержать вопрос пользователя");
        assertTrue(prompt.contains("This is a user management API"), "Должен содержать summary");
        assertTrue(prompt.contains("GET /users"), "Должен содержать список методов");
        assertFalse(prompt.contains("${userPrompt}"), "Плейсхолдеры не должны остаться");
        assertFalse(prompt.contains("${swaggerSummary}"), "Плейсхолдеры не должны остаться");
    }

    @Test
    @DisplayName("FT-009: промпт для извлечения ключевых слов формируется корректно")
    public void testGetExtractKeywordsPrompt_placeholderReplaced() {
        String prompt = promptBuilderService.getExtractKeywordsPrompt("find all authentication methods");

        assertNotNull(prompt);
        assertTrue(prompt.contains("find all authentication methods"), "Должен содержать запрос");
        assertFalse(prompt.contains("${userPrompt}"), "Плейсхолдер не должен остаться");
    }

    @Test
    @DisplayName("FT-010: при пустых значениях плейсхолдеров система не падает")
    public void testGetDocumentChatAnalyze_emptyValues_doesNotCrash() {
        assertDoesNotThrow(() -> {
            String prompt = promptBuilderService.getDocumentChatAnalyze("", "", "");
            assertNotNull(prompt, "Должен вернуть промпт, а не null");
        }, "Не должно быть исключений при пустых значениях");
    }

    @Test
    @DisplayName("FT-010: при null-значении StringSubstitutor не роняет приложение")
    public void testGetVectorSearchNotFoundPrompt_withNullQuery_doesNotCrash() {
        try {
            String prompt = promptBuilderService.getVectorSearchNotFoundPrompt(null);
            assertNotNull(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Метод не должен выбрасывать исключение при null-значении: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("FT-005: валидный Swagger content передаётся в upload-промпт без ошибок")
    public void testGetDocumentChatUpload_validSummary_returnsPrompt() {
        String infoSummary = "API INFO:\n  Title: User API\n  Version: 1.0\nSERVERS:\n  - https://api.example.com";
        String methodSummary = "GET /api/users: List users\nPOST /api/users: Create user\n";

        String prompt = promptBuilderService.getDocumentChatUpload(infoSummary, methodSummary);

        assertNotNull(prompt);
        assertTrue(prompt.contains("GET /api/users"), "Должен содержать summary методов");
        assertTrue(prompt.contains("User API"), "Должен содержать info summary");
        assertFalse(prompt.contains("${swaggerMethodSummary}"), "Плейсхолдер не должен остаться");
        assertFalse(prompt.contains("${swaggerInfoSummary}"), "Плейсхолдер не должен остаться");
    }
}
