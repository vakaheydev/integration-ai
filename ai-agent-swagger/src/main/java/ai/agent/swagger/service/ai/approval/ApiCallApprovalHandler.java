package ai.agent.swagger.service.ai.approval;

import ai.agent.swagger.model.ApprovalType;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Обрабатывает аппрув выполнения HTTP-запроса к внешнему API.
 * Одобрение → добавить вызов в список разрешённых, продолжить анализ.
 * Отклонение → добавить вызов в список отклонённых, продолжить анализ без него.
 */
@Component
@Slf4j
public class ApiCallApprovalHandler implements UserApprovalHandler {

    public static final String REJECTED_PREFIX = "REJECTED_";

    @Override
    public ApprovalType getApprovalType() {
        return ApprovalType.API_CALL;
    }

    @Override
    public String onApproved(Task task, TaskService taskService) {
        String subject = task.getPendingApproval().getSubject();
        log.info("API call approved for task id={}, call={}", task.getId(), subject);
        task.getApprovedApiCalls().add(subject);
        task.setPendingApproval(null);
        taskService.approveApiCall(task.getId(), subject);
        return "analyze_task";
    }

    @Override
    public String onRejected(Task task, String message, TaskService taskService) {
        String subject = task.getPendingApproval().getSubject();
        log.info("API call rejected for task id={}, call={}", task.getId(), subject);
        String rejectedKey = REJECTED_PREFIX + subject;
        task.getApprovedApiCalls().add(rejectedKey);
        task.setPendingApproval(null);
        taskService.rejectApiCall(task.getId(), subject);
        return "analyze_task";
    }
}
