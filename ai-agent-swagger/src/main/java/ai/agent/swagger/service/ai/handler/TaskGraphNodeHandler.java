package ai.agent.swagger.service.ai.handler;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskType;
import ai.agent.swagger.service.ai.AiTaskGraphService;

/**
 * Стратегия обработки нод графа для конкретного типа задачи.
 * Каждый метод соответствует одной ноде в {@link AiTaskGraphService}.
 */
public interface TaskGraphNodeHandler {

    /** Тип задачи, который обрабатывает этот хендлер. */
    TaskType getTaskType();

    /** Нода analyze_task: составляет план действий. */
    String analyze(Task task, String availableTools);

    /** Нода retry_analyze_task: повторный анализ с учётом feedback. */
    String retryAnalyze(Task task, String feedback, String availableTools);

    /** Нода handle_analysis: исполняет план. */
    String execute(Task task, String analysis);

    /** Нода handle_analysis_after_error / retry_handle_analysis_after_error: исполняет план с учётом ошибки. */
    String executeAfterError(Task task, String analysis, String errorMessage);

    /** Нода retry_handle_analysis: повторное исполнение с учётом feedback-а ревьюера. */
    String retryExecute(Task task, String analysis, String feedback);
}

