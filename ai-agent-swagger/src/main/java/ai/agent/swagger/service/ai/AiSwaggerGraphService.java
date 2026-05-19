package ai.agent.swagger.service.ai;

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

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class SwaggerChatState extends AgentState {

    public static final String QUESTION = "question";
    public static final String ANSWER = "answer";
    public static final String RESULT = "result";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            QUESTION, Channels.base(() -> ""),
            ANSWER, Channels.base(() -> ""),
            RESULT, Channels.base(() -> "")
    );

    public SwaggerChatState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> question() {
        return value(QUESTION);
    }

    public Optional<String> answer() {
        return value(ANSWER);
    }

    public Optional<String> result() {
        return value(RESULT);
    }
}

@Slf4j
@Service
public class AiSwaggerGraphService {
    private final AiChatService aiChatService;

    public AiSwaggerGraphService(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public Map<String, Object> runGraphDocumentChat(String userId, String request, String documentId) throws GraphStateException {
        return runGraphDocumentChat(userId, request, documentId, null);
    }

    public Map<String, Object> runGraphDocumentChat(String userId, String request, String documentId, String modelName) throws GraphStateException {
        var graph = new StateGraph<>(SwaggerChatState.SCHEMA, SwaggerChatState::new)
                .addNode("ask_llm", node_async(state -> {
                    String answer = aiChatService.chatWithSwaggerTools(userId, request + "\nDocument ID: " + documentId, modelName);
                    log.info("Получен ответ от ИИ на вопрос");
                    return Map.of(SwaggerChatState.ANSWER, answer);
                }))

                .addNode("handle_result", node_async(state -> {
                    String result = state.answer().orElse("");
                    return Map.of(SwaggerChatState.RESULT, result);
                }))

                .addEdge(START, "ask_llm")
                .addEdge("ask_llm", "handle_result")
                .addEdge("handle_result", END)

                .compile();

        var state = graph.invoke(Map.of()).get();
        return Map.of(
                "result", state.result().orElse(""),
                "question", state.question().orElse("")
        );
    }
}
