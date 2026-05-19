package ai.agent.swagger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "ai.swagger")
@Data
public class AiModelProperties {

    private String defaultModel;
    private List<ModelEntry> availableModels = new ArrayList<>();

    @Data
    public static class ModelEntry {
        private String id;
        private String name;
    }

    /** Возвращает множество допустимых model ID */
    public Set<String> getAvailableModelIds() {
        return availableModels.stream()
                .map(m -> m.getId())
                .collect(Collectors.toSet());
    }

    /** Проверяет, что model ID допустим */
    public boolean isModelAllowed(String modelId) {
        return modelId != null && getAvailableModelIds().contains(modelId);
    }

    /** Возвращает model ID — переданный или дефолтный, с валидацией */
    public String resolveModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return defaultModel;
        }
        if (!isModelAllowed(modelId)) {
            throw new IllegalArgumentException("Model not available: " + modelId
                    + ". Allowed models: " + getAvailableModelIds());
        }
        return modelId;
    }
}
