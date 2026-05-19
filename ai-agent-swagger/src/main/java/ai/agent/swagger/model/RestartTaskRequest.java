package ai.agent.swagger.model;

import lombok.Data;

@Data
public class RestartTaskRequest {
    /** Сообщение пользователя — что не устроило в предыдущем результате */
    private String userMessage;
}

