package ai.agent.swagger.controller;

import ai.agent.swagger.config.AiModelProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final AiModelProperties aiModelProperties;

    public ModelController(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    @GetMapping
    public ResponseEntity<?> getAvailableModels() {
        return ResponseEntity.ok(Map.of(
                "defaultModel", aiModelProperties.getDefaultModel(),
                "availableModels", aiModelProperties.getAvailableModels()
        ));
    }
}
