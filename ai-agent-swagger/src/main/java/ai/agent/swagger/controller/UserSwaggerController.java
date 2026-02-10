package ai.agent.swagger.controller;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.security.SecurityUtils;
import ai.agent.swagger.service.SwaggerService;
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

    public UserSwaggerController(SwaggerService swaggerService) {
        this.swaggerService = swaggerService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(@RequestPart("file") MultipartFile file) throws IOException {
        String userId = SecurityUtils.currentUser().getId();
        String swaggerContent = new String(file.getBytes());
        Map<String, String> result = swaggerService.uploadSwagger(swaggerContent, userId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{documentId}/chat")
    public ResponseEntity<Map<String, String>> documentChat(@PathVariable("documentId") String documentId,
                                                            @RequestParam("query") String query,
                                                            @RequestParam("role") String role) {
        try {
            String userId = SecurityUtils.currentUser().getId();
            String response = swaggerService.chatByDocumentId(documentId, userId, query, role);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "404", "error", "There is no document with ID" + documentId));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<SwaggerSearchResult> query(@RequestParam("query") String query) {
        try {
            SwaggerSearchResult result = swaggerService.search(query);
            if (result.isPresent() && result.getDocument().getUserId() != null
                    && !result.getDocument().getUserId().equals(SecurityUtils.currentUser().getId())) {
                return ResponseEntity.status(403).build();
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

    @GetMapping("/documents")
    public ResponseEntity<List<SwaggerDocument>> getDocumentByUserId() {
        String userId = SecurityUtils.currentUser().getId();
        List<SwaggerDocument> swaggers = swaggerService.getSwaggersByUserId(userId);
        return ResponseEntity.ok(swaggers);
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

