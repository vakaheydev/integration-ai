package ai.agent.swagger.service;

import ai.agent.swagger.model.Stage;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskResult;
import ai.agent.swagger.model.TaskScenario;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.model.TaskType;
import ai.agent.swagger.service.ai.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class TaskProcessorService {
    private final PromptBuilderService promptBuilderService;
    private final AiChatService aiChatService;
    private final TaskService taskService;
    private final TaskHandler taskHandler;

    public TaskProcessorService(PromptBuilderService promptBuilderService, AiChatService aiChatService, TaskService taskService, TaskHandler taskHandler) {
        this.promptBuilderService = promptBuilderService;
        this.aiChatService = aiChatService;
        this.taskService = taskService;
        this.taskHandler = taskHandler;
    }

    @Async
    public void handleTask(Task task) {
        log.info("Handling task id={}, type={}, documentId={}", task.getId(), task.getType(), task.getDocumentId());
        try {
            taskService.updateTask(task.toBuilder()
                    .status(TaskStatus.RUNNING)
                    .currentStage(Stage.builder()
                            .id(0)
                            .name("Processing task")
                            .description("Getting ready to execute the task")
                            .instantStart(Instant.now())
                            .build())
                    .build());

            TaskResult taskResult = taskHandler.handle(task);
            if (taskResult.getStatus() == TaskStatus.WAITING_USER_INPUT) {
                applyWaitingUserInput(task, taskResult);
                return;
            }
            if (taskResult.getStatus() == TaskStatus.WAITING_USER_APPROVE) {
                applyWaitingUserApprove(task, taskResult);
                return;
            }
            applyResult(task, taskResult);
        } catch (Exception e) {
            log.error("Task id={} failed with exception: {}", task.getId(), e.getMessage(), e);
            taskService.updateTask(task.toBuilder()
                    .status(TaskStatus.FAILED)
                    .statusDescription("Exception: " + e.getMessage())
                    .currentStage(null)
                    .stageHistory(null)
                    .build());
            // Если подтаска сценария упала — завершить родителя ошибкой
            if (task.getParentTaskId() != null) {
                taskService.finishTask(task.getParentTaskId(), TaskStatus.FAILED,
                        "Subtask failed: " + e.getMessage(), null);
                log.warn("Parent task id={} failed due to subtask id={}", task.getParentTaskId(), task.getId());
            }
        }
    }

    private void applyWaitingUserInput(Task task, TaskResult taskResult) {
        log.info("Task id={} paused, waiting for user input: {}", task.getId(), taskResult.getResult());
        Task current = taskService.getTaskById(task.getId())
                .orElseThrow(() -> new IllegalStateException("Task not found: " + task.getId()));
        current.setStatus(TaskStatus.WAITING_USER_INPUT);
        current.setStatusDescription("AI question: " + taskResult.getResult());

        // Устанавливаем статус, вопрос и результат на текущий стейдж
        Stage currentStage = current.getCurrentStage();
        if (currentStage != null) {
            current.setCurrentStage(currentStage.toBuilder()
                    .status(TaskStatus.WAITING_USER_INPUT)
                    .aiQuestion(taskResult.getResult())
                    .result(taskResult.getResult())
                    .build());
        }

        taskService.updateTask(current);
    }

    private void applyWaitingUserApprove(Task task, TaskResult taskResult) {
        log.info("Task id={} paused, waiting for user approval to execute code", task.getId());
        Task current = taskService.getTaskById(task.getId())
                .orElseThrow(() -> new IllegalStateException("Task not found: " + task.getId()));
        current.setStatus(TaskStatus.WAITING_USER_APPROVE);
        current.setStatusDescription("Code ready for review and approval");

        // Устанавливаем статус, описание аппрува и результат (код) на текущий стейдж
        Stage currentStage = current.getCurrentStage();
        if (currentStage != null) {
            current.setCurrentStage(currentStage.toBuilder()
                    .status(TaskStatus.WAITING_USER_APPROVE)
                    .approveDescription(current.getApproveDescription())
                    .result(current.getResult())
                    .build());
        }

        taskService.updateTask(current);
    }

    private void applyResult(Task task, TaskResult taskResult) {
        int stageId = taskService.changeCurrentStage(task.getId(), "Finishing task");

        TaskStatus status = taskResult.getStatus();
        log.info("Task id={} finished with status={}", task.getId(), status);

        Task current = taskService.getTaskById(task.getId())
                .orElseThrow(() -> new IllegalStateException("Task not found: " + task.getId()));

        String reviewResponse = "";
        try {
            String prompt = promptBuilderService.getHandleTaskReviewResultPrompt(current.getDescription(), taskResult.getResult(), status.name());
            reviewResponse = aiChatService.chatStateless(prompt, task.getModelName());
        } catch (Exception e) {
            log.warn("Failed to get review response for task id={}: {}", task.getId(), e.getMessage());
            reviewResponse = "Failed to get review response: " + e.getMessage();
        }

        taskService.changeStage(task.getId(), stageId, "Task review completed", reviewResponse, TaskStatus.COMPLETED);
        taskService.changeCurrentStage(task.getId(), null);

        switch (status) {
            case COMPLETED -> log.info("Task id={} completed successfully", current.getId());
            case FAILED -> log.warn("Task id={} failed", current.getId());
            case WAITING_USER_INPUT -> log.info("Task id={} is waiting for next run", current.getId());
            default     -> log.warn("Task id={} returned unexpected status={}", current.getId(), status);
        }

        taskService.finishTask(task.getId(), status, reviewResponse, taskResult.getResult());

        // Если это подтаска сценария — обработать переход к следующему шагу
        if (status == TaskStatus.COMPLETED && task.getParentTaskId() != null) {
            advanceScenario(task);
        }
    }

    /**
     * Продвигает сценарий: создаёт следующую подтаску или завершает родительскую таску.
     */
    private void advanceScenario(Task completedSubtask) {
        String parentId = completedSubtask.getParentTaskId();
        Task parent = taskService.getTaskById(parentId)
                .orElseThrow(() -> new IllegalStateException("Parent task not found: " + parentId));

        TaskType scenarioType = completedSubtask.getScenarioType();
        int currentStep = completedSubtask.getScenarioStep();
        TaskType nextStepType = TaskScenario.getNextStep(scenarioType, currentStep);

        if (nextStepType != null) {
            // Создаём следующую подтаску с результатом предыдущей
            Task nextSubtask = taskService.createSubtask(parent, currentStep + 1);
            nextSubtask.setPreviousResult(completedSubtask.getResult());
            taskService.updateTask(nextSubtask);
            log.info("Scenario {} advanced: step {} ({}) -> step {} ({}) for parent id={}",
                    scenarioType, currentStep, completedSubtask.getType(),
                    currentStep + 1, nextStepType, parentId);
        } else {
            // Сценарий завершён — завершаем родительскую таску результатом последней подтаски
            taskService.finishTask(parentId, TaskStatus.COMPLETED,
                    "Scenario completed", completedSubtask.getResult());
            log.info("Scenario {} completed for parent id={}", scenarioType, parentId);
        }
    }
}
