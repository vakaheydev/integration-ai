package ai.agent.swagger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "prompts")
@Data
public class SwaggerPromptsProperties {

    private Roles roles;
    private Actions actions;

    @Data
    public static class Roles {
        private String analyst;
        private String programmer;
    }

    @Data
    public static class Actions {
        private VectorSearch vectorSearch;
        private DocumentChat documentChat;

        @Data
        public static class DocumentChat {
            private String analyze;
            private String code;
            private String upload;
        }

        @Data
        public static class VectorSearch {
            private String extractKeyWords;
            private String notFound;
        }
    }
}

