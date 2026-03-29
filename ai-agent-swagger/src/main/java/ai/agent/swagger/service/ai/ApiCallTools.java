package ai.agent.swagger.service.ai;

import ai.agent.swagger.model.ApprovalType;
import ai.agent.swagger.model.PendingApproval;
import ai.agent.swagger.model.Task;
import ai.agent.swagger.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * LangChain4j tools для выполнения HTTP-запросов к API.
 * Используются ИИ во время стадии анализа для проверки контрактов (вход/выход) эндпоинтов.
 *
 * Каждый вызов требует предварительного аппрува пользователя через /approve.
 * Если аппрув не получен — tool возвращает маркер {@link #APPROVAL_MARKER},
 * ИИ видит его и формирует ответ, который переводит задачу в WAITING_USER_APPROVE.
 */
@Component
@Slf4j
public class ApiCallTools {

    /** Маркер в ответе ИИ, сигнализирующий о необходимости аппрува */
    static final String APPROVAL_MARKER = "<<<AWAITING_API_APPROVAL>>>";

    private static final int TIMEOUT_SECONDS = 15;
    private static final int MAX_RESPONSE_LENGTH = 4000;

    private final TaskService taskService;
    private final AiChatService aiChatService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiCallTools(TaskService taskService, @Lazy AiChatService aiChatService) {
        this.taskService = taskService;
        this.aiChatService = aiChatService;
    }

    @Tool("Выполняет HTTP GET запрос. Параметры: url — полный адрес эндпоинта; headersJson — заголовки запроса в виде JSON-объекта (например {\"Authorization\":\"Bearer token\"}), пустая строка если не нужны. Каждый вызов требует аппрува пользователя — при получении маркера <<<AWAITING_API_APPROVAL>>> задача перейдёт в ожидание подтверждения.")
    public String executeGet(String url, String headersJson) {
        String key = "GET::" + url;
        log.info("[API TOOL] executeGet called: url={}", url);
        String blocked = checkApproval(key, "GET", url, null);
        if (blocked != null) return blocked;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET();
            applyHeaders(builder, headersJson);
            String result = executeRequest(builder.build());
            log.info("[API TOOL] executeGet completed: url={}, response preview={}", url, preview(result));
            return result;
        } catch (Exception e) {
            log.warn("[API TOOL] executeGet failed: url={}, error={}", url, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Выполняет HTTP POST запрос. Параметры: url — полный адрес; headersJson — заголовки JSON или пустая строка; bodyJson — тело запроса JSON или пустая строка. Content-Type: application/json добавляется автоматически. Требует аппрув пользователя.")
    public String executePost(String url, String headersJson, String bodyJson) {
        String key = "POST::" + url;
        log.info("[API TOOL] executePost called: url={}, body={}", url, preview(bodyJson));
        String blocked = checkApproval(key, "POST", url, bodyJson);
        if (blocked != null) return blocked;
        try {
            String body = bodyJson != null ? bodyJson : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            applyHeaders(builder, headersJson);
            if (!hasContentType(headersJson)) builder.header("Content-Type", "application/json");
            String result = executeRequest(builder.build());
            log.info("[API TOOL] executePost completed: url={}, response preview={}", url, preview(result));
            return result;
        } catch (Exception e) {
            log.warn("[API TOOL] executePost failed: url={}, error={}", url, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Выполняет HTTP PUT запрос. Параметры: url — полный адрес; headersJson — заголовки JSON или пустая строка; bodyJson — тело запроса JSON или пустая строка. Content-Type: application/json добавляется автоматически. Требует аппрув пользователя.")
    public String executePut(String url, String headersJson, String bodyJson) {
        String key = "PUT::" + url;
        log.info("[API TOOL] executePut called: url={}, body={}", url, preview(bodyJson));
        String blocked = checkApproval(key, "PUT", url, bodyJson);
        if (blocked != null) return blocked;
        try {
            String body = bodyJson != null ? bodyJson : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .PUT(HttpRequest.BodyPublishers.ofString(body));
            applyHeaders(builder, headersJson);
            if (!hasContentType(headersJson)) builder.header("Content-Type", "application/json");
            String result = executeRequest(builder.build());
            log.info("[API TOOL] executePut completed: url={}, response preview={}", url, preview(result));
            return result;
        } catch (Exception e) {
            log.warn("[API TOOL] executePut failed: url={}, error={}", url, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Выполняет HTTP DELETE запрос. Параметры: url — полный адрес; headersJson — заголовки JSON или пустая строка. Требует аппрув пользователя.")
    public String executeDelete(String url, String headersJson) {
        String key = "DELETE::" + url;
        log.info("[API TOOL] executeDelete called: url={}", url);
        String blocked = checkApproval(key, "DELETE", url, null);
        if (blocked != null) return blocked;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .DELETE();
            applyHeaders(builder, headersJson);
            String result = executeRequest(builder.build());
            log.info("[API TOOL] executeDelete completed: url={}, response preview={}", url, preview(result));
            return result;
        } catch (Exception e) {
            log.warn("[API TOOL] executeDelete failed: url={}, error={}", url, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Выполняет HTTP PATCH запрос. Параметры: url — полный адрес; headersJson — заголовки JSON или пустая строка; bodyJson — тело запроса JSON или пустая строка. Content-Type: application/json добавляется автоматически. Требует аппрув пользователя.")
    public String executePatch(String url, String headersJson, String bodyJson) {
        String key = "PATCH::" + url;
        log.info("[API TOOL] executePatch called: url={}, body={}", url, preview(bodyJson));
        String blocked = checkApproval(key, "PATCH", url, bodyJson);
        if (blocked != null) return blocked;
        try {
            String body = bodyJson != null ? bodyJson : "";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
            applyHeaders(builder, headersJson);
            if (!hasContentType(headersJson)) builder.header("Content-Type", "application/json");
            String result = executeRequest(builder.build());
            log.info("[API TOOL] executePatch completed: url={}, response preview={}", url, preview(result));
            return result;
        } catch (Exception e) {
            log.warn("[API TOOL] executePatch failed: url={}, error={}", url, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    // ── approval check ────────────────────────────────────────────────────────

    /**
     * Проверяет, одобрен ли вызов пользователем.
     * @return null — можно выполнять; не-null строка — выполнение заблокировано (маркер или сообщение)
     */
    private String checkApproval(String key, String method, String url, String body) {
        if (ApiCallToolsContext.isRejected(key)) {
            log.info("API call tool: {} {} — rejected by user, skipping", method, url);
            return "Этот API вызов был отклонён пользователем: " + method + " " + url;
        }
        if (ApiCallToolsContext.isApproved(key)) {
            log.info("API call tool: {} {} — approved, executing", method, url);
            return null;
        }

        log.info("API call tool: {} {} — requires user approval", method, url);
        savePendingApproval(key, method, url, body);

        String bodyInfo = (body != null && !body.isBlank())
                ? "\nТело запроса: " + (body.length() > 200 ? body.substring(0, 200) + "..." : body)
                : "";

        return APPROVAL_MARKER + "\n"
                + "API вызов требует подтверждения пользователя.\n"
                + "Метод: " + method + " | URL: " + url + bodyInfo + "\n"
                + "Задача будет переведена в режим ожидания аппрува.";
    }

    private void savePendingApproval(String key, String method, String url, String body) {
        String taskId = ApiCallToolsContext.getTaskId();
        if (taskId == null) {
            log.warn("ApiCallToolsContext.taskId is null — cannot save pendingApproval");
            return;
        }
        try {
            String description = buildApprovalDescription(method, url, body);
            PendingApproval pending = PendingApproval.builder()
                    .type(ApprovalType.API_CALL)
                    .subject(key)
                    .description(description)
                    .build();
            Task patch = new Task();
            patch.setId(taskId);
            patch.setPendingApproval(pending);
            taskService.updateTask(patch);
            // Обновляем in-memory task, чтобы граф мог детектировать запрос аппрува
            // без повторного чтения из БД и без парсинга текста ответа LLM
            Task inMemoryTask = ApiCallToolsContext.getTask();
            if (inMemoryTask != null) {
                inMemoryTask.setPendingApproval(pending);
            }
        } catch (Exception e) {
            log.warn("Failed to save pendingApproval for task id={}: {}", taskId, e.getMessage());
        }
    }

    /**
     * Генерирует человекочитаемое объяснение аппрува через stateless чат с ИИ.
     * При ошибке возвращает базовое описание.
     */
    private String buildApprovalDescription(String method, String url, String body) {
        Task task = ApiCallToolsContext.getTask();
        String taskDescription = (task != null && task.getDescription() != null)
                ? task.getDescription() : "нет описания";
        String bodySnippet = (body != null && !body.isBlank())
                ? "\nТело запроса: " + (body.length() > 300 ? body.substring(0, 300) + "..." : body)
                : "";

        String prompt = "Задача пользователя: " + taskDescription + "\n\n"
                + "ИИ-агент хочет выполнить HTTP-запрос:\n"
                + method + " " + url + bodySnippet + "\n\n"
                + "В 1-2 предложениях объясни пользователю на русском языке: "
                + "зачем этот запрос нужен для выполнения задачи и что он делает. "
                + "Пиши просто, без технических деталей. Не упоминай слова 'HTTP', 'метод', 'эндпоинт'.";

        String bodyDisplay = (body != null && !body.isBlank())
                ? "Тело запроса:\n" + (body.length() > 500 ? body.substring(0, 500) + "..." : body)
                : "Тело запроса: отсутствует";

        try {
            String modelName = task != null ? task.getModelName() : null;
            String aiExplanation = aiChatService.chatStateless(prompt, modelName);
            return method + " " + url + "\n" + bodyDisplay + "\n\n" + aiExplanation;
        } catch (Exception e) {
            log.warn("Failed to generate AI approval description, using fallback: {}", e.getMessage());
            return method + " " + url + "\n" + bodyDisplay;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String preview(String s) {
        if (s == null || s.isBlank()) return "(empty)";
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String executeRequest(HttpRequest request) throws Exception {
        log.info("Executing API call: {} {}", request.method(), request.uri());
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        if (body != null && body.length() > MAX_RESPONSE_LENGTH) {
            body = body.substring(0, MAX_RESPONSE_LENGTH) + "... [truncated]";
        }
        return "HTTP " + response.statusCode() + "\n" + (body != null ? body : "");
    }

    @SuppressWarnings("unchecked")
    private void applyHeaders(HttpRequest.Builder builder, String headersJson) throws Exception {
        if (headersJson == null || headersJson.isBlank()) return;
        Map<String, String> headers = objectMapper.readValue(headersJson, Map.class);
        headers.forEach(builder::header);
    }

    private boolean hasContentType(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) return false;
        return headersJson.toLowerCase().contains("content-type");
    }
}
