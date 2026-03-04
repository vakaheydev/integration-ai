package ai.agent.swagger.security;

import ai.agent.swagger.model.SecurityUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
    public static SecurityUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser;
        }
        if (principal instanceof UserDetails userDetails) {
            // fallback для тестов с @WithMockUser — возвращаем SecurityUser с id = username
            return new SecurityUser(userDetails.getUsername(), userDetails.getUsername(),
                    "", userDetails.getAuthorities());
        }
        throw new IllegalStateException("No authenticated user found");
    }
}
