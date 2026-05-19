package ai.agent.swagger.repository;

import ai.agent.swagger.model.SwaggerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SwaggerDocumentRepository extends MongoRepository<SwaggerDocument, String> {
    List<SwaggerDocument> findByUserId(String userId);
    void deleteByUserId(String userId);
}
