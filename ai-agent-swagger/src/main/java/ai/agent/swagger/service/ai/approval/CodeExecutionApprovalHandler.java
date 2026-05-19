package ai.agent.swagger.service.ai.approval;

import ai.agent.swagger.model.ApprovalType;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Обрабатывает аппрув запуска сгенерированного кода.
 * Одобрение → выполнить код; отклонение → переписать код с учётом комментария пользователя.
 */
@Component
@Slf4j
public class CodeExecutionApprovalHandler implements UserApprovalHandler {

    @Override
    public ApprovalType getApprovalType() {
        return ApprovalType.CODE_EXECUTION;
    }

    @Override
    public String onApproved(Task task, TaskService taskService) {
        log.info("Code execution approved for task id={}", task.getId());
        clearPendingApproval(task, taskService);
        return "execute_code";
    }

    @Override
    public String onRejected(Task task, String message, TaskService taskService) {
        log.info("Code execution rejected for task id={}, message={}", task.getId(), message);
        task.setApproveMessage(message);
        clearPendingApproval(task, taskService);
        return "rewrite_disapproved_code";
    }

    private void clearPendingApproval(Task task, TaskService taskService) {
        task.setPendingApproval(null);
        taskService.clearPendingApproval(task.getId());
    }
}
