package ai.agent.swagger.service;

import ai.agent.swagger.config.SwaggerPromptsProperties;
import ai.agent.swagger.model.TaskType;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromptBuilderService {
    private final SwaggerPromptsProperties props;

    public PromptBuilderService(SwaggerPromptsProperties props) {
        this.props = props;
    }

    public String getAnalystRolePrompt(String prompt) {
        return props.getRoles().getAnalyst() + ".\n" + prompt;
    }

    public String getProgrammerRolePrompt(String prompt) {
        return props.getRoles().getProgrammer() + ".\n" + prompt;
    }

    // ── output rule accessors ─────────────────────────────────────────────────

    private String outputText()           { return props.getOutputs().getText(); }
    private String outputYesNo()          { return props.getOutputs().getYesNo(); }
    private String outputAnalysisPlan()   { return props.getOutputs().getAnalysisPlan(); }
    private String outputCode()           { return props.getOutputs().getCode(); }
    private String outputTest()           { return props.getOutputs().getTest(); }
    private String outputAnalysisResult() { return props.getOutputs().getAnalysisResult(); }

    /**
     * Возвращает ожидаемые output rules для конкретного типа таски.
     * Используется в review-стадии для проверки формата результата.
     */
    public String getExpectedOutputRules(TaskType taskType) {
        if (taskType == null) return outputAnalysisResult();
        return switch (taskType) {
            case CODE, ANALYZE_CODE -> outputCode();
            case TEST, ANALYZE_TEST -> outputTest();
            case ANALYZE -> outputAnalysisResult();
        };
    }

    public String getReviewPreviousResultPrompt(String taskDescription, String previousResult, String userMessage) {
        String template = props.getActions().getHandleTask().getReviewPreviousResult();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "previousResult", previousResult != null ? previousResult : "",
                "userMessage", userMessage != null ? userMessage : ""));
        return getAnalystRolePrompt(prompt);
    }

    public String getVectorSearchNotFoundPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            userPrompt = "empty prompt";
        }

        String template = props.getActions().getVectorSearch().getNotFound();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskReviewResultPrompt(String taskDescription, String taskResult, String taskStatus) {
        String template = props.getActions().getHandleTask().getReviewTaskResult();
        String prompt = replacePrompt(template, Map.of("taskDescription",  taskDescription, "taskResult", taskResult, "taskStatus", taskStatus));
        return getAnalystRolePrompt(prompt);
    }

   public String getHandleTaskReviewAnalysisPrompt(String taskDescription, String analysis) {
        String template = props.getActions().getHandleTask().getReviewTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription",  taskDescription, "analysis", analysis));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskAnalyzePrompt(String taskDescription, String documentId, String documentSummary, String documentMethodSummary, String availableTools) {
        return getHandleTaskAnalyzePrompt(taskDescription, documentId, documentSummary, documentMethodSummary, availableTools, null, null);
    }

    public String getHandleTaskAnalyzePrompt(String taskDescription, String documentId, String documentSummary, String documentMethodSummary, String availableTools, String previousResult, String userMessage) {
        String template = props.getActions().getHandleTask().getAnalyzeTask();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription, "documentId", documentId,
                "availableTools", availableTools, "swaggerSummary", documentSummary,
                "swaggerMethodSummary", documentMethodSummary,
                "restartContext", buildRestartContext(previousResult, userMessage)));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskRetryPrompt(String taskDescription, String documentId, String feedback, String documentSummary, String documentMethodSummary, String availableTools) {
        String template = props.getActions().getHandleTask().getRetryAnalyzeTask();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "feedback", feedback, "availableTools", availableTools, "swaggerSummary", documentSummary, "swaggerMethodSummary", documentMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskExecutePrompt(String taskDescription, String documentId, String analysis) {
        String template = props.getActions().getHandleTask().getHandleTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskExecuteAfterErrorPrompt(String taskDescription, String documentId, String analysis, String errorMessage) {
        String template = props.getActions().getHandleTask().getHandleTaskAnalysisAfterError();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis, "errorMessage", errorMessage != null ? errorMessage : "unknown error"));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskExecuteAfterErrorGeneralPrompt(String taskDescription, String analysis, String errorMessage) {
        String template = props.getActions().getHandleTask().getHandleTaskAnalysisAfterErrorGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis, "errorMessage", errorMessage != null ? errorMessage : "unknown error"));
        return getAnalystRolePrompt(prompt);
    }

    public String getReviewHandleAnalysisPrompt(String taskDescription, String taskResult) {
        return getReviewHandleAnalysisPrompt(taskDescription, taskResult, "");
    }

    public String getReviewHandleAnalysisPrompt(String taskDescription, String taskResult, String expectedOutputRules) {
        String template = props.getActions().getHandleTask().getReviewHandleAnalysis();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "taskResult", taskResult,
                "expectedOutputRules", expectedOutputRules != null ? expectedOutputRules : ""));
        return getAnalystRolePrompt(prompt);
    }

    public String getReviewHandleAnalysisSolutionPrompt(String feedback) {
        String template = props.getActions().getHandleTask().getReviewHandleAnalysisSolution();
        String prompt = replacePrompt(template, Map.of("feedback", feedback));
        return getAnalystRolePrompt(prompt);
    }

    public String getRetryHandleTaskAnalysisPrompt(String taskDescription, String documentId, String analysis, String feedback) {
        String template = props.getActions().getHandleTask().getRetryHandleTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis, "feedback", feedback));
        return getAnalystRolePrompt(prompt);
    }

    // ── General (no document) variants ──────────────────────────────────────

    public String getHandleTaskAnalyzeGeneralPrompt(String taskDescription, String availableTools) {
        return getHandleTaskAnalyzeGeneralPrompt(taskDescription, availableTools, null, null);
    }

    public String getHandleTaskAnalyzeGeneralPrompt(String taskDescription, String availableTools, String previousResult, String userMessage) {
        String template = props.getActions().getHandleTask().getAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription, "availableTools", availableTools,
                "restartContext", buildRestartContext(previousResult, userMessage)));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskRetryGeneralPrompt(String taskDescription, String feedback, String availableTools) {
        String template = props.getActions().getHandleTask().getRetryAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "feedback", feedback, "availableTools", availableTools));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskExecuteGeneralPrompt(String taskDescription, String analysis) {
        String template = props.getActions().getHandleTask().getHandleTaskAnalysisGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis));
        return getAnalystRolePrompt(prompt);
    }

    // ── Code task variants (programmer role) ─────────────────────────────────

    public String getCodeTaskAnalyzePrompt(String taskDescription, String documentId, String documentSummary, String documentMethodSummary, String availableTools) {
        return getCodeTaskAnalyzePrompt(taskDescription, documentId, documentSummary, documentMethodSummary, availableTools, null, null);
    }

    public String getCodeTaskAnalyzePrompt(String taskDescription, String documentId, String documentSummary, String documentMethodSummary, String availableTools, String previousResult, String userMessage) {
        String template = props.getActions().getCodeTask().getAnalyzeTask();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription, "documentId", documentId,
                "availableTools", availableTools, "swaggerSummary", documentSummary,
                "swaggerMethodSummary", documentMethodSummary,
                "restartContext", buildRestartContext(previousResult, userMessage)));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskRetryPrompt(String taskDescription, String documentId, String feedback, String documentSummary, String documentMethodSummary, String availableTools) {
        String template = props.getActions().getCodeTask().getRetryAnalyzeTask();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "feedback", feedback, "availableTools", availableTools, "swaggerSummary", documentSummary, "swaggerMethodSummary", documentMethodSummary));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskExecutePrompt(String taskDescription, String documentId, String analysis) {
        String template = props.getActions().getCodeTask().getHandleTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskExecuteAfterErrorPrompt(String taskDescription, String documentId, String analysis, String errorMessage) {
        String template = props.getActions().getCodeTask().getHandleTaskAnalysisAfterError();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis, "errorMessage", errorMessage != null ? errorMessage : "unknown error"));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskRetryExecutePrompt(String taskDescription, String documentId, String analysis, String feedback) {
        String template = props.getActions().getCodeTask().getRetryHandleTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis, "feedback", feedback));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskAnalyzeGeneralPrompt(String taskDescription, String availableTools) {
        return getCodeTaskAnalyzeGeneralPrompt(taskDescription, availableTools, null, null);
    }

    public String getCodeTaskAnalyzeGeneralPrompt(String taskDescription, String availableTools, String previousResult, String userMessage) {
        String template = props.getActions().getCodeTask().getAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription, "availableTools", availableTools,
                "restartContext", buildRestartContext(previousResult, userMessage)));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskRetryGeneralPrompt(String taskDescription, String feedback, String availableTools) {
        String template = props.getActions().getCodeTask().getRetryAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "feedback", feedback, "availableTools", availableTools));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskExecuteGeneralPrompt(String taskDescription, String analysis) {
        String template = props.getActions().getCodeTask().getHandleTaskAnalysisGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskExecuteAfterErrorGeneralPrompt(String taskDescription, String analysis, String errorMessage) {
        String template = props.getActions().getCodeTask().getHandleTaskAnalysisAfterErrorGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis, "errorMessage", errorMessage != null ? errorMessage : "unknown error"));
        return getProgrammerRolePrompt(prompt);
    }

    public String getCodeTaskRetryExecuteGeneralPrompt(String taskDescription, String analysis, String feedback) {
        String template = props.getActions().getCodeTask().getRetryHandleTaskAnalysisGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis, "feedback", feedback));
        return getProgrammerRolePrompt(prompt);
    }

    // ── Test task variants (programmer role) ─────────────────────────────────

    public String getTestTaskAnalyzePrompt(String taskDescription, String documentId, String documentSummary, String documentMethodSummary, String availableTools) {
        return getTestTaskAnalyzePrompt(taskDescription, documentId, documentSummary, documentMethodSummary, availableTools, null, null);
    }

    public String getTestTaskAnalyzePrompt(String taskDescription, String documentId, String documentSummary, String documentMethodSummary, String availableTools, String previousResult, String userMessage) {
        String template = props.getActions().getTestTask().getAnalyzeTask();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription, "documentId", documentId,
                "availableTools", availableTools, "swaggerSummary", documentSummary,
                "swaggerMethodSummary", documentMethodSummary,
                "restartContext", buildRestartContext(previousResult, userMessage)));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskRetryPrompt(String taskDescription, String documentId, String feedback, String documentSummary, String documentMethodSummary, String availableTools) {
        String template = props.getActions().getTestTask().getRetryAnalyzeTask();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "feedback", feedback, "availableTools", availableTools, "swaggerSummary", documentSummary, "swaggerMethodSummary", documentMethodSummary));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskExecutePrompt(String taskDescription, String documentId, String analysis) {
        String template = props.getActions().getTestTask().getHandleTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskExecuteAfterErrorPrompt(String taskDescription, String documentId, String analysis, String errorMessage) {
        String template = props.getActions().getTestTask().getHandleTaskAnalysisAfterError();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis, "errorMessage", errorMessage != null ? errorMessage : "unknown error"));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskRetryExecutePrompt(String taskDescription, String documentId, String analysis, String feedback) {
        String template = props.getActions().getTestTask().getRetryHandleTaskAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "analysis", analysis, "feedback", feedback));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskAnalyzeGeneralPrompt(String taskDescription, String availableTools) {
        return getTestTaskAnalyzeGeneralPrompt(taskDescription, availableTools, null, null);
    }

    public String getTestTaskAnalyzeGeneralPrompt(String taskDescription, String availableTools, String previousResult, String userMessage) {
        String template = props.getActions().getTestTask().getAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription, "availableTools", availableTools,
                "restartContext", buildRestartContext(previousResult, userMessage)));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskRetryGeneralPrompt(String taskDescription, String feedback, String availableTools) {
        String template = props.getActions().getTestTask().getRetryAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "feedback", feedback, "availableTools", availableTools));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskExecuteGeneralPrompt(String taskDescription, String analysis) {
        String template = props.getActions().getTestTask().getHandleTaskAnalysisGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskExecuteAfterErrorGeneralPrompt(String taskDescription, String analysis, String errorMessage) {
        String template = props.getActions().getTestTask().getHandleTaskAnalysisAfterErrorGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis, "errorMessage", errorMessage != null ? errorMessage : "unknown error"));
        return getProgrammerRolePrompt(prompt);
    }

    public String getTestTaskRetryExecuteGeneralPrompt(String taskDescription, String analysis, String feedback) {
        String template = props.getActions().getTestTask().getRetryHandleTaskAnalysisGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis, "feedback", feedback));
        return getProgrammerRolePrompt(prompt);
    }

    public String getRetryHandleTaskAnalysisGeneralPrompt(String taskDescription, String analysis, String feedback) {
        String template = props.getActions().getHandleTask().getRetryHandleTaskAnalysisGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "analysis", analysis, "feedback", feedback));
        return getAnalystRolePrompt(prompt);
    }

    public String getTaskChatPrompt(String taskId, String taskType, String taskStatus,
                                    String taskStatusDescription, String taskDescription,
                                    String taskResult, String taskCurrentStage, String userQuestion) {
        String template = props.getActions().getHandleTask().getTaskChat();
        String prompt = replacePrompt(template, Map.of(
                "taskId", taskId,
                "taskType", taskType,
                "taskStatus", taskStatus,
                "taskStatusDescription", taskStatusDescription != null ? taskStatusDescription : "N/A",
                "taskDescription", taskDescription,
                "taskResult", taskResult != null ? taskResult : "N/A",
                "taskCurrentStage", taskCurrentStage != null ? taskCurrentStage : "N/A",
                "userQuestion", userQuestion
        ));
        return getAnalystRolePrompt(prompt);
    }

    public String getHandleTaskReviewSolutionPrompt(String review) {
        String template = props.getActions().getHandleTask().getReviewTaskAnalysisSolution();
        String prompt = replacePrompt(template, Map.of("review",  review));
        return getAnalystRolePrompt(prompt);
    }

    public String getDocumentChatAnalyze(String userPrompt, String swaggerSummary, String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getAnalyze();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt, "swaggerSummary", swaggerSummary, "swaggerMethodSummary", swaggerMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getDocumentChatUpload(String swaggerInfoSummary, String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getUpload();
        String prompt = replacePrompt(template, Map.of(
                "swaggerInfoSummary", swaggerInfoSummary != null ? swaggerInfoSummary : "",
                "swaggerMethodSummary", swaggerMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getDocumentChatCode(String userPrompt, String swaggerSummary, String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getCode();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt, "swaggerSummary", swaggerSummary, "swaggerMethodSummary", swaggerMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getExtractKeywordsPrompt(String userPrompt) {
        String template = props.getActions().getVectorSearch().getExtractKeyWords();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt));
        return getAnalystRolePrompt(prompt);
    }

    /**
     * Формирует блок контекста рестарта для промпта.
     * Если таска рестартуется с previousResult и/или userMessage — ИИ получает эту информацию
     * и учитывает её при составлении нового плана.
     */
    private String buildRestartContext(String previousResult, String userMessage) {
        if ((previousResult == null || previousResult.isBlank())
                && (userMessage == null || userMessage.isBlank())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<<<RESTART_CONTEXT>>>\n");
        sb.append("This task is being re-executed. A previous attempt was made but was unsatisfactory.\n");
        if (previousResult != null && !previousResult.isBlank()) {
            sb.append("PREVIOUS_RESULT:\n").append(previousResult).append("\n");
        }
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append("USER_FEEDBACK:\n").append(userMessage).append("\n");
        }
        sb.append("<<<END_RESTART_CONTEXT>>>\n");
        sb.append("IMPORTANT: Take the RESTART_CONTEXT into account. Fix what the user found unsatisfactory.\n");
        return sb.toString();
    }

    // ── Code execution prompts ─────────────────────────────────────────────

    public String getDisapprovedRewriteCodePrompt(String taskDescription, String code, String userFeedback) {
        String template = props.getActions().getCodeExecution().getDisapprovedRewriteCode();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "code", code != null ? code : "",
                "userFeedback", userFeedback != null ? userFeedback : ""));
        return getProgrammerRolePrompt(prompt);
    }

    public String getDisapprovedRewriteTestPrompt(String taskDescription, String code, String userFeedback) {
        String template = props.getActions().getCodeExecution().getDisapprovedRewriteTest();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "code", code != null ? code : "",
                "userFeedback", userFeedback != null ? userFeedback : ""));
        return getProgrammerRolePrompt(prompt);
    }

    public String getApproveDescriptionPrompt(String taskDescription, String codeSummary) {
        String template = props.getActions().getCodeExecution().getApproveDescription();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "codeSummary", codeSummary != null ? codeSummary : ""));
        return getAnalystRolePrompt(prompt);
    }

    public String getReviewCodeExecutionPrompt(String taskDescription, String code, String executionResult) {
        String template = props.getActions().getCodeExecution().getReviewCodeExecution();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "code", code != null ? code : "",
                "executionResult", executionResult != null ? executionResult : ""));
        return getAnalystRolePrompt(prompt);
    }

    public String getReviewCodeExecutionSolutionPrompt(String feedback) {
        String template = props.getActions().getCodeExecution().getReviewCodeExecutionSolution();
        String prompt = replacePrompt(template, Map.of("feedback", feedback));
        return getAnalystRolePrompt(prompt);
    }

    public String getRewriteCodePrompt(String taskDescription, String code, String executionResult, String feedback) {
        String template = props.getActions().getCodeExecution().getRewriteCode();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "code", code != null ? code : "",
                "executionResult", executionResult != null ? executionResult : "",
                "feedback", feedback != null ? feedback : ""));
        return getProgrammerRolePrompt(prompt);
    }

    public String getRewriteTestPrompt(String taskDescription, String code, String executionResult, String feedback) {
        String template = props.getActions().getCodeExecution().getRewriteTest();
        String prompt = replacePrompt(template, Map.of(
                "taskDescription", taskDescription,
                "code", code != null ? code : "",
                "executionResult", executionResult != null ? executionResult : "",
                "feedback", feedback != null ? feedback : ""));
        return getProgrammerRolePrompt(prompt);
    }

    private String replacePrompt(String prompt, Map<String, String> placeHolders) {
        // Объединяем пользовательские плейсхолдеры с output rules
        var all = new java.util.HashMap<>(placeHolders);
        all.put("outputRules.text",           outputText());
        all.put("outputRules.yesNo",          outputYesNo());
        all.put("outputRules.analysisPlan",   outputAnalysisPlan());
        all.put("outputRules.code",           outputCode());
        all.put("outputRules.test",           outputTest());
        all.put("outputRules.analysisResult", outputAnalysisResult());
        StringSubstitutor sub = new StringSubstitutor(all);
        return sub.replace(prompt);
    }
}
