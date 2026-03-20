package ai.agent.swagger.service;

import ai.agent.swagger.model.CreateTaskRequest;
import ai.agent.swagger.model.Stage;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task createTask(String documentId, String userId, CreateTaskRequest request) {
        Task task = Task.builder()
                .documentId(documentId)
                .userId(userId)
                .type(request.getType())
                .description(request.getDescription())
                .build();
        Task saved = taskRepository.save(task);
        log.debug("Created task id={} for documentId={}, userId={}", saved.getId(), documentId, userId);
        return saved;
    }

    public Task createTask(Task task) {
        Task saved = taskRepository.save(task);
        log.debug("Created task id={} for documentId={}, userId={}", saved.getId(), task.getDocumentId(), task.getUserId());
        return saved;
    }

    public Task createTaskFromBase(String baseTaskId, String userId, String userMessage) {
        Task base = taskRepository.findById(baseTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Base task not found: " + baseTaskId));
        Task newTask = Task.builder()
                .documentId(base.getDocumentId())
                .userId(userId)
                .type(base.getType())
                .description(base.getDescription())
                .previousResult(base.getResult())
                .userMessage(userMessage)
                .build();
        Task saved = taskRepository.save(newTask);
        log.debug("Created task id={} from base task id={}", saved.getId(), baseTaskId);
        return saved;
    }

    public Task finishTask(String taskId, TaskStatus status, String statusDescription, String result) {
        Task existing = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        existing.setStatus(status);
        existing.setStatusDescription(statusDescription);
        existing.setResult(result);
        existing.setCurrentStage(null);
        existing.setCompletedDatetime(Instant.now());
        Task saved = taskRepository.save(existing);
        log.debug("Finished task id={} with status={}", existing.getId(), status);
        return saved;
    }

    public Task updateTask(Task patch) {
        Task existing = taskRepository.findById(patch.getId())
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + patch.getId()));

        if (patch.getStatus() != null)            existing.setStatus(patch.getStatus());
        if (patch.getCurrentStage() != null)      existing.setCurrentStage(patch.getCurrentStage());
        if (patch.getStatusDescription() != null) existing.setStatusDescription(patch.getStatusDescription());
        if (patch.getCompletedDatetime() != null) existing.setCompletedDatetime(patch.getCompletedDatetime());
        if (patch.getResult() != null)            existing.setResult(patch.getResult());
        if (patch.getStageHistory() != null)      existing.setStageHistory(patch.getStageHistory());
        if (patch.getDocumentId() != null)        existing.setDocumentId(patch.getDocumentId());
        if (patch.getUserId() != null)            existing.setUserId(patch.getUserId());
        if (patch.getType() != null)              existing.setType(patch.getType());
        if (patch.getDescription() != null)       existing.setDescription(patch.getDescription());

        Task saved = taskRepository.save(existing);
        log.debug("Updated task id={}", saved.getId());
        return saved;
    }

    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }

    public List<Task> getTasksByUserId(String userId) {
        return taskRepository.findByUserId(userId);
    }

    public List<Task> getTasksByDocumentId(String documentId) {
        return taskRepository.findByDocumentId(documentId);
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public void deleteById(String id) {
        taskRepository.deleteById(id);
        log.debug("Deleted task id={}", id);
    }

    public void deleteAllByUserId(String userId) {
        taskRepository.deleteByUserId(userId);
        log.debug("Deleted all tasks for userId={}", userId);
    }

    public Task restartTask(String id) {
        return restartTask(id, null, null);
    }

    public Task restartTask(String id, String previousResult, String userMessage) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
        task.setStatus(TaskStatus.CREATED);
        task.setCurrentStage(null);
        task.setStatusDescription(null);
        task.setCompletedDatetime(null);
        task.setStageHistory(new ArrayList<>());
        task.setPreviousResult(previousResult != null ? previousResult : task.getResult());
        task.setResult(null);
        task.setUserMessage(userMessage);
        Task saved = taskRepository.save(task);
        log.debug("Restarted task id={} with userMessage={}", id, userMessage);
        return saved;
    }

    /**
     * Меняет текущий Stage таски.
     * Первая Stage получает id=0. Каждая следующая — id предыдущей + 1.
     *
     * @return id новой Stage
     */
    public int changeCurrentStage(String taskId, String stageName) {
        return changeCurrentStage(taskId, stageName, null);
    }

    public int changeCurrentStage(String taskId, String stageName, String description) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        Stage oldStage = task.getCurrentStage();
        int newStageId;

        if (oldStage != null) {
            Stage finished = oldStage.toBuilder()
                    .instantEnd(Instant.now())
                    .build();
            task.getStageHistory().add(finished);
            newStageId = oldStage.getId() + 1;
        } else {
            newStageId = 0;
        }

        Stage newStage = Stage.builder()
                .id(newStageId)
                .name(stageName)
                .description(description)
                .status(TaskStatus.RUNNING)
                .instantStart(Instant.now())
                .build();
        task.setCurrentStage(newStage);

        taskRepository.save(task);
        log.debug("Changed currentStage for task id={} to '{}' (stageId={}, description={})", taskId, stageName, newStageId, description);
        return newStageId;
    }

    public void changeStage(String taskId, int stageId, String description, TaskStatus status) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        Stage currentStage = task.getCurrentStage();
        if (currentStage != null && currentStage.getId() == stageId) {
            Stage.StageBuilder builder = currentStage.toBuilder();
            if (description != null) builder.description(description);
            if (status != null) builder.status(status);
            task.setCurrentStage(builder.build());
            taskRepository.save(task);
            log.debug("Updated currentStage id={} of task id={}", stageId, taskId);
            return;
        }

        List<Stage> history = task.getStageHistory();
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).getId() == stageId) {
                Stage.StageBuilder builder = history.get(i).toBuilder();
                if (description != null) builder.description(description);
                if (status != null) builder.status(status);
                history.set(i, builder.build());
                taskRepository.save(task);
                log.debug("Updated stageHistory id={} of task id={}", stageId, taskId);
                return;
            }
        }

        throw new IllegalArgumentException("Stage id=" + stageId + " not found in task id=" + taskId);
    }
}

