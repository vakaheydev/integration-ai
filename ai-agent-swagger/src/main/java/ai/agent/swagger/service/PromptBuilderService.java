package ai.agent.swagger.service;

import ai.agent.swagger.config.SwaggerPromptsProperties;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromptBuilderService {
    private final SwaggerPromptsProperties props;

    public PromptBuilderService(SwaggerPromptsProperties props) {
        this.props = props;
    }

    public String getAnalystRolePrompt(String prompt) {
        return props.getRoles().getAnalyst() + ".\n" + prompt;
    }

    public String getProgrammerRolePrompt(String prompt) {
        return props.getRoles().getProgrammer() + ".\n" + prompt;
    }

    public String getVectorSearchNotFoundPrompt(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            userPrompt = "empty prompt";
        }

        String template = props.getActions().getVectorSearch().getNotFound();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt));
        return getAnalystRolePrompt(prompt);
    }

    public String getDocumentChatAnalyze(String userPrompt, String swaggerSummary, String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getAnalyze();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt, "swaggerSummary", swaggerSummary, "swaggerMethodSummary", swaggerMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getDocumentChatUpload(String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getUpload();
        String prompt = replacePrompt(template, Map.of("swaggerMethodSummary", swaggerMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getDocumentChatCode(String userPrompt, String swaggerSummary, String swaggerMethodSummary) {
        String template = props.getActions().getDocumentChat().getCode();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt, "swaggerSummary", swaggerSummary, "swaggerMethodSummary", swaggerMethodSummary));
        return getAnalystRolePrompt(prompt);
    }

    public String getExtractKeywordsPrompt(String userPrompt) {
        String template = props.getActions().getVectorSearch().getExtractKeyWords();
        String prompt = replacePrompt(template, Map.of("userPrompt",  userPrompt));
        return getAnalystRolePrompt(prompt);
    }

    private String replacePrompt(String prompt, Map<String, String> placeHolders) {
        StringSubstitutor sub = new StringSubstitutor(placeHolders);
        return sub.replace(prompt);
    }
}
