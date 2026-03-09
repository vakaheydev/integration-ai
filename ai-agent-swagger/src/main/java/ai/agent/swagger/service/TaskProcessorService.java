package ai.agent.swagger.service;

import ai.agent.swagger.model.Stage;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskResult;
import ai.agent.swagger.model.TaskStatus;
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
            applyResult(task, taskResult);
        } catch (Exception e) {
            log.error("Task id={} failed with exception: {}", task.getId(), e.getMessage(), e);
            taskService.updateTask(task.toBuilder()
                    .status(TaskStatus.FAILED)
                    .statusDescription("Exception: " + e.getMessage())
                    .currentStage(null)
                    .stageHistory(null)
                    .build());
        }
    }

    private void applyResult(Task task, TaskResult taskResult) {
        int stageId = taskService.changeCurrentStage(task.getId(), "Finishing task", "Reviewing task result");

        TaskStatus status = taskResult.getStatus();
        log.info("Task id={} finished with status={}", task.getId(), status);

        Task current = taskService.getTaskById(task.getId())
                .orElseThrow(() -> new IllegalStateException("Task not found: " + task.getId()));


        String reviewResponse = "";
        try {
            String prompt = promptBuilderService.getHandleTaskReviewResultPrompt(current.getDescription(), taskResult.getResult(), status.name());
            reviewResponse = aiChatService.chat(prompt);
        } catch (Exception e) {
            log.warn("Failed to get review response for task id={}: {}", task.getId(), e.getMessage());
            reviewResponse = "Failed to get review response: " + e.getMessage();
        }

        taskService.changeStage(task.getId(), stageId, null, TaskStatus.COMPLETED);
        taskService.changeCurrentStage(task.getId(), null);

        switch (status) {
            case COMPLETED -> log.info("Task id={} completed successfully", current.getId());
            case FAILED -> log.warn("Task id={} failed", current.getId());
            case WAITING -> log.info("Task id={} is waiting for next run", current.getId());
            default     -> log.warn("Task id={} returned unexpected status={}", current.getId(), status);
        }

        taskService.finishTask(task.getId(), status, reviewResponse, taskResult.getResult());
    }
}
