package ai.agent.swagger.service.ai;

import ai.agent.swagger.exception.UserApproveRequiredException;
import ai.agent.swagger.exception.UserInputRequiredException;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskResult;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.service.TaskHandler;
import ai.agent.swagger.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class AiTaskHandler implements TaskHandler {
    private final AiTaskGraphService taskGraphService;
    private final TaskService taskService;

    public AiTaskHandler(AiTaskGraphService taskGraphService, TaskService taskService) {
        this.taskGraphService = taskGraphService;
        this.taskService = taskService;
    }

    @Override
    public TaskResult handle(Task task) {
        try {
            taskService.changeStage(task.getId(), 0, null, TaskStatus.COMPLETED);
            Map<String, Object> resultMap = taskGraphService.runGraphTaskHandle(task);
            String result = resultMap.getOrDefault("result", "").toString();
            if (result.isEmpty()) {
                return new TaskResult(TaskStatus.FAILED, "Task completed with empty result");
            } else {
                return new TaskResult(TaskStatus.COMPLETED, result);
            }
        } catch (Exception e) {
            // LangGraph4j оборачивает исключения из нод в ExecutionException,
            // поэтому ищем UserInputRequiredException в цепочке причин
            UserInputRequiredException uire = findCause(e, UserInputRequiredException.class);
            if (uire != null) {
                log.info("Task id={} requires user input: {}", task.getId(), uire.getQuestion());
                return new TaskResult(TaskStatus.WAITING_USER_INPUT, uire.getQuestion());
            }
            UserApproveRequiredException uare = findCause(e, UserApproveRequiredException.class);
            if (uare != null) {
                log.info("Task id={} requires user approval for code execution", task.getId());
                return new TaskResult(TaskStatus.WAITING_USER_APPROVE, uare.getCode());
            }
            return new TaskResult(TaskStatus.FAILED, "Task execution failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable cause = throwable;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return (T) cause;
            }
            cause = cause.getCause();
        }
        return null;
    }
}

