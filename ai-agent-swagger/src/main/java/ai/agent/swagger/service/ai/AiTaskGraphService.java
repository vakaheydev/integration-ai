package ai.agent.swagger.service.ai;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.service.TaskService;
import ai.agent.swagger.service.ai.handler.TaskGraphHandlerRouter;
import ai.agent.swagger.service.ai.handler.TaskGraphNodeHandler;
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

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            TASK_ANALYSIS, Channels.base(() -> ""),
            FEEDBACK, Channels.base(() -> ""),
            FEEDBACK_SOLUTION, Channels.base(() -> ""),
            RETRY_CNT, Channels.base(() -> 0),
            RESULT, Channels.base(() -> ""),
            FAILED, Channels.base(() -> ""),
            PREVIOUS_REVIEW, Channels.base(() -> "")
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

    public AiTaskGraphService(TaskService taskService,
                              ToolDescriptionProvider toolDescriptionProvider,
                              TaskGraphHandlerRouter handlerRouter,
                              AiChatService aiChatService,
                              ai.agent.swagger.service.PromptBuilderService promptBuilderService) {
        this.taskService = taskService;
        this.toolDescriptionProvider = toolDescriptionProvider;
        this.handlerRouter = handlerRouter;
        this.aiChatService = aiChatService;
        this.promptBuilderService = promptBuilderService;
    }

    public Map<String, Object> runGraphTaskHandle(Task task) throws GraphStateException {
        String availableTools = toolDescriptionProvider.getToolsDescription();
        TaskGraphNodeHandler handler = handlerRouter.getHandler(task.getType());

        var graph = new StateGraph<>(TaskState.SCHEMA, TaskState::new)
                .addNode("review_previous_result", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Reviewing previous result and user feedback");
                    String prompt = promptBuilderService.getReviewPreviousResultPrompt(
                            task.getDescription(), task.getPreviousResult(), task.getUserMessage());
                    String review = aiChatService.chat(prompt);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + review, TaskStatus.COMPLETED);
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
                    String answer = handler.analyze(task, availableTools);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + answer, TaskStatus.COMPLETED);
                    return Map.of(TaskState.TASK_ANALYSIS, answer);
                }))

                .addNode("retry_analyze_task", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry analyzing task");
                    int retryCnt = state.retryCnt() + 1;
                    String feedback = state.feedback().orElse("");
                    String answer = handler.retryAnalyze(task, feedback, availableTools);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + answer, TaskStatus.COMPLETED);
                    return Map.of(TaskState.TASK_ANALYSIS, answer, TaskState.RETRY_CNT, retryCnt);
                }))

                .addNode("review_task_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Review task analysis");
                    String analysis = state.taskAnalysis().orElse("");
                    String prompt = promptBuilderService.getHandleTaskReviewAnalysisPrompt(task.getDescription(), analysis);
                    String feedback = aiChatService.chat(prompt);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + feedback, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_task_analysis_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Review task analysis solution");
                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getHandleTaskReviewSolutionPrompt(feedback);
                    String solution = aiChatService.chat(prompt);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + solution, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Execute task according to analysis");
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String result = handler.execute(task, analysis);
                        taskService.changeStage(task.getId(), stageId, "AI response: " + result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, 0, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during task execution, will trigger retry", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Error: " + errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("handle_analysis_after_error", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execution after error");
                    int retryCnt = state.retryCnt() + 1;
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String errorMessage = state.failedMessage();
                        String result = handler.executeAfterError(task, analysis, errorMessage);
                        taskService.changeStage(task.getId(), stageId, "AI response: " + result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during handle_analysis_after_error", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Error: " + errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("retry_handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execute task according to analysis");
                    int retryCnt = state.retryCnt() + 1;
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String feedback = state.feedback().orElse("");
                        String result = handler.retryExecute(task, analysis, feedback);
                        taskService.changeStage(task.getId(), stageId, "AI response: " + result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during retry_handle_analysis", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Error: " + errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("retry_handle_analysis_after_error", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execution after error (from review)");
                    int retryCnt = state.retryCnt() + 1;
                    try {
                        String analysis = state.taskAnalysis().orElse("");
                        String errorMessage = state.failedMessage();
                        String result = handler.executeAfterError(task, analysis, errorMessage);
                        taskService.changeStage(task.getId(), stageId, "AI response: " + result, TaskStatus.COMPLETED);
                        return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, "");
                    } catch (Exception e) {
                        log.error("Error during retry_handle_analysis_after_error", e);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        taskService.changeStage(task.getId(), stageId, "Error: " + errorMsg, TaskStatus.FAILED);
                        return Map.of(TaskState.RESULT, "", TaskState.RETRY_CNT, retryCnt, TaskState.FAILED, errorMsg);
                    }
                }))

                .addNode("review_handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Reviewing execution of task according to analysis");
                    String result = state.result().orElse("");
                    String expectedOutputRules = promptBuilderService.getExpectedOutputRules(task.getType());
                    String prompt = promptBuilderService.getReviewHandleAnalysisPrompt(task.getDescription(), result, expectedOutputRules);
                    String feedback = aiChatService.chat(prompt);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + feedback, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_handle_analysis_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Making solution for review of execution of task according to analysis");
                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getReviewHandleAnalysisSolutionPrompt(feedback);
                    String solution = aiChatService.chat(prompt);
                    taskService.changeStage(task.getId(), stageId, "AI response: " + solution, TaskStatus.COMPLETED);
                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("handle_result", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Handling final result");
                    String result = state.result().orElse("");
                    taskService.changeStage(task.getId(), stageId, "Final result: " + result, TaskStatus.COMPLETED);
                    return Map.of(TaskState.RESULT, result);
                }))

                .addConditionalEdges(
                        START,
                        edge_async(state -> {
                            boolean isRestarted = (task.getPreviousResult() != null && !task.getPreviousResult().isBlank())
                                    || (task.getUserMessage() != null && !task.getUserMessage().isBlank());
                            return isRestarted ? "review_previous" : "analyze";
                        }),
                        Map.of("review_previous", "review_previous_result", "analyze", "analyze_task")
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
                            if ("yes".equalsIgnoreCase(feedbackSolution)) return "approved";
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
                            if ("yes".equalsIgnoreCase(feedbackSolution)) return "approved";
                            return "retry";
                        }),
                        Map.of("approved", "handle_result", "retry", "retry_handle_analysis")
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

                .addEdge("handle_result", END)

                .compile();

        var state = graph.invoke(Map.of()).get();
        return Map.of("result", state.result().orElse(""));
    }
}
