package ai.agent.swagger.service.ai;

/**
 * Thread-local контекст для передачи userId в SwaggerTools.
 * Устанавливается перед вызовом ИИ-агента и очищается после.
 */
public class SwaggerToolsContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    public static void set(String userId) {
        USER_ID.set(userId);
    }

    public static String get() {
        String userId = USER_ID.get();
        if (userId == null) {
            throw new IllegalStateException("SwaggerToolsContext: userId is not set in current thread");
        }
        return userId;
    }

    public static void clear() {
        USER_ID.remove();
    }
}

