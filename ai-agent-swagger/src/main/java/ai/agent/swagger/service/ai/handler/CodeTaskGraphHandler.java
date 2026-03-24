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
public class CodeTaskGraphHandler implements TaskGraphNodeHandler {

    private final AiChatService aiChatService;
    private final PromptBuilderService promptBuilderService;
    private final SwaggerServiceDocument swaggerServiceDocument;

    public CodeTaskGraphHandler(AiChatService aiChatService,
                                PromptBuilderService promptBuilderService,
                                SwaggerServiceDocument swaggerServiceDocument) {
        this.aiChatService = aiChatService;
        this.promptBuilderService = promptBuilderService;
        this.swaggerServiceDocument = swaggerServiceDocument;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CODE;
    }

    @Override
    public String analyze(Task task, String availableTools) {
        String prompt;
        if (hasDocument(task)) {
            SwaggerDocument document = getDocument(task);
            prompt = promptBuilderService.getCodeTaskAnalyzePrompt(
                    task.getDescription(), task.getDocumentId(),
                    document.getDocumentSummary(), document.getMethodSummary(), availableTools,
                    task.getPreviousResult(), task.getUserMessage(), task.getChainInput());
        } else {
            prompt = promptBuilderService.getCodeTaskAnalyzeGeneralPrompt(
                    task.getDescription(), availableTools,
                    task.getPreviousResult(), task.getUserMessage(), task.getChainInput());
        }
        return aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName());
    }

    @Override
    public String retryAnalyze(Task task, String feedback, String availableTools) {
        String prompt;
        if (hasDocument(task)) {
            SwaggerDocument document = getDocument(task);
            prompt = promptBuilderService.getCodeTaskRetryPrompt(
                    task.getDescription(), task.getDocumentId(), feedback,
                    document.getDocumentSummary(), document.getMethodSummary(), availableTools);
        } else {
            prompt = promptBuilderService.getCodeTaskRetryGeneralPrompt(task.getDescription(), feedback, availableTools);
        }
        return aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName());
    }

    @Override
    public String execute(Task task, String analysis) {
        String prompt;
        if (hasDocument(task)) {
            prompt = promptBuilderService.getCodeTaskExecutePrompt(task.getDescription(), task.getDocumentId(), analysis);
        } else {
            prompt = promptBuilderService.getCodeTaskExecuteGeneralPrompt(task.getDescription(), analysis);
        }
        return aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName());
    }

    @Override
    public String executeAfterError(Task task, String analysis, String errorMessage) {
        String prompt;
        if (hasDocument(task)) {
            prompt = promptBuilderService.getCodeTaskExecuteAfterErrorPrompt(
                    task.getDescription(), task.getDocumentId(), analysis, errorMessage);
        } else {
            prompt = promptBuilderService.getCodeTaskExecuteAfterErrorGeneralPrompt(
                    task.getDescription(), analysis, errorMessage);
        }
        return aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName());
    }

    @Override
    public String retryExecute(Task task, String analysis, String feedback) {
        String prompt;
        if (hasDocument(task)) {
            prompt = promptBuilderService.getCodeTaskRetryExecutePrompt(
                    task.getDescription(), task.getDocumentId(), analysis, feedback);
        } else {
            prompt = promptBuilderService.getCodeTaskRetryExecuteGeneralPrompt(
                    task.getDescription(), analysis, feedback);
        }
        return aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName());
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
