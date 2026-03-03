package ai.agent.swagger.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Тестовый вариант App без @EnableMongoRepositories.
 * Используется как bootstrapClass в @WebMvcTest через @BootstrapWith,
 * чтобы Spring не пытался создавать MongoDB-репозитории.
 */
@SpringBootApplication(scanBasePackages = "ai.agent.swagger")
public class TestApp {
}

