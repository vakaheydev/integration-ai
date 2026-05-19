package ai.agent.swagger.service.ai.handler;

import ai.agent.swagger.model.TaskType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Роутер, который по типу задачи возвращает нужный {@link TaskGraphNodeHandler}.
 */
@Component
public class TaskGraphHandlerRouter {

    private final Map<TaskType, TaskGraphNodeHandler> handlers = new EnumMap<>(TaskType.class);

    public TaskGraphHandlerRouter(List<TaskGraphNodeHandler> handlerList) {
        for (TaskGraphNodeHandler handler : handlerList) {
            handlers.put(handler.getTaskType(), handler);
        }
    }

    public TaskGraphNodeHandler getHandler(TaskType type) {
        TaskGraphNodeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No TaskGraphNodeHandler registered for TaskType: " + type);
        }
        return handler;
    }
}

