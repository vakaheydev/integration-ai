package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.model.SwaggerVectorSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("IT-003/IT-004/IT-005/IT-006 — Анализ, кодогенерация и поиск по Swagger")
@ExtendWith(MockitoExtension.class)
public class SwaggerServiceChatTest {

    @Mock
    private AiChatService aiChatService;

    @Mock
    private VectorStorageService vectorStorageService;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private PromptBuilderService promptBuilderService;

    @InjectMocks
    private SwaggerService swaggerService;

    private SwaggerDocument testDoc;

    @BeforeEach
    public void setUp() {
        testDoc = SwaggerDocument.builder()
                .id("doc-1").userId("user-1").name("Test API")
                .documentSummary("API для управления пользователями")
                .methodSummary("GET /users: List users\nPOST /users: Create user")
                .content("{}")
                .build();
    }

    @Test
    @DisplayName("IT-003: chatByDocumentId с ролью analytic — возвращает аналитический ответ")
    public void testChatByDocumentId_analyticRole_returnsResponse() {
        when(documentStorageService.findById("doc-1")).thenReturn(Optional.of(testDoc));
        when(promptBuilderService.getDocumentChatAnalyze(anyString(), anyString(), anyString()))
                .thenReturn("Built analytic prompt");
        when(aiChatService.chat("Built analytic prompt"))
                .thenReturn("Этот API предоставляет операции управления пользователями.");

        String response = swaggerService.chatByDocumentId("doc-1", "user-1", "What does this API do?", "analytic");

        assertNotNull(response);
        assertFalse(response.isEmpty(), "Ответ не должен быть пустым");
        verify(promptBuilderService).getDocumentChatAnalyze("What does this API do?",
                testDoc.getDocumentSummary(), testDoc.getMethodSummary());
        verify(aiChatService).chat("Built analytic prompt");
    }

    @Test
    @DisplayName("IT-004: chatByDocumentId с ролью programmer — возвращает ответ с кодом")
    public void testChatByDocumentId_programmerRole_returnsCodeResponse() {
        when(documentStorageService.findById("doc-1")).thenReturn(Optional.of(testDoc));
        when(promptBuilderService.getDocumentChatCode(anyString(), anyString(), anyString()))
                .thenReturn("Built code prompt");
        when(aiChatService.chat("Built code prompt"))
                .thenReturn("public class UserApiClient { ... }");

        String response = swaggerService.chatByDocumentId("doc-1", "user-1", "Generate Java client", "programmer");

        assertNotNull(response);
        assertFalse(response.isEmpty(), "Ответ не должен быть пустым");
        verify(promptBuilderService).getDocumentChatCode(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("IT-003/IT-004: неизвестная роль — бросает IllegalArgumentException")
    public void testChatByDocumentId_unknownRole_throwsException() {
        when(documentStorageService.findById("doc-1")).thenReturn(Optional.of(testDoc));

        assertThrows(IllegalArgumentException.class,
                () -> swaggerService.chatByDocumentId("doc-1", "user-1", "query", "unknown_role"));
    }

    @Test
    @DisplayName("IT-003: документ не найден — бросает NoSuchElementException")
    public void testChatByDocumentId_documentNotFound_throwsException() {
        when(documentStorageService.findById("missing")).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class,
                () -> swaggerService.chatByDocumentId("missing", "user-1", "query", "analytic"));
    }

    @Test
    @DisplayName("IT-005: search с userId — документ найден, возвращает present=true")
    public void testSearch_withUserId_documentFound_returnsPresent() {
        SwaggerVectorSearchResponse vectorResponse = SwaggerVectorSearchResponse.builder()
                .present(true).documentId("doc-1").build();

        when(promptBuilderService.getExtractKeywordsPrompt(anyString())).thenReturn("keywords prompt");
        when(aiChatService.chat("keywords prompt")).thenReturn("users, authentication");
        when(vectorStorageService.search("users, authentication", "user-1")).thenReturn(vectorResponse);
        when(documentStorageService.findById("doc-1")).thenReturn(Optional.of(testDoc));
        when(promptBuilderService.getDocumentChatAnalyze(anyString(), anyString(), anyString()))
                .thenReturn("analyze prompt");
        when(aiChatService.chat("analyze prompt")).thenReturn("Найден документ.");

        SwaggerSearchResult result = swaggerService.search("find user endpoints", "user-1");

        assertNotNull(result);
        assertTrue(result.isPresent(), "Результат должен быть найден");
        assertNotNull(result.getDocument(), "Документ должен присутствовать");
        assertEquals("doc-1", result.getDocument().getId());
    }

    @Test
    @DisplayName("IT-006: search с userId — документ не найден, возвращает present=false")
    public void testSearch_withUserId_notFound_returnsPresentFalse() {
        SwaggerVectorSearchResponse vectorResponse = SwaggerVectorSearchResponse.builder()
                .present(false).build();

        when(promptBuilderService.getExtractKeywordsPrompt(anyString())).thenReturn("keywords prompt");
        when(aiChatService.chat("keywords prompt")).thenReturn("blockchain, nft");
        when(vectorStorageService.search("blockchain, nft", "user-1")).thenReturn(vectorResponse);
        when(promptBuilderService.getVectorSearchNotFoundPrompt(anyString())).thenReturn("not found prompt");
        when(aiChatService.chat("not found prompt")).thenReturn("Ничего не найдено.");

        SwaggerSearchResult result = swaggerService.search("find blockchain nft api", "user-1");

        assertNotNull(result);
        assertFalse(result.isPresent(), "Результат должен быть не найден");
        assertNull(result.getDocument(), "Документ должен быть null");
    }

    @Test
    @DisplayName("getSwaggersByUserId: возвращает список документов пользователя")
    public void testGetSwaggersByUserId_returnsUserDocs() {
        when(documentStorageService.findByUserId("user-1")).thenReturn(List.of(testDoc));

        List<SwaggerDocument> docs = swaggerService.getSwaggersByUserId("user-1");

        assertEquals(1, docs.size());
        assertEquals("doc-1", docs.getFirst().getId());
    }

    @Test
    @DisplayName("deleteDocumentById: вызывает удаление и в MongoStorage, и в VectorStorage")
    public void testDeleteDocumentById_callsBothServices() {
        swaggerService.deleteDocumentById("doc-1");

        verify(documentStorageService).deleteDocument("doc-1");
        verify(vectorStorageService).deleteById("doc-1");
    }
}
