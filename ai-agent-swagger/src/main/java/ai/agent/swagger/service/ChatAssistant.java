package ai.agent.swagger.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ChatAssistant {
    @UserMessage("{{prompt}}")
    String chat(@MemoryId String userId, @V("prompt") String prompt);
}
