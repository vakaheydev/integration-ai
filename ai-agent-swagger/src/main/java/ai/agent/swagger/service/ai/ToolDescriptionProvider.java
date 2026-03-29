package ai.agent.swagger.service.ai;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ToolDescriptionProvider {

    private final SwaggerTools swaggerTools;
    private final ApiCallTools apiCallTools;

    public ToolDescriptionProvider(SwaggerTools swaggerTools, ApiCallTools apiCallTools) {
        this.swaggerTools = swaggerTools;
        this.apiCallTools = apiCallTools;
    }

    public String getToolsDescription() {
        List<String> lines = new ArrayList<>();
        collectToolDescriptions(lines, swaggerTools);
        collectToolDescriptions(lines, apiCallTools);
        return String.join("\n", lines);
    }

    private void collectToolDescriptions(List<String> lines, Object toolsInstance) {
        for (Method method : toolsInstance.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                continue;
            }
            String description = annotation.value().length > 0 ? annotation.value()[0] : "(no description)";
            String signature = buildSignature(method);
            lines.add("- " + signature + ": " + description);
        }
    }

    private String buildSignature(Method method) {
        String params = Arrays.stream(method.getParameters())
                .map(p -> p.getType().getSimpleName() + " " + p.getName())
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + params + ")";
    }
}

