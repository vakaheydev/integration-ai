package ai.agent.swagger.exception;

/**
 * Выбрасывается когда ИИ решает, что для продолжения работы
 * необходим дополнительный ввод от пользователя.
 */
public class UserInputRequiredException extends RuntimeException {

    private final String question;

    public UserInputRequiredException(String question) {
        super("AI requires user input: " + question);
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }
}
