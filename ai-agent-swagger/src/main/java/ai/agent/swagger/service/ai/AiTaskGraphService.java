package ai.agent.swagger.service.ai;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.PromptBuilderService;
import ai.agent.swagger.service.SwaggerServiceDocument;
import ai.agent.swagger.service.TaskService;
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

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            TASK_ANALYSIS, Channels.base(() -> ""),
            FEEDBACK, Channels.base(() -> ""),
            FEEDBACK_SOLUTION, Channels.base(() -> ""),
            RETRY_CNT, Channels.base(() -> 0),
            RESULT, Channels.base(() -> "")
    );

    public TaskState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> taskAnalysis() {
        return value(TASK_ANALYSIS);
    }

    public Optional<String> feedback() {
        return value(FEEDBACK);
    }

    public Optional<String> feedbackSolution() {
        return value(FEEDBACK_SOLUTION);
    }

    public int retryCnt() {
        return (int) value(RETRY_CNT).orElse(0);
    }

    public Optional<String> result() {
        return value(RESULT);
    }
}

@Slf4j
@Service
public class AiTaskGraphService {
    private final AiChatService aiChatService;
    private final PromptBuilderService promptBuilderService;
    private final TaskService taskService;
    private final ToolDescriptionProvider toolDescriptionProvider;
    private final SwaggerServiceDocument swaggerServiceDocument;

    public AiTaskGraphService(AiChatService aiChatService, PromptBuilderService promptBuilderService,
                              TaskService taskService, ToolDescriptionProvider toolDescriptionProvider, SwaggerServiceDocument swaggerServiceDocument) {
        this.aiChatService = aiChatService;
        this.promptBuilderService = promptBuilderService;
        this.taskService = taskService;
        this.toolDescriptionProvider = toolDescriptionProvider;
        this.swaggerServiceDocument = swaggerServiceDocument;
    }

