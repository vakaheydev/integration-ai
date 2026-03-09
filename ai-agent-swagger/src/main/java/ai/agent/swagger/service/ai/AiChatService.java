package ai.agent.swagger.service.ai;

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
    private final ChatAssistant chatAssistant;
    private final StatelessChatAssistant statelessChatAssistant;
    private final SwaggerServiceDocument swaggerServiceDocument;
    private final SwaggerServiceAi swaggerServiceAi;
    private final ChatModel model;
    private final Map<String, ChatMemory> memoryStore = new ConcurrentHashMap<>();

    private ChatAssistant swaggerToolsChatAssistant;

    public AiChatService(
            @Value("${secrets.polza.api-key}") String modelApikey,
            @Value("${ai.swagger.model}") String modelName,
            SwaggerServiceDocument swaggerServiceDocument,
            @Lazy SwaggerServiceAi swaggerServiceAi
    ) {
        this.swaggerServiceDocument = swaggerServiceDocument;
        this.swaggerServiceAi = swaggerServiceAi;
        this.model = getChatModel(modelApikey, modelName);

        this.chatAssistant = AiServices.builder(ChatAssistant.class)
                .chatModel(model)
                .chatMemoryProvider(userId -> memoryStore.computeIfAbsent(
                        userId.toString(),
                        id -> MessageWindowChatMemory.withMaxMessages(10)
                ))
                .build();

        this.statelessChatAssistant = AiServices.builder(StatelessChatAssistant.class)
                .chatModel(model)
                .build();
    }

    @PostConstruct
    private void initSwaggerToolsAssistant() {
        this.swaggerToolsChatAssistant = AiServices.builder(ChatAssistant.class)
                .chatModel(model)
                .chatMemoryProvider(userId -> memoryStore.computeIfAbsent(
                        userId.toString(),
                        id -> MessageWindowChatMemory.withMaxMessages(10)
                ))
                .tools(new SwaggerTools(swaggerServiceDocument, swaggerServiceAi))
                .build();
    }

    public String chat(String userId, String prompt) {
        return chatAssistant.chat(userId, prompt);
    }

    public String chat(String prompt) {
        return statelessChatAssistant.chat(prompt);
    }

    public String chatWithSwaggerTools(String userId, String prompt) {
        SwaggerToolsContext.set(userId);
        try {
            return swaggerToolsChatAssistant.chat(userId, prompt);
        } finally {
            SwaggerToolsContext.clear();
        }
    }

    public ChatModel getChatModel(String modelApikey, String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.polza.ai/api/v1")
                .apiKey(modelApikey)
                .modelName(modelName)
                .build();
    }
}


