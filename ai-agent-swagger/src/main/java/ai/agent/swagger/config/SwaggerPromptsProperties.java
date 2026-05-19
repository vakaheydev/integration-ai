package ai.agent.swagger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "prompts")
@Data
public class SwaggerPromptsProperties {

    private Roles roles;
    private Outputs outputs;
    private Actions actions;

    @Data
    public static class Roles {
        private String analyst;
        private String programmer;
    }

    @Data
    public static class Outputs {
        private String text;
        private String yesNo;
        private String analysisPlan;
        private String code;
         private String test;
        private String analysisResult;
    }

    @Data
    public static class Actions {
        private VectorSearch vectorSearch;
        private DocumentChat documentChat;
        private HandleTask handleTask;
        private CodeTask codeTask;
        private TestTask testTask;
        private CodeExecution codeExecution;

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
            private String reviewPreviousResult;
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
            // after-error variants
            private String handleTaskAnalysisAfterError;
            private String handleTaskAnalysisAfterErrorGeneral;
            // chat
            private String taskChat;
        }

        @Data
        public static class CodeTask {
            private String analyzeTask;
            private String retryAnalyzeTask;
            private String handleTaskAnalysis;
            private String retryHandleTaskAnalysis;
            private String handleTaskAnalysisAfterError;
            // general (no document) variants
            private String analyzeTaskGeneral;
            private String retryAnalyzeTaskGeneral;
            private String handleTaskAnalysisGeneral;
            private String retryHandleTaskAnalysisGeneral;
            private String handleTaskAnalysisAfterErrorGeneral;
        }

        @Data
        public static class CodeExecution {
            private String disapprovedRewriteCode;
            private String disapprovedRewriteTest;
            private String approveDescription;
            private String reviewCodeExecution;
            private String reviewCodeExecutionSolution;
            private String rewriteCode;
            private String rewriteTest;
        }

        @Data
        public static class TestTask {
            private String analyzeTask;
            private String retryAnalyzeTask;
            private String handleTaskAnalysis;
            private String retryHandleTaskAnalysis;
            private String handleTaskAnalysisAfterError;
            // general (no document) variants
            private String analyzeTaskGeneral;
            private String retryAnalyzeTaskGeneral;
            private String handleTaskAnalysisGeneral;
            private String retryHandleTaskAnalysisGeneral;
            private String handleTaskAnalysisAfterErrorGeneral;
        }
    }
}

