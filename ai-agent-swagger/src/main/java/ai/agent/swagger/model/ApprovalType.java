package ai.agent.swagger.model;

/** Тип ожидающего аппрува от пользователя. */
public enum ApprovalType {
    /** Подтверждение запуска сгенерированного кода */
    CODE_EXECUTION,
    /** Подтверждение выполнения HTTP-запроса к внешнему API */
    API_CALL
}
