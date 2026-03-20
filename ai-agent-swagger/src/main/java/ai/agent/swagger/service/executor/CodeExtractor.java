package ai.agent.swagger.service.executor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeExtractor {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "<<<CODE_START(?:\\s+language=\\w+)?>>>\\s*(.*?)\\s*<<<CODE_END>>>",
            Pattern.DOTALL
    );

    private static final Pattern MARKDOWN_BLOCK_PATTERN = Pattern.compile(
            "```(?:python|py)?\\s*\\n(.*?)\\n\\s*```",
            Pattern.DOTALL
    );

    private CodeExtractor() {}

    /**
     * Извлекает код из строки вида:
     * <<<CODE_START language=python>>>
     * ... код ...
     * <<<CODE_END>>>
     *
     * Если служебный блок не найден, вся строка считается кодом.
     *
     * @return Optional с кодом, или empty если текст пустой
     */
    public static Optional<String> extract(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        // 1. <<<CODE_START>>> ... <<<CODE_END>>>
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        // 2. ```python ... ```
        Matcher mdMatcher = MARKDOWN_BLOCK_PATTERN.matcher(text);
        if (mdMatcher.find()) {
            return Optional.of(mdMatcher.group(1).trim());
        }
        // 3. Служебный блок не найден — считаем всю строку кодом
        return Optional.of(text.trim());
    }
}

