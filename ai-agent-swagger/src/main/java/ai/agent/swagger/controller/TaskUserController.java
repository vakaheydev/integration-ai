package ai.agent.swagger.controller;

import ai.agent.swagger.model.ChatRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me/tasks")
public class TaskUserController {

    private final TaskService taskService;
    private final TaskServiceAi taskServiceAi;
    private final PythonCodeExecutorService pythonCodeExecutorService;

    public TaskUserController(TaskService taskService, TaskServiceAi taskServiceAi,
                              PythonCodeExecutorService pythonCodeExecutorService) {
        this.taskService = taskService;
        this.taskServiceAi = taskServiceAi;
        this.pythonCodeExecutorService = pythonCodeExecutorService;
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@Valid @RequestBody CreateTaskRequest request) {
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

