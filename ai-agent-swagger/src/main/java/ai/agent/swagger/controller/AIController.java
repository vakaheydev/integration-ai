package ai.agent.swagger.controller;

import ai.agent.swagger.security.SecurityUtils;
import ai.agent.swagger.service.ai.AiChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AIController {
    private final AiChatService aiChatService;

    public AIController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestParam("message") String message) {
        String userId = SecurityUtils.currentUser().getId();
        String response = aiChatService.chat(message, userId);
        return ResponseEntity.ok(response);
    }
}
