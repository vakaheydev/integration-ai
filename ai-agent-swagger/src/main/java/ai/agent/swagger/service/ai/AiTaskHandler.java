package ai.agent.swagger.service.ai;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskResult;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.service.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class AiTaskHandler implements TaskHandler {
    private final AiTaskGraphService taskGraphService;

    public AiTaskHandler(AiTaskGraphService taskGraphService) {
        this.taskGraphService = taskGraphService;
    }

    @Override
    public TaskResult handle(Task task) {
        try {
            Map<String, Object> resultMap = taskGraphService.runGraphTaskHandle(task);
            String result = resultMap.getOrDefault("result", "").toString();
            if (result.isEmpty()) {
                return new TaskResult(TaskStatus.FAILED, "Task completed with empty result");
            } else {
                return new TaskResult(TaskStatus.COMPLETED, result);
            }
        } catch (GraphStateException e) {
            return new TaskResult(TaskStatus.FAILED, "Graph execution failed: " + e.getMessage());
        }
    }
}

