package ai.agent.swagger.service.ai;

import ai.agent.swagger.model.Task;

import java.util.Collections;
import java.util.Set;

/**
 * Thread-local контекст для {@link ApiCallTools}.
 * Устанавливается перед запуском ноды графа и очищается после.
 * Передаёт task и текущий список одобренных вызовов.
 */
public class ApiCallToolsContext {

    private static final ThreadLocal<Task> TASK = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> APPROVED_CALLS = new ThreadLocal<>();

    public static void set(Task task, Set<String> approvedCalls) {
        TASK.set(task);
        APPROVED_CALLS.set(approvedCalls != null ? approvedCalls : Collections.emptySet());
    }

    public static String getTaskId() {
        Task task = TASK.get();
        return task != null ? task.getId() : null;
    }

    public static Task getTask() {
        return TASK.get();
    }

    public static boolean isApproved(String callKey) {
        Set<String> calls = APPROVED_CALLS.get();
        return calls != null && calls.contains(callKey);
    }

    public static boolean isRejected(String callKey) {
        Set<String> calls = APPROVED_CALLS.get();
        return calls != null && calls.contains(
                ai.agent.swagger.service.ai.approval.ApiCallApprovalHandler.REJECTED_PREFIX + callKey);
    }

    public static void clear() {
        TASK.remove();
        APPROVED_CALLS.remove();
    }
}
