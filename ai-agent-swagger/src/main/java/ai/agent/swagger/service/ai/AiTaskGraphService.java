package ai.agent.swagger.service.ai;

import ai.agent.swagger.exception.UserApproveRequiredException;
import ai.agent.swagger.exception.UserInputRequiredException;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.model.TaskType;
import ai.agent.swagger.service.TaskService;
import ai.agent.swagger.service.ai.handler.TaskGraphHandlerRouter;
import ai.agent.swagger.service.ai.handler.TaskGraphNodeHandler;
import ai.agent.swagger.service.executor.CodeExecutionResult;
import ai.agent.swagger.service.executor.CodeExtractor;
import ai.agent.swagger.service.executor.PythonCodeExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class TaskState extends AgentState {

    public static final String TASK_ANALYSIS = "analyze_task";
    public static final String FEEDBACK = "feedback";
    public static final String FEEDBACK_SOLUTION = "feedback_solution";
    public static final String RETRY_CNT = "retry_cnt";
    public static final String RESULT = "result";
    public static final String FAILED = "failed";
    public static final String PREVIOUS_REVIEW = "previous_review";
    public static final String EXECUTION_RESULT = "execution_result";
    public static final String EXEC_RETRY_CNT = "exec_retry_cnt";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            TASK_ANALYSIS, Channels.base(() -> ""),
            FEEDBACK, Channels.base(() -> ""),
            FEEDBACK_SOLUTION, Channels.base(() -> ""),
            RETRY_CNT, Channels.base(() -> 0),
            RESULT, Channels.base(() -> ""),
            FAILED, Channels.base(() -> ""),
            PREVIOUS_REVIEW, Channels.base(() -> ""),
            EXECUTION_RESULT, Channels.base(() -> ""),
            EXEC_RETRY_CNT, Channels.base(() -> 0)
    );

    public TaskState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> taskAnalysis() { return value(TASK_ANALYSIS); }
    public Optional<String> feedback() { return value(FEEDBACK); }
    public Optional<String> feedbackSolution() { return value(FEEDBACK_SOLUTION); }
    public int retryCnt() { return (int) value(RETRY_CNT).orElse(0); }
    public Optional<String> result() { return value(RESULT); }
    public Optional<String> previousReview() { return value(PREVIOUS_REVIEW); }
    public Optional<String> executionResult() { return value(EXECUTION_RESULT); }
    public int execRetryCnt() { return (int) value(EXEC_RETRY_CNT).orElse(0); }

    /** Возвращает текст ошибки, или null если ошибки не было */
    public String failedMessage() {
        String val = (String) value(FAILED).orElse("");
        return val.isBlank() ? null : val;
    }
}

@Slf4j
@Service
public class AiTaskGraphService {

    private final TaskService taskService;
    private final ToolDescriptionProvider toolDescriptionProvider;
    private final TaskGraphHandlerRouter handlerRouter;
    private final AiChatService aiChatService;
    private final ai.agent.swagger.service.PromptBuilderService promptBuilderService;
    private final PythonCodeExecutorService pythonCodeExecutorService;

    public AiTaskGraphService(TaskService taskService,
                              ToolDescriptionProvider toolDescriptionProvider,
                              TaskGraphHandlerRouter handlerRouter,
                              AiChatService aiChatService,
                              ai.agent.swagger.service.PromptBuilderService promptBuilderService,
                              PythonCodeExecutorService pythonCodeExecutorService) {
        this.taskService = taskService;
        this.toolDescriptionProvider = toolDescriptionProvider;
        this.handlerRouter = handlerRouter;
        this.aiChatService = aiChatService;
        this.promptBuilderService = promptBuilderService;
        this.pythonCodeExecutorService = pythonCodeExecutorService;
    }

    private static final Pattern NEED_USER_INPUT_PATTERN = Pattern.compile(
            "<<<NEED_USER_INPUT>>>(.*?)<<<END_NEED_USER_INPUT>>>", Pattern.DOTALL);

    /**
     * Извлекает вопрос ИИ из ответа, если он содержит маркер NEED_USER_INPUT.
     * @return вопрос или null если маркер не найден
     */
    static String extractUserInputQuestion(String aiResponse) {
        if (aiResponse == null) return null;
        Matcher matcher = NEED_USER_INPUT_PATTERN.matcher(aiResponse);
        if (matcher.find()) {
            return matcher.group(1).strip();
        }
        return null;
    }

