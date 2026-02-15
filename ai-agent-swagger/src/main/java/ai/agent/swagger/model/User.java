package ai.agent.swagger.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @Id
    @NotNull
    private String id;
    @Indexed(unique = true)
    @NotEmpty
    private String email;
    @NotEmpty
    @Indexed(unique = true)
    private String login;
    @NotEmpty
    private String passwordHash;
    private Set<String> roles;
    @NotNull
    private Instant createdAt;
}
