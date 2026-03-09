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
        private HandleTask handleTask;

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

        @Data
        public static class HandleTask {
            private String reviewTaskResult;
            private String analyzeTask;
            private String retryAnalyzeTask;
            private String reviewTaskAnalysis;
            private String reviewTaskAnalysisSolution;
            private String handleTaskAnalysis;
            private String reviewHandleAnalysis;
            private String reviewHandleAnalysisSolution;
            private String retryHandleTaskAnalysis;
            // general (no document) variants
            private String analyzeTaskGeneral;
            private String retryAnalyzeTaskGeneral;
            private String handleTaskAnalysisGeneral;
            private String retryHandleTaskAnalysisGeneral;
            // chat
            private String taskChat;
        }
    }
}

