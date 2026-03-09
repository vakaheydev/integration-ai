package ai.agent.swagger.service;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskResult;

public interface TaskHandler {
    TaskResult handle(Task task);
}

