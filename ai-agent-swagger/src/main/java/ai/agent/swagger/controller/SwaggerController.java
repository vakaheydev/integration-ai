package ai.agent.swagger.controller;

import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
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
@RequestMapping("/swagger")
public class SwaggerController {
    private final SwaggerService swaggerService;

    public SwaggerController(SwaggerService swaggerService) {
        this.swaggerService = swaggerService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(@RequestPart("file") MultipartFile file,
                                                          @RequestParam("userId") String userId) throws IOException {
        String swaggerContent = new String(file.getBytes());
        Map<String, String> result = swaggerService.uploadSwagger(swaggerContent, userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<SwaggerDocument> getDocument(@PathVariable("documentId") String documentId) {
        Optional<SwaggerDocument> swagger = swaggerService.getSwaggerById(documentId);

        if (swagger.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(swagger.get());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SwaggerDocument>> getDocumentByUserId(@PathVariable("userId") String userId) {
        List<SwaggerDocument> swaggers = swaggerService.getSwaggersByUserId(userId);
        if (swaggers.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(swaggers);
    }

    @PostMapping("/{documentId}/chat")
    public ResponseEntity<Map<String, String>> documentChat(@PathVariable("documentId") String documentId,
                                                            @RequestParam("userId") String userId,
                                                            @RequestParam("query") String query) {
        try {
            String response = swaggerService.chatByDocumentId(documentId, userId, query);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("status", "404", "error", "There is no document with ID" + documentId));
        }
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable("documentId") String documentId) {
        swaggerService.deleteDocumentById(documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    public ResponseEntity<SwaggerSearchResult> query(@RequestParam("query") String query) {
        try {
            SwaggerSearchResult result = swaggerService.search(query);
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
