package ai.agent.swagger.controller;

import ai.agent.swagger.config.AiModelProperties;
import ai.agent.swagger.model.ChatRequest;
import ai.agent.swagger.model.ResolveInputRequest;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.CreateTaskRequest;
import ai.agent.swagger.model.RestartTaskRequest;
import ai.agent.swagger.security.SecurityUtils;
import ai.agent.swagger.service.TaskService;
import ai.agent.swagger.service.TaskServiceAi;
import ai.agent.swagger.service.executor.CodeExecutionResult;
import ai.agent.swagger.service.executor.CodeExtractor;
import ai.agent.swagger.service.executor.PythonCodeExecutorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me/tasks")
public class TaskUserController {

    private final TaskService taskService;
    private final TaskServiceAi taskServiceAi;
    private final PythonCodeExecutorService pythonCodeExecutorService;
    private final AiModelProperties aiModelProperties;

    public TaskUserController(TaskService taskService, TaskServiceAi taskServiceAi,
                              PythonCodeExecutorService pythonCodeExecutorService,
                              AiModelProperties aiModelProperties) {
        this.taskService = taskService;
        this.taskServiceAi = taskServiceAi;
        this.pythonCodeExecutorService = pythonCodeExecutorService;
        this.aiModelProperties = aiModelProperties;
    }

    @PostMapping
    public ResponseEntity<?> createTask(@Valid @RequestBody CreateTaskRequest request) {
        // Валидируем и резолвим модель (null → дефолтная)
        try {
            request.setModelName(aiModelProperties.resolveModel(request.getModelName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        String userId = SecurityUtils.currentUser().getId();
        Task task = taskService.createTask(null, userId, request);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/fromBase/{id}")
    public ResponseEntity<?> createTaskFromBase(@PathVariable("id") String id,
                                                @RequestBody(required = false) RestartTaskRequest request) {
        String userId = SecurityUtils.currentUser().getId();
        String userMessage = request != null ? request.getUserMessage() : null;
        Task task = taskService.createTaskFromBase(id, userId, userMessage);
        return ResponseEntity.ok(task);
    }

    @GetMapping
    public ResponseEntity<List<Task>> getMyTasks() {
        String userId = SecurityUtils.currentUser().getId();
        return ResponseEntity.ok(taskService.getTasksByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTaskById(@PathVariable("id") String id) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    return ResponseEntity.ok(task);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/subtasks")
    public ResponseEntity<?> getSubtasks(@PathVariable("id") String id) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    return ResponseEntity.ok(taskService.getSubtasks(id));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<?> restartTask(@PathVariable("id") String id,
                                         @RequestBody(required = false) RestartTaskRequest request) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    String userMessage = request != null ? request.getUserMessage() : null;
                    Task restarted = taskService.restartTask(id, task.getResult(), userMessage);
                    return ResponseEntity.ok(restarted);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve_input")
    public ResponseEntity<?> resolveInput(@PathVariable("id") String id,
                                          @Valid @RequestBody ResolveInputRequest request) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    try {
                        Task resolved = taskService.resolveUserInput(id, request.getMessage());
                        return ResponseEntity.ok(resolved);
                    } catch (IllegalStateException e) {
                        return ResponseEntity.badRequest().body(e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveTask(@PathVariable("id") String id,
                                          @RequestParam("status") boolean status,
                                          @RequestBody(required = false) Map<String, String> body) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    String message = body != null ? body.get("message") : null;
                    if (!status && (message == null || message.isBlank())) {
                        return ResponseEntity.badRequest().body("Message is required when disapproving");
                    }
                    try {
                        Task result = taskService.approveTask(id, status, message);
                        return ResponseEntity.ok(result);
                    } catch (IllegalStateException e) {
                        return ResponseEntity.badRequest().body(e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<?> chat(@PathVariable("id") String id,
                                  @Valid @RequestBody ChatRequest request) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    String userId = SecurityUtils.currentUser().getId();
                    String response = taskServiceAi.chat(id, userId, request.getQuery());
                    return ResponseEntity.ok(Map.of("response", response));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/executeCode")
    public ResponseEntity<?> executeCode(@PathVariable("id") String id) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }

                    String result = task.getResult();
                    if (result == null || result.isBlank()) {
                        return ResponseEntity.badRequest().body("Task has no result to execute");
                    }

                    String code = CodeExtractor.extract(result)
                            .orElse(null);
                    if (code == null) {
                        return ResponseEntity.badRequest().body("No code block found in task result. Expected <<<CODE_START>>>...<<<CODE_END>>>");
                    }

                    CodeExecutionResult execResult = pythonCodeExecutorService.execute(code);
                    return ResponseEntity.ok(Map.of(
                            "success", execResult.isSuccess(),
                            "exitCode", execResult.getExitCode(),
                            "stdout", execResult.getStdout(),
                            "stderr", execResult.getStderr(),
                            "timedOut", execResult.isTimedOut()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTask(@PathVariable("id") String id) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    taskService.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/deleteAll")
    public ResponseEntity<?> deleteAllTasks() {
        String userId = SecurityUtils.currentUser().getId();
        taskService.deleteAllByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}

