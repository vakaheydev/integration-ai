package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document(collection = "tasks")
public class Task {
    @Id
    private String id;
    private String documentId;
    private String userId;
    private TaskType type;
    private String description;

    @Builder.Default
    private TaskStatus status = TaskStatus.CREATED;
    private Stage currentStage;
    private String statusDescription;
    private Instant completedDatetime;
    private String result;

    @Builder.Default
    private List<Stage> stageHistory = new ArrayList<>();

    /** Результат предыдущего выполнения (заполняется при рестарте) */
    private String previousResult;

    /** Сообщение пользователя при рестарте — что не устроило / что нужно исправить */
    private String userMessage;

    /** Вопрос ИИ к пользователю (заполняется при WAITING_USER_INPUT) */
    private String aiQuestion;

    /** Ответ пользователя на вопрос ИИ (заполняется через resolve_input) */
    private String userInputResponse;

    /** Идентификатор модели ИИ для выполнения таски (null = дефолтная) */
    private String modelName;

    /** Флаг подтверждения пользователем (true/false, устанавливается через /approve) */
    private boolean approved;

    /** Комментарий пользователя при отклонении (заполняется через /approve?status=false) */
    private String approveMessage;

    /**
     * Описание текущего ожидающего аппрува.
     * Устанавливается перед переходом в WAITING_USER_APPROVE, очищается после обработки решения.
     */
    private PendingApproval pendingApproval;

    /**
     * Множество одобренных пользователем API вызовов — "METHOD::URL".
     * Отклонённые вызовы хранятся с префиксом "REJECTED_METHOD::URL".
     * Накапливается в течение жизни задачи.
     */
    @Builder.Default
    private Set<String> approvedApiCalls = new HashSet<>();

    /** Результат предыдущего шага сценария (заполняется при цепочке подзадач) */
    private String chainInput;

    /** ID родительской таски (null для корневых задач и одиночных типов) */
    private String parentTaskId;

    /** Тип сценария родительской таски (ANALYZE_CODE, ANALYZE_TEST и т.д.) */
    private ScenarioType scenarioType;

    /** Индекс текущего шага в сценарии (0-based) */
    private int scenarioStep;
}
