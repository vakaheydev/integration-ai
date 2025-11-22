package ai.agent.swagger.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonWriter;
import org.springframework.stereotype.Service;

import java.util.*;

import static dev.langchain4j.store.embedding.chroma.ChromaApiVersion.V2;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
@Slf4j
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final ChromaEmbeddingStore embeddingStore;

    public EmbeddingService() {
        this.embeddingStore = ChromaEmbeddingStore.builder()
                .apiVersion(V2)
                .baseUrl("http://localhost:8000")
                .collectionName("swagger")
                .build();

        this.embeddingModel = OllamaEmbeddingModel.builder()
                .modelName("nomic-embed-text:latest")
                .baseUrl("http://localhost:11434")
                .build();
    }

    private Embedding embed(String content) {
        return embeddingModel.embed(content).content();
    }

    public String saveEmbedding(String content, Map<String, String> metadata) {
        TextSegment textSegment = TextSegment.from(content, Metadata.from(metadata));
        Embedding embedding = embeddingModel.embed(textSegment).content();
        return embeddingStore.add(embedding, textSegment);
    }

    public String saveEmbedding(String content) {
        TextSegment textSegment = TextSegment.from(content);
        Embedding embedding = embeddingModel.embed(textSegment).content();
        return embeddingStore.add(embedding, textSegment);
    }

    public void deleteById(String id) {
        embeddingStore.remove(id);
    }

    public Optional<EmbeddingMatch<TextSegment>> search(String searchQuery) {
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest
                .builder()
                .queryEmbedding(embed(searchQuery))
                .build();

        EmbeddingSearchResult<TextSegment> embeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
        List<EmbeddingMatch<TextSegment>> embeddingMatch = embeddingSearchResult.matches();
        if (embeddingMatch.isEmpty()) {
            return Optional.empty();
        }
        else {
            EmbeddingMatch<TextSegment> first = embeddingMatch.getFirst();
            return Optional.of(first);
        }
    }

    public String searchByDocumentId(String documentId) {
        Filter filter = metadataKey("document-id").isEqualTo(documentId);

        EmbeddingSearchRequest embeddingSearchRequest1 = EmbeddingSearchRequest
                .builder()
                .filter(filter)
                .maxResults(1)
                .build();

        EmbeddingSearchResult<TextSegment> embeddingSearchResult1 = embeddingStore.search(embeddingSearchRequest1);
        EmbeddingMatch<TextSegment> embeddingMatch1 = embeddingSearchResult1.matches().getFirst();
        return embeddingMatch1.embedded().text();
    }

    public List<String> searchByUserId(String userId) {
        Filter filter = metadataKey("user-id").isEqualTo(userId);

        EmbeddingSearchRequest embeddingSearchRequest1 = EmbeddingSearchRequest
                .builder()
                .filter(filter)
                .build();

        EmbeddingSearchResult<TextSegment> embeddingSearchResult1 = embeddingStore.search(embeddingSearchRequest1);
        List<EmbeddingMatch<TextSegment>> embeddingMatch1 = embeddingSearchResult1.matches();
        List<String> results = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> embeddingMatch : embeddingMatch1) {
            results.add(embeddingMatch.embedded().text());
        }

        return results;
    }
}
