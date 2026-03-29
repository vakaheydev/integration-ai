package ai.agent.swagger.service.ai;

import ai.agent.swagger.config.AiModelProperties;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.ChatAssistant;
import ai.agent.swagger.service.StatelessChatAssistant;
import ai.agent.swagger.service.SwaggerServiceAi;
import ai.agent.swagger.service.SwaggerServiceDocument;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiChatService {
    private final String apiKey;
    private final AiModelProperties modelProperties;
    private final SwaggerServiceDocument swaggerServiceDocument;
    private final SwaggerServiceAi swaggerServiceAi;
    private final ApiCallTools apiCallTools;
    private final Map<String, ChatMemory> memoryStore = new ConcurrentHashMap<>();

    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, StatelessChatAssistant> statelessCache = new ConcurrentHashMap<>();
    private final Map<String, ChatAssistant> chatAssistantCache = new ConcurrentHashMap<>();
    private final Map<String, ChatAssistant> swaggerToolsCache = new ConcurrentHashMap<>();
    private final Map<String, ChatAssistant> taskAnalysisCache = new ConcurrentHashMap<>();

    public AiChatService(
            @Value("${secrets.polza.api-key}") String apiKey,
            AiModelProperties modelProperties,
            SwaggerServiceDocument swaggerServiceDocument,
            @Lazy SwaggerServiceAi swaggerServiceAi,
            ApiCallTools apiCallTools
    ) {
        this.apiKey = apiKey;
        this.modelProperties = modelProperties;
        this.swaggerServiceDocument = swaggerServiceDocument;
        this.swaggerServiceAi = swaggerServiceAi;
        this.apiCallTools = apiCallTools;
    }

    @PostConstruct
    private void warmUpDefault() {
        getOrCreateChatModel(modelProperties.getDefaultModel());
    }

    // ── chat с памятью (userId) ──────────────────────────────────────────────

    /** Chat с памятью, дефолтная модель */
    public String chat(String userId, String prompt) {
        return chat(userId, prompt, null);
    }

    /** Chat с памятью, указанная модель */
    public String chat(String userId, String prompt, String modelName) {
        String model = resolveModel(modelName);
        ChatAssistant assistant = chatAssistantCache.computeIfAbsent(model, this::buildChatAssistant);
        return assistant.chat(userId, prompt);
    }

    // ── stateless chat ───────────────────────────────────────────────────────

    /** Stateless chat, дефолтная модель */
    public String chat(String prompt) {
        return chatStateless(prompt, null);
    }

    /** Stateless chat, указанная модель */
    public String chatStateless(String prompt, String modelName) {
        String model = resolveModel(modelName);
        StatelessChatAssistant assistant = statelessCache.computeIfAbsent(model, this::buildStatelessAssistant);
        return assistant.chat(prompt);
    }

    // ── chat со swagger tools ────────────────────────────────────────────────

    /** Chat со swagger tools, дефолтная модель */
    public String chatWithSwaggerTools(String userId, String prompt) {
        return chatWithSwaggerTools(userId, prompt, null);
    }

    /** Chat со swagger tools, указанная модель */
    public String chatWithSwaggerTools(String userId, String prompt, String modelName) {
        String model = resolveModel(modelName);
        ChatAssistant assistant = swaggerToolsCache.computeIfAbsent(model, this::buildSwaggerToolsAssistant);
        SwaggerToolsContext.set(userId);
        try {
            return assistant.chat(userId, prompt);
        } finally {
            SwaggerToolsContext.clear();
        }
    }

    // ── chat для анализа задач (swagger tools + api call tools) ──────────────

    /**
     * Chat для ноды анализа задачи. Использует оба набора инструментов.
     * Управляет SwaggerToolsContext и ApiCallToolsContext — вызывающей стороне не нужно.
     */
    public String chatForTaskAnalysis(Task task, String prompt, String modelName) {
        String model = resolveModel(modelName);
        ChatAssistant assistant = taskAnalysisCache.computeIfAbsent(model, this::buildTaskAnalysisAssistant);
        SwaggerToolsContext.set(task.getUserId());
        ApiCallToolsContext.set(task, task.getApprovedApiCalls());
        try {
            return assistant.chat(task.getUserId(), prompt);
        } finally {
            SwaggerToolsContext.clear();
            ApiCallToolsContext.clear();
        }
    }

    // ── model resolution ─────────────────────────────────────────────────────

    private String resolveModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return modelProperties.getDefaultModel();
        }
        return modelName;
    }

    // ── factory methods ──────────────────────────────────────────────────────

    ChatModel getOrCreateChatModel(String modelId) {
        return chatModelCache.computeIfAbsent(modelId, id ->
                OpenAiChatModel.builder()
                        .baseUrl("https://api.polza.ai/api/v1")
                        .apiKey(apiKey)
                        .modelName(id)
                        .build()
        );
    }

    private ChatAssistant buildChatAssistant(String modelId) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(getOrCreateChatModel(modelId))
                .chatMemoryProvider(userId -> memoryStore.computeIfAbsent(
                        userId.toString(),
                        id -> MessageWindowChatMemory.withMaxMessages(10)
                ))
                .build();
    }

    private StatelessChatAssistant buildStatelessAssistant(String modelId) {
        return AiServices.builder(StatelessChatAssistant.class)
                .chatModel(getOrCreateChatModel(modelId))
                .build();
    }

    private ChatAssistant buildSwaggerToolsAssistant(String modelId) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(getOrCreateChatModel(modelId))
                .chatMemoryProvider(userId -> memoryStore.computeIfAbsent(
                        userId.toString() + ":swagger",
                        id -> MessageWindowChatMemory.withMaxMessages(10)
                ))
                .tools(new SwaggerTools(swaggerServiceDocument, swaggerServiceAi))
                .build();
    }

    private ChatAssistant buildTaskAnalysisAssistant(String modelId) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(getOrCreateChatModel(modelId))
                .chatMemoryProvider(userId -> memoryStore.computeIfAbsent(
                        userId.toString() + ":analysis",
                        id -> MessageWindowChatMemory.withMaxMessages(10)
                ))
                .tools(new SwaggerTools(swaggerServiceDocument, swaggerServiceAi), apiCallTools)
                .build();
    }
}
