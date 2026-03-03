package ai.agent.swagger.controller;

import ai.agent.swagger.config.MockServicesConfig;
import ai.agent.swagger.config.MongoConfig;
import ai.agent.swagger.model.SecurityUser;
import ai.agent.swagger.model.SwaggerDocument;
import ai.agent.swagger.model.SwaggerSearchResult;
import ai.agent.swagger.service.SwaggerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IT-001 — Загрузка валидного Swagger через API
 * IT-002 — Получение Swagger по ID
 * IT-005 — Семантический поиск существующего Swagger
 * IT-006 — Семантический поиск несуществующего Swagger
 * SEC-001 — Доступ без JWT
 * SEC-002 — Доступ с невалидным JWT
 * SEC-003 — Доступ с JWT без нужных прав
 */
@DisplayName("IT-001/IT-002/IT-005/IT-006/SEC-001/SEC-003 — Контроллер /api/me/swagger")
@Import(MockServicesConfig.class)
@WebMvcTest(value = UserSwaggerController.class, excludeAutoConfiguration = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MongoConfig.class))
public class UserSwaggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SwaggerService swaggerService;


    private final ObjectMapper objectMapper = new ObjectMapper();

    private SecurityUser mockUser(String id, String role) {
        return new SecurityUser(id, "testuser", "hashed",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    // IT-002: GET /api/me/swagger/{id} — авторизованный пользователь получает свой документ
    @Test
    @DisplayName("IT-002: авторизованный пользователь получает свой документ по ID")
    public void testGetDocument_authorized_returnsDocument() throws Exception {
        SecurityUser currentUser = mockUser("user-1", "USER");
        SwaggerDocument doc = SwaggerDocument.builder()
                .id("doc-1").userId("user-1").name("My API").documentSummary("Summary").build();

        when(swaggerService.getSwaggerById("doc-1")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/me/swagger/doc-1").with(user(currentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.name").value("My API"));
    }

    // IT-002: GET /api/me/swagger/{id} — документ не найден → 404
    @Test
    @DisplayName("IT-002: документ не найден — возвращается 404")
    @WithMockUser(roles = "USER")
    public void testGetDocument_notFound_returns404() throws Exception {
        when(swaggerService.getSwaggerById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/me/swagger/missing"))
                .andExpect(status().isNotFound());
    }

    // IT-002: GET /api/me/swagger/{id} — чужой документ → 403
    @Test
    @DisplayName("IT-002: попытка получить чужой документ — возвращается 403")
    public void testGetDocument_otherUsersDoc_returns403() throws Exception {
        SecurityUser currentUser = mockUser("user-1", "USER");
        SwaggerDocument doc = SwaggerDocument.builder()
                .id("doc-2").userId("other-user").name("Other API").build();

        when(swaggerService.getSwaggerById("doc-2")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/me/swagger/doc-2").with(user(currentUser)))
                .andExpect(status().isForbidden());
    }

    // IT-005: POST /api/me/swagger/search — найдено → возвращает результат
    @Test
    @DisplayName("IT-005: поиск — документ найден, возвращает present=true и ответ модели")
    @WithMockUser(roles = "USER")
    public void testSearch_found_returnsResult() throws Exception {
        SwaggerDocument doc = SwaggerDocument.builder()
                .id("doc-1").userId("user-1").name("Test API").build();
        SwaggerSearchResult result = SwaggerSearchResult.builder()
                .present(true).document(doc).modelResponse("Это API для управления пользователями").build();

        when(swaggerService.search(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/api/me/swagger/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "user management"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true))
                .andExpect(jsonPath("$.modelResponse").exists());
    }

    // IT-006: POST /api/me/swagger/search — ничего не найдено → present=false
    @Test
    @DisplayName("IT-006: поиск — ничего не найдено, возвращает present=false")
    @WithMockUser(roles = "USER")
    public void testSearch_notFound_returnsPresentFalse() throws Exception {
        SwaggerSearchResult result = SwaggerSearchResult.builder()
                .present(false).modelResponse("Документ не найден. Попробуйте другой запрос.").build();

        when(swaggerService.search(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/api/me/swagger/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "some unknown topic"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(false));
    }

    // SEC-001: запрос без JWT → 401
    @Test
    @DisplayName("SEC-001: GET без JWT — возвращается 401")
    public void testGetDocument_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/me/swagger/doc-1"))
                .andExpect(status().isUnauthorized());
    }

    // SEC-001: поиск без JWT → 401
    @Test
    @DisplayName("SEC-001: POST /search без JWT — возвращается 401")
    public void testSearch_noJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/me/swagger/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "test"))))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/me/swagger — список документов текущего пользователя
    @Test
    @DisplayName("IT-002: GET /api/me/swagger — список всех документов текущего пользователя")
    @WithMockUser(roles = "USER")
    public void testGetMyDocuments_returnsListForCurrentUser() throws Exception {
        SwaggerDocument doc1 = SwaggerDocument.builder().id("d1").userId("user-1").name("API 1").build();
        SwaggerDocument doc2 = SwaggerDocument.builder().id("d2").userId("user-1").name("API 2").build();

        when(swaggerService.getSwaggersByUserId(anyString())).thenReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/api/me/swagger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // DELETE /api/me/swagger/{id} — успешное удаление своего документа
    @Test
    @DisplayName("IT-001: удаление своего документа — возвращается 204 No Content")
    public void testDeleteDocument_own_returns204() throws Exception {
        SecurityUser currentUser = mockUser("user-1", "USER");
        SwaggerDocument doc = SwaggerDocument.builder().id("doc-1").userId("user-1").name("API").build();

        when(swaggerService.getSwaggerById("doc-1")).thenReturn(Optional.of(doc));

        mockMvc.perform(delete("/api/me/swagger/doc-1").with(user(currentUser)))
                .andExpect(status().isNoContent());
    }

    // DELETE /api/me/swagger/{id} — чужой документ → 403
    @Test
    @DisplayName("SEC-003: удаление чужого документа — возвращается 403")
    public void testDeleteDocument_otherUser_returns403() throws Exception {
        SecurityUser currentUser = mockUser("user-1", "USER");
        SwaggerDocument doc = SwaggerDocument.builder().id("doc-2").userId("other-user").name("API").build();

        when(swaggerService.getSwaggerById("doc-2")).thenReturn(Optional.of(doc));

        mockMvc.perform(delete("/api/me/swagger/doc-2").with(user(currentUser)))
                .andExpect(status().isForbidden());
    }
}
