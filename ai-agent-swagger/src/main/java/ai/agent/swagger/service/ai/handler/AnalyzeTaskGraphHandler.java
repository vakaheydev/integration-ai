package ai.agent.swagger.service.ai.handler;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskType;
import ai.agent.swagger.service.PromptBuilderService;
import ai.agent.swagger.service.SwaggerServiceDocument;
import ai.agent.swagger.service.ai.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalyzeTaskGraphHandler implements TaskGraphNodeHandler {

    private final AiChatService aiChatService;
    private final PromptBuilderService promptBuilderService;
    private final SwaggerServiceDocument swaggerServiceDocument;

    public AnalyzeTaskGraphHandler(AiChatService aiChatService,
                                   PromptBuilderService promptBuilderService,
                                   SwaggerServiceDocument swaggerServiceDocument) {
        this.aiChatService = aiChatService;
        this.promptBuilderService = promptBuilderService;
        this.swaggerServiceDocument = swaggerServiceDocument;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.ANALYZE;
    }

    @Override
    public String analyze(Task task, String availableTools) {
        String prompt;
        if (hasDocument(task)) {
            SwaggerDocument document = getDocument(task);
            prompt = promptBuilderService.getHandleTaskAnalyzePrompt(
                    task.getDescription(), task.getDocumentId(),
                    document.getDocumentSummary(), document.getMethodSummary(), availableTools,
                    task.getPreviousResult(), task.getUserMessage(), task.getChainInput());
        } else {
            prompt = promptBuilderService.getHandleTaskAnalyzeGeneralPrompt(
                    task.getDescription(), availableTools,
                    task.getPreviousResult(), task.getUserMessage(), task.getChainInput());
        }
        return aiChatService.chatForTaskAnalysis(task, prompt, task.getModelName());
    }

    @Override
    public String retryAnalyze(Task task, String feedback, String availableTools) {
        String prompt;
        if (hasDocument(task)) {
            SwaggerDocument document = getDocument(task);
            prompt = promptBuilderService.getHandleTaskRetryPrompt(
                    task.getDescription(), task.getDocumentId(), feedback,
                    document.getDocumentSummary(), document.getMethodSummary(), availableTools);
        } else {
            prompt = promptBuilderService.getHandleTaskRetryGeneralPrompt(task.getDescription(), feedback, availableTools);
        }
        return aiChatService.chatForTaskAnalysis(task, prompt, task.getModelName());
    }

    @Override
    public String execute(Task task, String analysis) {
        String prompt;
        if (hasDocument(task)) {
            prompt = promptBuilderService.getHandleTaskExecutePrompt(task.getDescription(), task.getDocumentId(), analysis);
        } else {
            prompt = promptBuilderService.getHandleTaskExecuteGeneralPrompt(task.getDescription(), analysis);
        }
        return aiChatService.chatForTaskAnalysis(task, prompt, task.getModelName());
    }

    @Override
    public String executeAfterError(Task task, String analysis, String errorMessage) {
        String prompt;
        if (hasDocument(task)) {
            prompt = promptBuilderService.getHandleTaskExecuteAfterErrorPrompt(
                    task.getDescription(), task.getDocumentId(), analysis, errorMessage);
        } else {
            prompt = promptBuilderService.getHandleTaskExecuteAfterErrorGeneralPrompt(
                    task.getDescription(), analysis, errorMessage);
        }
        return aiChatService.chatForTaskAnalysis(task, prompt, task.getModelName());
    }

    @Override
    public String retryExecute(Task task, String analysis, String feedback) {
        String prompt;
        if (hasDocument(task)) {
            prompt = promptBuilderService.getRetryHandleTaskAnalysisPrompt(
                    task.getDescription(), task.getDocumentId(), analysis, feedback);
        } else {
            prompt = promptBuilderService.getRetryHandleTaskAnalysisGeneralPrompt(
                    task.getDescription(), analysis, feedback);
        }
        return aiChatService.chatForTaskAnalysis(task, prompt, task.getModelName());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean hasDocument(Task task) {
        return task.getDocumentId() != null && !task.getDocumentId().isBlank();
    }

    private SwaggerDocument getDocument(Task task) {
        return swaggerServiceDocument.getSwaggerById(task.getDocumentId())
                .orElseThrow(() -> new IllegalStateException(
                        "Document specified in task not found: " + task.getDocumentId()));
    }
}