    public Map<String, Object> runGraphTaskHandle(Task task) throws GraphStateException {
        String availableTools = toolDescriptionProvider.getToolsDescription();
        boolean hasDocument = task.getDocumentId() != null && !task.getDocumentId().isBlank();

        var graph = new StateGraph<>(SwaggerChatState.SCHEMA, TaskState::new)
                .addNode("analyze_task", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Analyzing task");
                    String prompt;
                    if (hasDocument) {
                        SwaggerDocument document = swaggerServiceDocument.getSwaggerById(task.getDocumentId())
                                .orElseThrow(() -> new IllegalStateException("Document specified in task not found: " + task.getDocumentId()));
                        prompt = promptBuilderService.getHandleTaskAnalyzePrompt(task.getDescription(), task.getDocumentId(), document.getDocumentSummary(), document.getMethodSummary(), availableTools);
                    } else {
                        prompt = promptBuilderService.getHandleTaskAnalyzeGeneralPrompt(task.getDescription(), availableTools);
                    }
                    String answer = aiChatService.chat(prompt);
                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + answer);
                    return Map.of(TaskState.TASK_ANALYSIS, answer);
                }))

                .addNode("retry_analyze_task", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry analyzing task");
                    int retryCnt = state.retryCnt() + 1;
                    String feedback = state.feedback().orElse("");
                    String prompt;
                    if (hasDocument) {
                        SwaggerDocument document = swaggerServiceDocument.getSwaggerById(task.getDocumentId())
                                .orElseThrow(() -> new IllegalStateException("Document specified in task not found: " + task.getDocumentId()));
                        prompt = promptBuilderService.getHandleTaskRetryPrompt(task.getDescription(), task.getDocumentId(), feedback, document.getDocumentSummary(), document.getMethodSummary(), availableTools);
                    } else {
                        prompt = promptBuilderService.getHandleTaskRetryGeneralPrompt(task.getDescription(), feedback, availableTools);
                    }
                    String answer = aiChatService.chat(prompt);
                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + answer);
                    return Map.of(TaskState.TASK_ANALYSIS, answer, TaskState.RETRY_CNT, retryCnt);
                }))

                .addNode("review_task_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Review task analysis");

                    String analysis = state.taskAnalysis().orElse("");
                    String prompt = promptBuilderService.getHandleTaskReviewAnalysisPrompt(task.getDescription(), analysis);
                    String feedback = aiChatService.chat(prompt);
                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + feedback);

                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_task_analysis_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Review task analysis solution");

                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getHandleTaskReviewSolutionPrompt(feedback);
                    String solution = aiChatService.chat(prompt);
                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + solution);

                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Execute task according to analysis");

                    String analysis = state.taskAnalysis().orElse("");
                    String prompt;
                    if (hasDocument) {
                        prompt = promptBuilderService.getHandleTaskExecutePrompt(task.getDescription(), task.getDocumentId(), analysis);
                    } else {
                        prompt = promptBuilderService.getHandleTaskExecuteGeneralPrompt(task.getDescription(), analysis);
                    }
                    String result = aiChatService.chatWithSwaggerTools(task.getUserId(), prompt);

                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + result);

                    return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, 0);
                }))

                .addNode("retry_handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Retry execute task according to analysis");

                    int retryCnt = state.retryCnt() + 1;
                    String analysis = state.taskAnalysis().orElse("");
                    String feedback = state.feedback().orElse("");
                    String prompt;
                    if (hasDocument) {
                        prompt = promptBuilderService.getRetryHandleTaskAnalysisPrompt(task.getDescription(), task.getDocumentId(), analysis, feedback);
                    } else {
                        prompt = promptBuilderService.getRetryHandleTaskAnalysisGeneralPrompt(task.getDescription(), analysis, feedback);
                    }
                    String result = aiChatService.chatWithSwaggerTools(task.getUserId(), prompt);

                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + result);

                    return Map.of(TaskState.RESULT, result, TaskState.RETRY_CNT, retryCnt);
                }))

                .addNode("review_handle_analysis", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Reviewing execution of task according to analysis");

                    String result = state.result().orElse("");
                    String prompt = promptBuilderService.getReviewHandleAnalysisPrompt(task.getDescription(), result);
                    String feedback = aiChatService.chat(prompt);

                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + feedback);

                    return Map.of(TaskState.FEEDBACK, feedback);
                }))

                .addNode("review_handle_analysis_solution", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Making solution for review of execution of task according to analysis");

                    String feedback = state.feedback().orElse("");
                    String prompt = promptBuilderService.getReviewHandleAnalysisSolutionPrompt(feedback);
                    String solution = aiChatService.chat(prompt);

                    taskService.changeStageDescription(task.getId(), stageId, "AI response: " + solution);

                    return Map.of(TaskState.FEEDBACK_SOLUTION, solution);
                }))

                .addNode("handle_result", node_async(state -> {
                    int stageId = taskService.changeCurrentStage(task.getId(), "Handling final result");

                    String result = state.result().orElse("");

                    taskService.changeStageDescription(task.getId(), stageId, "Final result: " + result);

                    return Map.of(TaskState.RESULT, result);
                }))

                .addEdge(START, "analyze_task")
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
                            if ("yes".equalsIgnoreCase(feedbackSolution)) {
                                return "approved";
                            }
                            return "retry";
                        }),
                        Map.of(
                                "approved", "handle_analysis",
                                "retry", "retry_analyze_task"
                        )
                )

                .addEdge("handle_analysis", "review_handle_analysis")
                .addEdge("review_handle_analysis", "review_handle_analysis_solution")

                .addEdge("retry_handle_analysis", "review_handle_analysis")

                .addConditionalEdges(
                        "review_handle_analysis_solution",
                        edge_async(state -> {
                            int retryCnt = state.retryCnt();
                            if (retryCnt > 2) {
                                log.info("Превышено количество попыток, ответ считается одобренным");
                                return "approved";
                            }
                            String feedbackSolution = state.feedbackSolution().orElse("no");
                            if ("yes".equalsIgnoreCase(feedbackSolution)) {
                                return "approved";
                            }
                            return "retry";
                        }),
                        Map.of(
                                "approved", "handle_result",
                                "retry", "retry_handle_analysis"
                        )
                )

                .addEdge("handle_result", END)

                .compile();

        var state = graph.invoke(Map.of()).get();
        return Map.of(
                "result", state.result().orElse("")
        );
    }
}