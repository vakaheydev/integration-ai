package ai.agent.swagger.exception;

/**
 * Выбрасывается когда ИИ сгенерировал код/тесты и нужно
 * подтверждение пользователя перед запуском.
 */
public class UserApproveRequiredException extends RuntimeException {

    private final String code;

    public UserApproveRequiredException(String code) {
        super("Code/test execution requires user approval");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
