package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    /** Флаг подтверждения пользователем запуска кода (для CODE/TEST тасок) */
    private boolean approved;

    /** Описание того, что именно нужно подтвердить (заполняется при WAITING_USER_APPROVE) */
    private String approveDescription;

    /** Комментарий пользователя при отклонении кода (заполняется через /approve?status=false) */
    private String approveMessage;

    /** Результат предыдущего шага сценария (заполняется при цепочке подзадач) */
    private String chainInput;

    /** ID родительской таски (null для корневых задач и одиночных типов) */
    private String parentTaskId;

    /** Тип сценария родительской таски (ANALYZE_CODE, ANALYZE_TEST и т.д.) */
    private ScenarioType scenarioType;

    /** Индекс текущего шага в сценарии (0-based) */
    private int scenarioStep;
}