    public Map<String, Object> runGraphTaskHandle(Task task) throws GraphStateException {
        String availableTools = toolDescriptionProvider.getToolsDescription();
        TaskGraphNodeHandler handler = handlerRouter.getHandler(task.getType());

        boolean isCodeOrTest = task.getType() == TaskType.CODE || task.getType() == TaskType.TEST;

        var graph = new StateGraph<>(TaskState.SCHEMA, TaskState::new)
                .addNode("review_previous_result", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Reviewing previous result and user feedback");
                    String prompt = promptBuilderService.getReviewPreviousResultPrompt(
                            task.getDescription(), task.getPreviousResult(), task.getUserMessage());
                    String review = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Reviewed previous result", review, TaskStatus.COMPLETED);
                    return Map.of(TaskState.PREVIOUS_REVIEW, review);
                }))

                .addNode("analyze_task", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Analyzing task");
                    // Если есть ревью предыдущего результата — дополняем userMessage
                    String previousReview = state.previousReview().orElse("");
                    if (!previousReview.isBlank()) {
                        String original = task.getUserMessage() != null ? task.getUserMessage() : "";
                        task.setUserMessage(original + "\n\nAI REVIEW OF PREVIOUS RESULT:\n" + previousReview);
                    }
                    // Если есть ответ пользователя на предыдущий вопрос ИИ — добавляем контекст
                    if (task.getUserInputResponse() != null && !task.getUserInputResponse().isBlank()) {
                        String original = task.getUserMessage() != null ? task.getUserMessage() : "";
                        String inputContext = "\n\n<<<USER_INPUT_CONTEXT>>>\n"
                                + "You previously asked the user a clarifying question:\n"
                                + "QUESTION: " + (task.getAiQuestion() != null ? task.getAiQuestion() : "N/A") + "\n"
                                + "USER ANSWER: " + task.getUserInputResponse() + "\n"
                                + "<<<END_USER_INPUT_CONTEXT>>>\n"
                                + "IMPORTANT: Use the user's answer to proceed with the task. Do NOT ask the same question again.";
                        task.setUserMessage(original + inputContext);
                    }
                    String answer = Objects.toString(handler.analyze(task, availableTools), "");

                    // Проверяем, не запросил ли ИИ ввод пользователя
                    String question = extractUserInputQuestion(answer);
                    if (question != null) {
                        log.info("AI requested user input for task id={}: {}", task.getId(), question);
                        taskService.changeStage(task.getId(), stageId, "AI requested user input", question, TaskStatus.COMPLETED);
                        // Сохраняем вопрос через patch без копирования всего task (task.toBuilder() скопирует status=CREATED)
                        Task patch = new Task();
                        patch.setId(task.getId());
                        patch.setAiQuestion(question);
                        taskService.updateTask(patch);
                        throw new UserInputRequiredException(question);
                    }

                    taskService.changeStage(task.getId(), stageId, "Task analyzed", answer, TaskStatus.COMPLETED);
                    return Map.of(TaskState.TASK_ANALYSIS, answer);
                }))

                .addNode("retry_analyze_task", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry analyzing task");
                    int retryCnt = state.retryCnt() + 1;
                    String feedback = state.feedback().orElse("");
                    String answer = Objects.toString(handler.retryAnalyze(task, feedback, availableTools), "");
                    taskService.changeStage(task.getId(), stageId, "Retry analysis completed", answer, TaskStatus.COMPLETED);
                    return Map.of(TaskState.TASK_ANALYSIS, answer, TaskState.RETRY_CNT, retryCnt);
                }))

                .addNode("review_task_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Review task analysis");
                    String analysis = state.taskAnalysis().orElse("");
                    String prompt = promptBuilderService.getHandleTaskReviewAnalysisPrompt(task.getDescription(), analysis);
                    String feedback = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Analysis reviewed", feedback, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_task_analysis_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Review task analysis solution");
                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getHandleTaskReviewSolutionPrompt(feedback);
                    String solution = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Solution decided", solution, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Execute task according to analysis");
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String result = Objects.toString(handler.execute(task, analysis), "");
                        taskService.changeStage(task.getId(), stageId, "Task executed", result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, 0, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during task execution, will trigger retry", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Execution failed", errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("handle_analysis_after_error", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execution after error");
                    int retryCnt = state.retryCnt() + 1;
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String errorMessage = state.failedMessage();
                        String result = Objects.toString(handler.executeAfterError(task, analysis, errorMessage), "");
                        taskService.changeStage(task.getId(), stageId, "Retry execution succeeded", result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during handle_analysis_after_error", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Retry execution failed", errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("retry_handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execute task according to analysis");
                    int retryCnt = state.retryCnt() + 1;
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String feedback = state.feedback().orElse("");
                        String result = Objects.toString(handler.retryExecute(task, analysis, feedback), "");
                        taskService.changeStage(task.getId(), stageId, "Retry execution succeeded", result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during retry_handle_analysis", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Retry execution failed", errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("retry_handle_analysis_after_error", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execution after error (from review)");
                    int retryCnt = state.retryCnt() + 1;
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String errorMessage = state.failedMessage();
                        String result = Objects.toString(handler.executeAfterError(task, analysis, errorMessage), "");
                        taskService.changeStage(task.getId(), stageId, "Retry execution succeeded", result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during retry_handle_analysis_after_error", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Retry execution failed", errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("review_handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Reviewing execution result");
                    String result = state.result().orElse("");
                    String expectedOutputRules = promptBuilderService.getExpectedOutputRules(task.getType());
                    String prompt = promptBuilderService.getReviewHandleAnalysisPrompt(task.getDescription(), result, expectedOutputRules);
                    String feedback = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Execution reviewed", feedback, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_handle_analysis_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Deciding on execution review");
                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getReviewHandleAnalysisSolutionPrompt(feedback);
                    String solution = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Solution decided", solution, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("handle_result", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Handling final result");
                    String result = state.result().orElse("");
                    taskService.changeStage(task.getId(), stageId, "Final result ready", result, TaskStatus.COMPLETED);
                    return Map.of(TaskState.RESULT, result);
                }))

                // ── Code execution nodes (CODE/TEST only) ──────────────────────

                .addNode("rewrite_disapproved_code", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Rewriting code after user disapproval");
                    String code = task.getResult() != null ? task.getResult() : "";
                    String userFeedback = task.getApproveMessage() != null ? task.getApproveMessage() : "";
                    String prompt;
                    if (task.getType() == TaskType.TEST) {
                        prompt = promptBuilderService.getDisapprovedRewriteTestPrompt(
                                task.getDescription(), code, userFeedback);
                    } else {
                        prompt = promptBuilderService.getDisapprovedRewriteCodePrompt(
                                task.getDescription(), code, userFeedback);
                    }
                    String rewritten = Objects.toString(
                            aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Code rewritten after disapproval", rewritten, TaskStatus.COMPLETED);
                    return Map.of(TaskState.RESULT, rewritten);
                }))

                .addNode("approve_code", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Code generated, waiting for user approval");
                    String result = state.result().orElse("");
                    // Генерируем короткое описание того, что нужно заапрувить
                    String codeSummary = result.length() > 500 ? result.substring(0, 500) + "..." : result;
                    String approvePrompt = promptBuilderService.getApproveDescriptionPrompt(
                            task.getDescription(), codeSummary);
                    String approveDescription = Objects.toString(
                            aiChatService.chatStateless(approvePrompt, task.getModelName()), "");
                    // Сохраняем сгенерированный код и описание аппрува в task
                    Task patch = new Task();
                    patch.setId(task.getId());
                    patch.setResult(result);
                    patch.setApproveDescription(approveDescription);
                    taskService.updateTask(patch);
                    taskService.changeStage(task.getId(), stageId, "Awaiting user approval", result, TaskStatus.COMPLETED);
                    throw new UserApproveRequiredException(result);
                }))

                .addNode("execute_code", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Executing code");
                    // Код может быть в state (после rewrite) или в task (после approve)
                    String code = state.result().orElse("");
                    if (code.isBlank()) code = task.getResult() != null ? task.getResult() : "";
                    String extracted = CodeExtractor.extract(code).orElse(code);
                    CodeExecutionResult execResult = pythonCodeExecutorService.execute(extracted);
                    String summary = execResult.toSummary();
                    taskService.changeStage(task.getId(), stageId,
                            execResult.isSuccess() ? "Code executed successfully" : "Code execution failed",
                            summary,
                            execResult.isSuccess() ? TaskStatus.COMPLETED : TaskStatus.FAILED);
                    // Сохраняем код в state.result(), чтобы handle_result получил его в финале
                    return Map.of(TaskState.EXECUTION_RESULT, summary, TaskState.RESULT, code, TaskState.FAILED, "");
                }))

                .addNode("review_code_execution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Reviewing code execution results");
                    String code = state.result().orElse("");
                    if (code.isBlank()) code = task.getResult() != null ? task.getResult() : "";
                    String executionResult = state.executionResult().orElse("");
                    String prompt = promptBuilderService.getReviewCodeExecutionPrompt(
                            task.getDescription(), code, executionResult);
                    String feedback = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Code execution reviewed", feedback, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_code_execution_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Deciding on code execution result");
                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getReviewCodeExecutionSolutionPrompt(feedback);
                    String solution = Objects.toString(aiChatService.chatStateless(prompt, task.getModelName()), "");
                    taskService.changeStage(task.getId(), stageId, "Execution decision made", solution, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("rewrite_code", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Rewriting code after failed execution");
                    int retryCnt = state.execRetryCnt() + 1;
                    String code = state.result().orElse("");
                    if (code.isBlank()) code = task.getResult() != null ? task.getResult() : "";
                    String executionResult = state.executionResult().orElse("");
                    String feedback = state.feedback().orElse("");
                    String prompt;
                    if (task.getType() == TaskType.TEST) {
                        prompt = promptBuilderService.getRewriteTestPrompt(
                                task.getDescription(), code, executionResult, feedback);
                    } else {
                        prompt = promptBuilderService.getRewriteCodePrompt(
                                task.getDescription(), code, executionResult, feedback);
                    }
                    String rewritten = Objects.toString(
                            aiChatService.chatWithSwaggerTools(task.getUserId(), prompt, task.getModelName()), "");
                    // Обновляем результат в task
                    Task patch = new Task();
                    patch.setId(task.getId());
                    patch.setResult(rewritten);
                    taskService.updateTask(patch);
                    taskService.changeStage(task.getId(), stageId, "Code rewritten", rewritten, TaskStatus.COMPLETED);
                    return Map.of(TaskState.RESULT, rewritten, TaskState.EXEC_RETRY_CNT, retryCnt);
                }))

                // ── Edges ───────────────────────────────────────────────────────

                .addConditionalEdges(
                        START,
                        edge_async(state -> {
                            // Если таска одобрена для запуска кода — сразу к execute_code
                            if (task.isApproved()) return "execute_code";

                            // Если код был отклонён пользователем — переписать с учётом комментария
                            boolean isDisapproved = !task.isApproved()
                                    && task.getApproveMessage() != null
                                    && !task.getApproveMessage().isBlank();
                            if (isDisapproved) return "rewrite_disapproved";

                            // Если таска возобновлена после ответа пользователя — сразу к анализу
                            boolean isResumedAfterUserInput = task.getUserInputResponse() != null
                                    && !task.getUserInputResponse().isBlank();
                            if (isResumedAfterUserInput) return "analyze";

                            boolean isRestarted = (task.getPreviousResult() != null && !task.getPreviousResult().isBlank())
                                    || (task.getUserMessage() != null && !task.getUserMessage().isBlank());
                            return isRestarted ? "review_previous" : "analyze";
                        }),
                        Map.of("review_previous", "review_previous_result",
                               "analyze", "analyze_task",
                               "execute_code", "execute_code",
                               "rewrite_disapproved", "rewrite_disapproved_code")
                )
                .addEdge("review_previous_result", "analyze_task")
                .addEdge("analyze_task", "review_task_analysis")
                .addEdge("retry_analyze_task", "review_task_analysis")
                .addEdge("review_task_analysis", "review_task_analysis_solution")

                .addConditionalEdges(
                        "review_task_analysis_solution",
                        edge_async(state -> {
                            int retryCnt = state.retryCnt();
                            if (retryCnt > 2) {
                                log.info("Превышено количество попыток, ответ считается одобренным");
                                return "approved";
                            }
                            String feedbackSolution = state.feedbackSolution().orElse("no");
                            if ("yes".equalsIgnoreCase(feedbackSolution.strip())) return "approved";
                            return "retry";
                        }),
                        Map.of("approved", "handle_analysis", "retry", "retry_analyze_task")
                )

                .addConditionalEdges(
                        "handle_analysis",
                        edge_async(state -> {
                            String error = state.failedMessage();
                            if (error == null || error.isBlank()) return "ok";
                            if (state.retryCnt() > 2) throw new IllegalStateException(
                                    "Task execution failed after maximum retries for task id=" + task.getId() + ". Last error: " + error);
                            return "error";
                        }),
                        Map.of("ok", "review_handle_analysis", "error", "handle_analysis_after_error")
                )

                .addConditionalEdges(
                        "handle_analysis_after_error",
                        edge_async(state -> {
                            String error = state.failedMessage();
                            if (error == null || error.isBlank()) return "ok";
                            if (state.retryCnt() > 2) throw new IllegalStateException(
                                    "Task execution failed after maximum retries for task id=" + task.getId() + ". Last error: " + error);
                            return "error";
                        }),
                        Map.of("ok", "review_handle_analysis", "error", "handle_analysis_after_error")
                )

                .addEdge("review_handle_analysis", "review_handle_analysis_solution")

                .addConditionalEdges(
                        "review_handle_analysis_solution",
                        edge_async(state -> {
                            int retryCnt = state.retryCnt();
                            if (retryCnt > 2) {
                                log.info("Превышено количество попыток, ответ считается одобренным");
                                return "approved";
                            }
                            String feedbackSolution = state.feedbackSolution().orElse("no");
                            if ("yes".equalsIgnoreCase(feedbackSolution.strip())) return "approved";
                            return "retry";
                        }),
                        isCodeOrTest
                                ? Map.of("approved", "approve_code", "retry", "retry_handle_analysis")
                                : Map.of("approved", "handle_result", "retry", "retry_handle_analysis")
                )

                .addConditionalEdges(
                        "retry_handle_analysis",
                        edge_async(state -> {
                            String error = state.failedMessage();
                            if (error == null || error.isBlank()) return "ok";
                            if (state.retryCnt() > 2) throw new IllegalStateException(
                                    "Task execution failed after maximum retries for task id=" + task.getId() + ". Last error: " + error);
                            return "error";
                        }),
                        Map.of("ok", "review_handle_analysis", "error", "retry_handle_analysis_after_error")
                )

                .addConditionalEdges(
                        "retry_handle_analysis_after_error",
                        edge_async(state -> {
                            String error = state.failedMessage();
                            if (error == null || error.isBlank()) return "ok";
                            if (state.retryCnt() > 2) throw new IllegalStateException(
                                    "Task execution failed after maximum retries for task id=" + task.getId() + ". Last error: " + error);
                            return "error";
                        }),
                        Map.of("ok", "review_handle_analysis", "error", "retry_handle_analysis_after_error")
                )

                // ── Code execution edges ─────────────────────────────────────
                .addEdge("rewrite_disapproved_code", "review_handle_analysis")
                .addEdge("approve_code", END) // unreachable — approve_code always throws
                .addEdge("execute_code", "review_code_execution")
                .addEdge("review_code_execution", "review_code_execution_solution")

                .addConditionalEdges(
                        "review_code_execution_solution",
                        edge_async(state -> {
                            int retryCnt = state.execRetryCnt();
                            if (retryCnt > 2) {
                                log.info("Превышено кол-во попыток переписывания кода, берём текущий результат");
                                return "done";
                            }
                            String feedbackSolution = state.feedbackSolution().orElse("no");
                            if ("yes".equalsIgnoreCase(feedbackSolution.strip())) return "done";
                            return "rewrite";
                        }),
                        Map.of("done", "handle_result", "rewrite", "rewrite_code")
                )

                .addEdge("rewrite_code", "approve_code")

                .addEdge("handle_result", END)

                .compile();

        var state = graph.invoke(Map.of()).get();
        return Map.of("result", state.result().orElse(""));
    }
}
