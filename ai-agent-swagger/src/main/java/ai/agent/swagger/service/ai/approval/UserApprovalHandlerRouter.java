package ai.agent.swagger.service.ai.approval;

import ai.agent.swagger.model.ApprovalType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Роутер хендлеров аппрувов.
 * Принимает все реализации {@link UserApprovalHandler} через Spring DI
 * и маршрутизирует запросы по {@link ApprovalType}.
 */
@Component
public class UserApprovalHandlerRouter {

    private final Map<ApprovalType, UserApprovalHandler> handlers;

    public UserApprovalHandlerRouter(List<UserApprovalHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(UserApprovalHandler::getApprovalType, h -> h));
    }

    public UserApprovalHandler getHandler(ApprovalType type) {
        UserApprovalHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No approval handler registered for type: " + type);
        }
        return handler;
    }
}
