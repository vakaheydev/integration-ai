package ai.agent.swagger.repository;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByUserId(String userId);
    List<Task> findByDocumentId(String documentId);
    List<Task> findByStatus(TaskStatus status);
}

