package ai.agent.swagger.controller;

import ai.agent.swagger.config.AiModelProperties;
import ai.agent.swagger.model.ChatRequest;
import ai.agent.swagger.model.CreateTaskRequest;
import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.security.SecurityUtils;
import ai.agent.swagger.service.TaskService;
import ai.agent.swagger.service.ai.AiSwaggerGraphService;
import ai.agent.swagger.service.SwaggerService;
import jakarta.validation.Valid;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/me/swagger")
public class UserSwaggerController {
    private final SwaggerService swaggerService;
    private final AiSwaggerGraphService aiSwaggerGraphService;
    private final TaskService taskService;
    private final AiModelProperties aiModelProperties;

    public UserSwaggerController(SwaggerService swaggerService, AiSwaggerGraphService aiSwaggerGraphService,
                                 TaskService taskService, AiModelProperties aiModelProperties) {
        this.swaggerService = swaggerService;
        this.aiSwaggerGraphService = aiSwaggerGraphService;
        this.taskService = taskService;
        this.aiModelProperties = aiModelProperties;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(@RequestPart("file") MultipartFile file,
                                                          @Valid @RequestPart("name") String name) throws IOException {
        String userId = SecurityUtils.currentUser().getId();
        String swaggerContent = new String(file.getBytes());
        Map<String, String> result = swaggerService.uploadSwagger(swaggerContent, userId, name);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/upload-url")
    public ResponseEntity<?> uploadFromUrl(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        String name = request.get("name");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        try {
            byte[] bytes = new java.net.URI(url).toURL().openConnection().getInputStream().readAllBytes();
            String swaggerContent = new String(bytes);
            if (swaggerContent.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Downloaded file is empty"));
            }
            String userId = SecurityUtils.currentUser().getId();
            Map<String, String> result = swaggerService.uploadSwagger(swaggerContent, userId, name);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to download file: " + e.getMessage()));
        }
    }

    @PostMapping("/{documentId}/chat")
    public ResponseEntity<Map<String, String>> documentChat(@PathVariable("documentId") String documentId,
                                                            @Valid @RequestBody ChatRequest chatRequest) {
        try {
            String modelName;
            try {
                modelName = aiModelProperties.resolveModel(chatRequest.getModelName());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", e.getMessage()));
            }
            String userId = SecurityUtils.currentUser().getId();
            String role = chatRequest.getRole() != null ? chatRequest.getRole() : "analytic";
            String response = swaggerService.chatByDocumentId(documentId, userId, chatRequest.getQuery(), role, modelName);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "404", "error", "There is no document with ID" + documentId));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> query(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("status", "400", "error", "Query is required"));
            }
            SwaggerSearchResult result = swaggerService.search(query, SecurityUtils.currentUser().getId());
            if (result.isPresent() && result.getDocument().getUserId() != null
                    && !result.getDocument().getUserId().equals(SecurityUtils.currentUser().getId())) {
                return ResponseEntity.status(403).body(Map.of("status", "403", "error", "Access denied"));
            }
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<SwaggerDocument> getDocument(@PathVariable("documentId") String documentId) {
        Optional<SwaggerDocument> swagger = swaggerService.getSwaggerById(documentId);

        if (swagger.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (swagger.get().getUserId() != null && !swagger.get().getUserId().equals(SecurityUtils.currentUser().getId())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(swagger.get());
    }

    @GetMapping()
    public ResponseEntity<List<SwaggerDocument>> getDocumentByUserId() {
        String userId = SecurityUtils.currentUser().getId();
        List<SwaggerDocument> swaggers = swaggerService.getSwaggersByUserId(userId);
        return ResponseEntity.ok(swaggers);
    }

    @PostMapping("/{documentId}/task")
    public ResponseEntity<?> createTask(@PathVariable("documentId") String documentId,
                                        @Valid @RequestBody CreateTaskRequest request) {
        try {
            request.setModelName(aiModelProperties.resolveModel(request.getModelName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        String userId = SecurityUtils.currentUser().getId();
        Task task = taskService.createTask(documentId, userId, request);
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<?> deleteAllDocuments() {
        String userId = SecurityUtils.currentUser().getId();
        swaggerService.deleteAllByUserId(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable("documentId") String documentId) {
        Optional<SwaggerDocument> swagger = swaggerService.getSwaggerById(documentId);

        if (swagger.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (swagger.get().getUserId() != null && !swagger.get().getUserId().equals(SecurityUtils.currentUser().getId())) {
            return ResponseEntity.status(403).build();
        }

        swaggerService.deleteDocumentById(documentId);
        return ResponseEntity.noContent().build();
    }
}

