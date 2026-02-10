package ai.agent.swagger.config;

import ai.agent.swagger.model.CreateUserRequest;
import ai.agent.swagger.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {
    private final UserService userService;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        createDefaultUsers();
    }

    private void createDefaultUsers() {
        if (!userService.existsByEmail("admin@example.com")) {
            CreateUserRequest adminRequest = new CreateUserRequest();
            adminRequest.setEmail("admin@example.com");
            adminRequest.setLogin("admin");
            adminRequest.setPassword("admin123");
            adminRequest.setRoles(Set.of("ADMIN", "USER"));

            userService.createUser(adminRequest);
            log.info("Admin user created: admin@example.com");
        } else {
            log.info("Admin user already exists");
        }

        if (!userService.existsByEmail("user@example.com")) {
            CreateUserRequest userRequest = new CreateUserRequest();
            userRequest.setEmail("user@example.com");
            userRequest.setLogin("user");
            userRequest.setPassword("user123");
            userRequest.setRoles(Set.of("USER"));

            userService.createUser(userRequest);
            log.info("Regular user created: user@example.com");
        } else {
            log.info("Regular user already exists");
        }
    }
}

