package ai.agent.swagger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Описание ожидающего аппрува пользователя.
 * Хранится в Task.pendingApproval и очищается после обработки решения.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingApproval {

    /** Тип аппрува */
    private ApprovalType type;

    /**
     * Предмет аппрува:
     * - API_CALL: ключ вызова "METHOD::URL"
     * - CODE_EXECUTION: null (код хранится в task.result)
     */
    private String subject;

    /** Человекочитаемое описание, показываемое пользователю */
    private String description;
}
