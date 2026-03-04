package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerVectorSearchResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FT-007/FT-008 — Векторизация запросов и Swagger документов")
@ExtendWith(MockitoExtension.class)
public class VectorStorageServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private VectorStorageService vectorStorageService;

    // FT-007: векторизация запроса — search возвращает непустой результат
    @Test
    @DisplayName("FT-007: векторизация запроса — поиск возвращает результат с documentId")
    public void testSearch_withQuery_returnsPresent() {
        TextSegment segment = TextSegment.from("GET /users: List users",
                Metadata.from(Map.of("document-id", "doc-1", "user-id", "user-1")));
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.9, "doc-1",
                Embedding.from(new float[]{0.1f, 0.2f, 0.3f}), segment);

        when(embeddingService.search(anyString())).thenReturn(Optional.of(match));

        SwaggerVectorSearchResponse result = vectorStorageService.search("find user endpoints");

        assertNotNull(result);
        assertTrue(result.isPresent(), "Результат должен быть найден");
        assertEquals("doc-1", result.getDocumentId(), "ID документа должен совпадать");
        assertNotNull(result.getContent(), "Содержимое не должно быть null");
    }

    // FT-007: повторный вызов с тем же запросом — стабильный результат
    @Test
    @DisplayName("FT-007: повторный вызов поиска — результат стабилен")
    public void testSearch_repeatedCall_stableResult() {
        TextSegment segment = TextSegment.from("POST /orders: Create order",
                Metadata.from(Map.of("document-id", "doc-2")));
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.85, "doc-2",
                Embedding.from(new float[]{0.5f, 0.6f}), segment);

        when(embeddingService.search(anyString())).thenReturn(Optional.of(match));

        SwaggerVectorSearchResponse first = vectorStorageService.search("create order");
        SwaggerVectorSearchResponse second = vectorStorageService.search("create order");

        assertEquals(first.getDocumentId(), second.getDocumentId(),
                "Повторный вызов должен возвращать тот же documentId");
        assertEquals(first.isPresent(), second.isPresent(),
                "Повторный вызов должен возвращать тот же статус present");
    }

    // FT-007: когда ничего не найдено — present=false, не падает
    @Test
    @DisplayName("FT-007: поиск не нашёл результатов — возвращает present=false без исключений")
    public void testSearch_notFound_returnsPresentFalse() {
        when(embeddingService.search(anyString())).thenReturn(Optional.empty());

        SwaggerVectorSearchResponse result = vectorStorageService.search("completely unknown topic");

        assertNotNull(result, "Результат не должен быть null");
        assertFalse(result.isPresent(), "present должен быть false");
        assertNull(result.getDocumentId(), "documentId должен быть null");
    }

    // FT-008: сохранение документа с метаданными — возвращает documentId
    @Test
    @DisplayName("FT-008: сохранение Swagger с метаданными — возвращает непустой documentId")
    public void testSaveDocument_withMetadata_returnsDocumentId() {
        String content = "GET /users: List users\nPOST /users: Create user";
        Map<String, String> metadata = Map.of("user_id", "user-1", "swagger_summary", content);

        when(embeddingService.saveEmbedding(content, metadata)).thenReturn("vec-id-123");

        String documentId = vectorStorageService.saveDocument(content, metadata);

        assertNotNull(documentId, "documentId не должен быть null");
        assertFalse(documentId.isEmpty(), "documentId не должен быть пустым");
        assertEquals("vec-id-123", documentId);
        verify(embeddingService).saveEmbedding(content, metadata);
    }

    // FT-008: метаданные передаются корректно (userId, swagger_summary)
    @Test
    @DisplayName("FT-008: метаданные (userId, swagger_summary) передаются в хранилище без изменений")
    public void testSaveDocument_metadataPropagated() {
        String content = "GET /products: List products";
        Map<String, String> metadata = Map.of("user_id", "user-42", "swagger_summary", content);

        when(embeddingService.saveEmbedding(eq(content), eq(metadata))).thenReturn("vec-id-42");

        vectorStorageService.saveDocument(content, metadata);

        verify(embeddingService).saveEmbedding(
                argThat(c -> c.equals(content)),
                argThat(m -> "user-42".equals(m.get("user_id")))
        );
    }

    // FT-008: удаление документа из векторного хранилища
    @Test
    @DisplayName("FT-008: удаление документа из векторного хранилища — вызывается EmbeddingService.deleteById")
    public void testDeleteById_callsEmbeddingService() {
        doNothing().when(embeddingService).deleteById("doc-1");

        vectorStorageService.deleteById("doc-1");

        verify(embeddingService).deleteById("doc-1");
    }
}

