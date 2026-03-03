package ai.agent.swagger.service;

import ai.agent.swagger.model.CreateUserRequest;
import ai.agent.swagger.model.User;
import ai.agent.swagger.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("FT-006 — Валидация данных пользователя при регистрации (некорректные данные)")
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Validator validator;

    @BeforeEach
    public void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ========================
    // FT-006: негативные тесты — некорректные данные, валидация отклоняет запрос
    // ========================

    @Test
    @DisplayName("FT-006: пустой логин — нарушение ограничения @NotBlank")
    public void testValidation_blankLogin_violationReported() {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com", "", "password123", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Должны быть нарушения валидации");
        assertTrue(
                violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("login")),
                "Нарушение должно быть на поле login"
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: логин из 1 символа — нарушение ограничения @Size(min=3)")
    public void testValidation_tooShortLogin_violationReported() {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com", "ab", "password123", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Должны быть нарушения валидации");
        assertTrue(
                violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("login")),
                "Нарушение должно быть на поле login"
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: пустой пароль — нарушение ограничения @NotBlank")
    public void testValidation_blankPassword_violationReported() {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com", "validlogin", "", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Должны быть нарушения валидации");
        assertTrue(
                violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")),
                "Нарушение должно быть на поле password"
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: пароль короче 6 символов — нарушение ограничения @Size(min=6)")
    public void testValidation_tooShortPassword_violationReported() {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com", "validlogin", "123", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Должны быть нарушения валидации");
        assertTrue(
                violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")),
                "Нарушение должно быть на поле password"
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: некорректный email — нарушение ограничения @Email")
    public void testValidation_invalidEmail_violationReported() {
        CreateUserRequest request = new CreateUserRequest(
                "not-an-email", "validlogin", "password123", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Должны быть нарушения валидации");
        assertTrue(
                violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")),
                "Нарушение должно быть на поле email"
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: пустой email — нарушение ограничения @NotBlank")
    public void testValidation_blankEmail_violationReported() {
        CreateUserRequest request = new CreateUserRequest(
                "", "validlogin", "password123", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Должны быть нарушения валидации");
        assertTrue(
                violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")),
                "Нарушение должно быть на поле email"
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: все поля пустые — нарушения на каждом обязательном поле")
    public void testValidation_allBlank_multipleViolations() {
        CreateUserRequest request = new CreateUserRequest("", "", "", null);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertTrue(violations.size() >= 3, "Должно быть минимум 3 нарушения (email, login, password)");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("FT-006: корректные данные — нарушений нет, пользователь создаётся")
    public void testValidation_validData_noViolations() {
        CreateUserRequest request = new CreateUserRequest(
                "user@example.com", "validlogin", "password123", null
        );

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "Не должно быть нарушений для корректных данных");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByLogin(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        User savedUser = User.builder()
                .id("id-1").email("user@example.com").login("validlogin")
                .passwordHash("hashed").roles(Set.of("USER")).createdAt(Instant.now()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.createUser(request);

        assertNotNull(result);
        verify(userRepository).save(any(User.class));
    }

    // ========================
    // Дополнительные тесты UserService
    // ========================

    @Test
    @DisplayName("getUserById: пользователь найден — возвращает Optional с данными")
    public void testGetUserById_existingUser_returnsUser() {
        User user = User.builder().id("u1").email("a@b.com").login("aaa")
                .passwordHash("h").roles(Set.of("USER")).createdAt(Instant.now()).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        Optional<User> result = userService.getUserById("u1");

        assertTrue(result.isPresent());
        assertEquals("u1", result.get().getId());
    }

    @Test
    @DisplayName("getUserById: пользователь не найден — возвращает пустой Optional")
    public void testGetUserById_notExisting_returnsEmpty() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        Optional<User> result = userService.getUserById("missing");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("deleteUser: пользователь существует — успешно удаляется")
    public void testDeleteUser_existingUser_deleted() {
        when(userRepository.existsById("u1")).thenReturn(true);
        doNothing().when(userRepository).deleteById("u1");

        userService.deleteUser("u1");

        verify(userRepository).deleteById("u1");
    }

    @Test
    @DisplayName("deleteUser: пользователь не найден — бросает IllegalArgumentException")
    public void testDeleteUser_notExisting_throwsException() {
        when(userRepository.existsById("missing")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser("missing"));
    }
}
