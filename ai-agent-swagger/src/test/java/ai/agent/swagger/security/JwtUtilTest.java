package ai.agent.swagger.security;

import ai.agent.swagger.model.JwtResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IT-007/IT-008/SEC-001/SEC-002 - Авторизация и валидация JWT")
public class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET = "a1b2c3d4e5f67890abcdef1234567890abcdef1234567890abcdef123456789";
    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    public void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("IT-007: генерация токена возвращает непустой JWT")
    public void testGenerateToken_validLogin_returnsToken() {
        JwtResponse response = jwtUtil.generateToken("user123");

        assertNotNull(response);
        assertNotNull(response.accessToken(), "Токен не должен быть null");
        assertFalse(response.accessToken().isEmpty(), "Токен не должен быть пустым");
        assertNotNull(response.expiresAt(), "Дата истечения должна присутствовать");
    }

    @Test
    @DisplayName("IT-007: токен можно провалидировать и извлечь логин")
    public void testValidateToken_validToken_returnsTrue() {
        JwtResponse response = jwtUtil.generateToken("user123");

        boolean valid = jwtUtil.validateToken(response.accessToken());
        String extractedLogin = jwtUtil.extractLogin(response.accessToken());

        assertTrue(valid, "Токен должен быть валидным");
        assertEquals("user123", extractedLogin, "Логин должен совпадать");
    }

    @Test
    @DisplayName("SEC-002: невалидный токен — validateToken возвращает false")
    public void testValidateToken_fakeToken_returnsFalse() {
        boolean valid = jwtUtil.validateToken("this.is.not.a.valid.jwt.token");

        assertFalse(valid, "Поддельный токен не должен проходить валидацию");
    }

    @Test
    @DisplayName("SEC-002: extractLogin на невалидном токене возвращает null, не падает")
    public void testExtractLogin_invalidToken_returnsNull() {
        String login = jwtUtil.extractLogin("fake.token.here");

        assertNull(login, "Логин должен быть null для невалидного токена");
    }

    @Test
    @DisplayName("SEC-001: пустая строка — validateToken возвращает false")
    public void testValidateToken_emptyString_returnsFalse() {
        assertFalse(jwtUtil.validateToken(""), "Пустой токен не должен быть валидным");
    }

    @Test
    @DisplayName("Секрет короче 32 байт — должно бросить IllegalArgumentException при создании")
    public void testJwtUtil_shortSecret_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("short", EXPIRATION_MS));
    }
}
