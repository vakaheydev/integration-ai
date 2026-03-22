package ai.agent.swagger.service;

import ai.agent.swagger.model.CreateTaskRequest;
import ai.agent.swagger.model.Stage;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskScenario;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.model.TaskType;
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
        TaskType requestedType = request.getType();

        // Если это сценарий — создаём родительскую таску и первую подтаску
        if (TaskScenario.isScenario(requestedType)) {
            Task parent = Task.builder()
                    .documentId(documentId)
                    .userId(userId)
                    .type(requestedType)
                    .description(request.getDescription())
                    .modelName(request.getModelName())
                    .status(TaskStatus.WAITING_SUBTASK)
                    .build();
            Task savedParent = taskRepository.save(parent);
            log.debug("Created scenario parent task id={}, type={}", savedParent.getId(), requestedType);

            createSubtask(savedParent, 0);
            return savedParent;
        }

        // Одиночная задача
        Task task = Task.builder()
                .documentId(documentId)
                .userId(userId)
                .type(requestedType)
                .description(request.getDescription())
                .modelName(request.getModelName())
                .build();
        Task saved = taskRepository.save(task);
        log.debug("Created task id={} for documentId={}, userId={}", saved.getId(), documentId, userId);
        return saved;
    }

    /**
     * Создаёт подтаску для указанного шага сценария.
     */
    public Task createSubtask(Task parent, int stepIndex) {
        TaskType scenarioType = parent.getType();
        TaskType stepType = TaskScenario.getSteps(scenarioType).get(stepIndex);

        Task subtask = Task.builder()
                .documentId(parent.getDocumentId())
                .userId(parent.getUserId())
                .type(stepType)
                .description(parent.getDescription())
                .modelName(parent.getModelName())
                .parentTaskId(parent.getId())
                .scenarioType(scenarioType)
                .scenarioStep(stepIndex)
                .build();
        Task saved = taskRepository.save(subtask);
        log.debug("Created subtask id={}, step={}, type={} for parent id={}",
                saved.getId(), stepIndex, stepType, parent.getId());
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
                .modelName(base.getModelName())
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
        if (patch.getAiQuestion() != null)        existing.setAiQuestion(patch.getAiQuestion());
        if (patch.getUserInputResponse() != null) existing.setUserInputResponse(patch.getUserInputResponse());
        if (patch.getModelName() != null)         existing.setModelName(patch.getModelName());
        if (patch.getApproveDescription() != null) existing.setApproveDescription(patch.getApproveDescription());
        if (patch.getApproveMessage() != null)     existing.setApproveMessage(patch.getApproveMessage());

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

    public List<Task> getSubtasks(String parentTaskId) {
        return taskRepository.findByParentTaskId(parentTaskId);
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
        task.setApproved(false);
        Task saved = taskRepository.save(task);
        log.debug("Restarted task id={} with userMessage={}", id, userMessage);
        return saved;
    }

    /**
     * Принимает ответ пользователя на вопрос ИИ.
     * Сохраняет ответ, сбрасывает статус в CREATED для повторной обработки.
     */
    public Task resolveUserInput(String taskId, String userResponse) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (task.getStatus() != TaskStatus.WAITING_USER_INPUT) {
            throw new IllegalStateException("Task id=" + taskId + " is not waiting for user input (status=" + task.getStatus() + ")");
        }
        task.setUserInputResponse(userResponse);
        task.setStatus(TaskStatus.CREATED);
        task.setStatusDescription(null);

        // Обновляем последний стейдж — сохраняем ответ и завершаем его
        Stage currentStage = task.getCurrentStage();
        if (currentStage != null) {
            Stage finished = currentStage.toBuilder()
                    .status(TaskStatus.COMPLETED)
                    .userInputResponse(userResponse)
                    .instantEnd(Instant.now())
                    .build();
            task.getStageHistory().add(finished);
        }
        task.setCurrentStage(null);

        Task saved = taskRepository.save(task);
        log.debug("Resolved user input for task id={}, response={}", taskId, userResponse);
        return saved;
    }

    /**
     * Обрабатывает решение пользователя по коду: approve или disapprove.
     * @param approved true — подтвердить запуск, false — отклонить с комментарием
     * @param message комментарий пользователя (обязателен при disapprove)
     */
    public Task approveTask(String taskId, boolean approved, String message) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (task.getStatus() != TaskStatus.WAITING_USER_APPROVE) {
            throw new IllegalStateException("Task id=" + taskId + " is not waiting for approval (status=" + task.getStatus() + ")");
        }
        task.setApproved(approved);
        task.setApproveMessage(approved ? null : message);
        task.setStatus(TaskStatus.CREATED);
        task.setStatusDescription(null);

        // Обновляем последний стейдж — сохраняем решение и завершаем его
        Stage currentStage = task.getCurrentStage();
        if (currentStage != null) {
            Stage.StageBuilder builder = currentStage.toBuilder()
                    .status(TaskStatus.COMPLETED)
                    .instantEnd(Instant.now());
            if (!approved) {
                builder.approveMessage(message);
            }
            task.getStageHistory().add(builder.build());
        }
        task.setCurrentStage(null);

        Task saved = taskRepository.save(task);
        log.debug("{} task id={}, message={}", approved ? "Approved" : "Disapproved", taskId, message);
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
        changeStage(taskId, stageId, description, null, status);
    }

    public void changeStage(String taskId, int stageId, String description, String result, TaskStatus status) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        Stage currentStage = task.getCurrentStage();
        if (currentStage != null && currentStage.getId() == stageId) {
            Stage.StageBuilder builder = currentStage.toBuilder();
            if (description != null) builder.description(description);
            if (result != null) builder.result(result);
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
                if (result != null) builder.result(result);
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

