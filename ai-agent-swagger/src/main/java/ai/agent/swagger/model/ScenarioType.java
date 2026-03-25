package ai.agent.swagger.model;

/**
 * Типы сценариев — цепочек базовых задач.
 * Каждый сценарий определяет последовательность шагов (TaskType),
 * где результат предыдущего шага передаётся следующему через chainInput.
 */
public enum ScenarioType {
    ANALYZE_CODE,
    ANALYZE_TEST,
    ANALYZE_CODE_TEST
}
