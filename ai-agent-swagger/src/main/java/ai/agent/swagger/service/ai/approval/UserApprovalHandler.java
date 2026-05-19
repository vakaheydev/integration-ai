package ai.agent.swagger.service.ai.approval;

import ai.agent.swagger.model.ApprovalType;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.TaskService;

/**
 * Стратегия обработки решения пользователя по конкретному типу аппрува.
 * Реализации регистрируются как Spring-бины и агрегируются через {@link UserApprovalHandlerRouter}.
 */
public interface UserApprovalHandler {

    /** Тип аппрува, который обрабатывает этот хендлер. */
    ApprovalType getApprovalType();

    /**
     * Вызывается когда пользователь подтвердил аппрув.
     * Должен обновить состояние задачи (in-memory + DB) и вернуть имя следующей ноды графа.
     */
    String onApproved(Task task, TaskService taskService);

    /**
     * Вызывается когда пользователь отклонил аппрув.
     * Должен обновить состояние задачи (in-memory + DB) и вернуть имя следующей ноды графа.
     *
     * @param message комментарий пользователя (причина отклонения)
     */
    String onRejected(Task task, String message, TaskService taskService);
}
