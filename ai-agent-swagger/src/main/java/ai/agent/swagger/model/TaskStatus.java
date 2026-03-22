package ai.agent.swagger.model;

public enum TaskStatus {
    CREATED,
    RUNNING,
    WAITING_USER_INPUT,
    WAITING_USER_APPROVE,
    WAITING_SUBTASK,
    COMPLETED,
    FAILED
}

