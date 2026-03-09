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

    public ToolDescriptionProvider(SwaggerTools swaggerTools) {
        this.swaggerTools = swaggerTools;
    }

    public String getToolsDescription() {
        List<String> lines = new ArrayList<>();
        for (Method method : swaggerTools.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                continue;
            }
            String description = annotation.value().length > 0 ? annotation.value()[0] : "(no description)";
            String signature = buildSignature(method);
            lines.add("- " + signature + ": " + description);
        }
        return String.join("\n", lines);
    }

    private String buildSignature(Method method) {
        String params = Arrays.stream(method.getParameters())
                .map(p -> p.getType().getSimpleName() + " " + p.getName())
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + params + ")";
    }
}

