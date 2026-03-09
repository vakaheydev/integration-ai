package ai.agent.swagger.service;

import ai.agent.swagger.config.SwaggerPromptsProperties;
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
        String template = props.getActions().getHandleTask().getAnalyzeTask();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "documentId", documentId, "availableTools", availableTools, "swaggerSummary", documentSummary, "swaggerMethodSummary", documentMethodSummary));
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
        String template = props.getActions().getHandleTask().getReviewHandleAnalysis();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "taskResult", taskResult));
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
        String template = props.getActions().getHandleTask().getAnalyzeTaskGeneral();
        String prompt = replacePrompt(template, Map.of("taskDescription", taskDescription, "availableTools", availableTools));
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

    public String getDocumentChatUpload(String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getUpload();
        String prompt = replacePrompt(template, Map.of("swaggerMethodSummary", swaggerMethodSummary));
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

    private String replacePrompt(String prompt, Map<String, String> placeHolders) {
        StringSubstitutor sub = new StringSubstitutor(placeHolders);
        return sub.replace(prompt);
    }
}
