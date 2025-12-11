package ai.agent.swagger.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private final ChatAssistant chatAssistant;

    public AiChatService(
            @Value("${secrets.polza.api-key}") String modelApikey,
            @Value("${ai.swagger.model}") String modelName
    ) {
        ChatModel model = getChatModel(modelApikey, modelName);

        this.chatAssistant = AiServices.builder(ChatAssistant.class)
                .chatModel(model)
//                    .contentRetriever(contentRetriever)
                .build();
    }

    public String chat(String prompt) {
        return chatAssistant.chat(prompt);
    }

    public ChatModel getChatModel(String modelApikey, String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl("https://api.polza.ai/api/v1")
                .apiKey(modelApikey)
                .modelName(modelName)
                .build();
    }
}
