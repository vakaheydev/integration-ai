package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Stage {
    private int id;
    private String name;
    private String description;
    @Builder.Default
    private TaskStatus status = TaskStatus.CREATED;
    private Instant instantStart;
    private Instant instantEnd;

    /** Вопрос ИИ к пользователю (заполняется при WAITING_USER_INPUT) */
    private String aiQuestion;

    /** Ответ пользователя на вопрос ИИ */
    private String userInputResponse;

    /** Описание того, что нужно подтвердить (заполняется при WAITING_USER_APPROVE) */
    private String approveDescription;

    /** Комментарий пользователя при отклонении */
    private String approveMessage;

    /** Результат выполнения стейджа (код, ответ ИИ и т.д.) */
    private String result;

    public Duration getDuration() {
        if (instantStart == null || instantEnd == null) {
            return null;
        }
        return Duration.between(instantStart, instantEnd);
    }
}
