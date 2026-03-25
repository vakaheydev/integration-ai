package ai.agent.swagger.model;

import java.util.List;
import java.util.Map;

/**
 * Статическая конфигурация сценариев — цепочек базовых типов задач.
 * Сценарий выполняется последовательно: каждый шаг получает результат предыдущего.
 */
public final class TaskScenario {

    private TaskScenario() {}

    private static final Map<ScenarioType, List<TaskType>> SCENARIOS = Map.of(
            ScenarioType.ANALYZE_CODE,      List.of(TaskType.ANALYZE, TaskType.CODE),
            ScenarioType.ANALYZE_TEST,      List.of(TaskType.ANALYZE, TaskType.TEST),
            ScenarioType.ANALYZE_CODE_TEST, List.of(TaskType.ANALYZE, TaskType.CODE, TaskType.TEST)
    );

    /** Возвращает цепочку базовых типов для сценария */
    public static List<TaskType> getSteps(ScenarioType type) {
        List<TaskType> steps = SCENARIOS.get(type);
        if (steps == null) {
            throw new IllegalArgumentException(type + " is not a scenario");
        }
        return steps;
    }

    /** Возвращает следующий шаг сценария или null, если текущий — последний */
    public static TaskType getNextStep(ScenarioType scenarioType, int currentStep) {
        List<TaskType> steps = getSteps(scenarioType);
        int next = currentStep + 1;
        return next < steps.size() ? steps.get(next) : null;
    }

    /** Возвращает первый шаг сценария */
    public static TaskType getFirstStep(ScenarioType scenarioType) {
        return getSteps(scenarioType).get(0);
    }
}
