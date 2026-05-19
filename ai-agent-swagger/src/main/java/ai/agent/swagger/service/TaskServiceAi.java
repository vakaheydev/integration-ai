package ai.agent.swagger.service;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.ai.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TaskServiceAi {

    private final TaskService taskService;
    private final AiChatService aiChatService;
    private final PromptBuilderService promptBuilderService;

    public TaskServiceAi(TaskService taskService, AiChatService aiChatService,
                         PromptBuilderService promptBuilderService) {
        this.taskService = taskService;
        this.aiChatService = aiChatService;
        this.promptBuilderService = promptBuilderService;
    }

    public String chat(String taskId, String userId, String userQuestion) {
        Task task = taskService.getTaskById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        String currentStage = task.getCurrentStage() != null ? task.getCurrentStage().getName() : null;

        String prompt = promptBuilderService.getTaskChatPrompt(
                task.getId(),
                task.getType() != null ? task.getType().name() : "N/A",
                task.getStatus().name(),
                task.getStatusDescription(),
                task.getDescription(),
                task.getResult(),
                currentStage,
                userQuestion
        );

        log.debug("Task chat for taskId={}, userId={}", taskId, userId);
        return aiChatService.chat(userId, prompt);
    }
}

