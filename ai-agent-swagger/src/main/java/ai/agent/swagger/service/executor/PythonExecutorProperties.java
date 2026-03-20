package ai.agent.swagger.service.executor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "executor.python")
@Data
public class PythonExecutorProperties {

    /** Docker image для запуска кода */
    private String image = "python:3.11-slim";

    /** Максимальное время выполнения в секундах */
    private int timeoutSeconds = 30;

    /** Лимит памяти контейнера (например: "128m") */
    private String memoryLimit = "128m";

    /** Лимит CPU (например: 0.5 = половина ядра) */
    private double cpuLimit = 0.5;

    /** URL Docker daemon (null = использовать системный сокет) */
    private String dockerHost = null;

    /**
     * Путь к директории с Dockerfile для сборки кастомного образа.
     * Если null — образ будет скачан через docker pull.
     */
    private String dockerfilePath = null;
}

