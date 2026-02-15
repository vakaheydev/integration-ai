package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerVectorSearchResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VectorStorageService {
    private final EmbeddingService embeddingService;

    public VectorStorageService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public String saveDocument(String content, Map<String, String> metadata) {
        String id = embeddingService.saveEmbedding(content, metadata);
        log.info("Document saved to storage successfully");
        return id;
    }

    public String saveDocument(String content) {
        String id = embeddingService.saveEmbedding(content);
        log.info("Document saved to storage successfully");
        return id;
    }

    public SwaggerVectorSearchResponse search(String searchQuery) {
        EmbeddingMatch<TextSegment> searchResult = embeddingService.search(searchQuery).orElse(null);
        if (searchResult == null) {
            return SwaggerVectorSearchResponse.builder()
                    .present(false)
                    .build();
        }
        TextSegment embedded = searchResult.embedded();
        return SwaggerVectorSearchResponse.builder()
                .present(true)
                .content(embedded.text())
                .metadata(embedded.metadata().toMap())
                .documentId(searchResult.embeddingId())
                .build();
    }

    public SwaggerVectorSearchResponse search(String searchQuery, String userId) {
        EmbeddingMatch<TextSegment> searchResult = embeddingService.search(searchQuery, userId).orElse(null);
        if (searchResult == null) {
            return SwaggerVectorSearchResponse.builder()
                    .present(false)
                    .build();
        }
        TextSegment embedded = searchResult.embedded();
        return SwaggerVectorSearchResponse.builder()
                .present(true)
                .content(embedded.text())
                .metadata(embedded.metadata().toMap())
                .documentId(searchResult.embeddingId())
                .build();
    }

    public void deleteById(String id) {
        embeddingService.deleteById(id);
    }

    public List<String> searchByUserId(String userId) {
        return embeddingService.searchByUserId(userId);
    }

    public String searchByDocumentId(String documentId) {
        return embeddingService.searchByDocumentId(documentId);
    }
}
