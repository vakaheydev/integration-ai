package ai.agent.swagger.service;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StatelessChatAssistant {
    @UserMessage("{{prompt}}")
    String chat(@V("prompt") String prompt);
}

