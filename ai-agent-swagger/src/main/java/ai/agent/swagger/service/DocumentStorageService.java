package ai.agent.swagger.service;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.repository.SwaggerDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentStorageService {
    private final SwaggerDocumentRepository swaggerDocumentRepository;

    public DocumentStorageService(SwaggerDocumentRepository swaggerDocumentRepository) {
        this.swaggerDocumentRepository = swaggerDocumentRepository;
    }

    public Optional<SwaggerDocument> findById(String id) {
        return swaggerDocumentRepository.findById(id);
    }

    public List<SwaggerDocument> findByUserId(String userId) {
        return swaggerDocumentRepository.findByUserId(userId);
    }

    public SwaggerDocument saveDocument(SwaggerDocument swaggerDocument) {
        return swaggerDocumentRepository.save(swaggerDocument);
    }

    public void deleteDocument(String id) {
        swaggerDocumentRepository.deleteById(id);
    }
}
