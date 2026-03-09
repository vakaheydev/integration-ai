package ai.agent.swagger.controller;

import ai.agent.swagger.model.ChatRequest;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.CreateTaskRequest;
import ai.agent.swagger.security.SecurityUtils;
import ai.agent.swagger.service.TaskService;
import ai.agent.swagger.service.TaskServiceAi;
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

    public TaskUserController(TaskService taskService, TaskServiceAi taskServiceAi) {
        this.taskService = taskService;
        this.taskServiceAi = taskServiceAi;
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@Valid @RequestBody CreateTaskRequest request) {
        String userId = SecurityUtils.currentUser().getId();
        Task task = taskService.createTask(null, userId, request);
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
    public ResponseEntity<?> restartTask(@PathVariable("id") String id) {
        return taskService.getTaskById(id)
                .map(task -> {
                    if (!task.getUserId().equals(SecurityUtils.currentUser().getId())) {
                        return ResponseEntity.status(403).body("Access restricted");
                    }
                    Task restarted = taskService.restartTask(id);
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
}

