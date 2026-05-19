package ai.agent.swagger.scheduler;

import ai.agent.swagger.model.Task;
import ai.agent.swagger.model.TaskStatus;
import ai.agent.swagger.repository.TaskRepository;
import ai.agent.swagger.service.TaskProcessorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class DocumentTaskScheduler {

    private final TaskRepository taskRepository;
    private final TaskProcessorService taskProcessorService;

    public DocumentTaskScheduler(TaskRepository taskRepository, TaskProcessorService taskProcessorService) {
        this.taskRepository = taskRepository;
        this.taskProcessorService = taskProcessorService;
    }

    @Scheduled(fixedRate = 1_000)
    public void processPendingTasks() {
        List<Task> tasks = taskRepository.findByStatus(TaskStatus.CREATED);
        if (tasks.isEmpty()) {
            return;
        }
        log.info("Found {} pending task(s), dispatching...", tasks.size());
        tasks.forEach(taskProcessorService::handleTask);
    }
}

