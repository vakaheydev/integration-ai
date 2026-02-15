package ai.agent.swagger.controller;

import ai.agent.swagger.service.AiChatService;
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
        String response = aiChatService.chat(message);
        return ResponseEntity.ok(response);
    }
}
