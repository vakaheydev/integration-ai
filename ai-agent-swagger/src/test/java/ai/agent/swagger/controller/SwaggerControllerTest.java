package ai.agent.swagger.controller;

import ai.agent.swagger.config.MockServicesConfig;
import ai.agent.swagger.config.MongoConfig;
import ai.agent.swagger.model.SwaggerDocument;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IT-001 — Загрузка валидного Swagger через /api/admin/swagger (ADMIN)
 * IT-002 — Получение Swagger по ID (ADMIN)
 * SEC-001 — Доступ к /api/admin/swagger без JWT
 * SEC-003 — Доступ к /api/admin/swagger с ролью USER → 403
 */
@DisplayName("IT-001/IT-002/SEC-001/SEC-003 — Контроллер /api/admin/swagger")
@Import(MockServicesConfig.class)
@WebMvcTest(value = SwaggerController.class, excludeAutoConfiguration = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MongoConfig.class))
public class SwaggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SwaggerService swaggerService;


    private final ObjectMapper objectMapper = new ObjectMapper();

    // IT-002: GET /api/admin/swagger/{id} — ADMIN получает документ
    @Test
    @DisplayName("IT-002: ADMIN получает документ по ID — возвращается 200 с данными")
    @WithMockUser(roles = "ADMIN")
    public void testGetDocument_admin_returnsDocument() throws Exception {
        SwaggerDocument doc = SwaggerDocument.builder()
                .id("doc-1").userId("user-1").name("Admin API").documentSummary("Summary text").build();

        when(swaggerService.getSwaggerById("doc-1")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/admin/swagger/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.name").value("Admin API"));
    }

    // IT-002: GET /api/admin/swagger/{id} — документ не найден → 404
    @Test
    @DisplayName("IT-002: документ не найден — возвращается 404")
    @WithMockUser(roles = "ADMIN")
    public void testGetDocument_notFound_returns404() throws Exception {
        when(swaggerService.getSwaggerById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/swagger/missing"))
                .andExpect(status().isNotFound());
    }

    // IT-002: GET /api/admin/swagger/user/{userId} — список документов пользователя
    @Test
    @DisplayName("IT-002: ADMIN получает список документов пользователя — возвращается список")
    @WithMockUser(roles = "ADMIN")
    public void testGetDocumentsByUser_found_returnsList() throws Exception {
        SwaggerDocument doc = SwaggerDocument.builder().id("d1").userId("u1").name("API 1").build();

        when(swaggerService.getSwaggersByUserId("u1")).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/admin/swagger/user/u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // IT-002: GET /api/admin/swagger/user/{userId} — пользователь не имеет документов → 404
    @Test
    @DisplayName("IT-002: у пользователя нет документов — возвращается 404")
    @WithMockUser(roles = "ADMIN")
    public void testGetDocumentsByUser_empty_returns404() throws Exception {
        when(swaggerService.getSwaggersByUserId("u-none")).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/swagger/user/u-none"))
                .andExpect(status().isNotFound());
    }

    // IT-001: DELETE /api/admin/swagger/{id} — ADMIN удаляет документ → 204
    @Test
    @DisplayName("IT-001: ADMIN удаляет документ — возвращается 204 No Content")
    @WithMockUser(roles = "ADMIN")
    public void testDeleteDocument_admin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/swagger/doc-1"))
                .andExpect(status().isNoContent());
    }

    // SEC-001: GET /api/admin/swagger/{id} без JWT → 401
    @Test
    @DisplayName("SEC-001: GET /api/admin/swagger/{id} без JWT — возвращается 401")
    public void testGetDocument_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/swagger/doc-1"))
                .andExpect(status().isUnauthorized());
    }

    // SEC-001: POST /api/admin/swagger/search без JWT → 401
    @Test
    @DisplayName("SEC-001: POST /api/admin/swagger/search без JWT — возвращается 401")
    public void testSearch_noJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/swagger/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "test"))))
                .andExpect(status().isUnauthorized());
    }

    // SEC-003: GET /api/admin/swagger/{id} с ролью USER → 403
    @Test
    @DisplayName("SEC-003: GET /api/admin/swagger/{id} с ролью USER — возвращается 403")
    @WithMockUser(roles = "USER")
    public void testGetDocument_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/swagger/doc-1"))
                .andExpect(status().isForbidden());
    }

    // SEC-003: DELETE /api/admin/swagger/{id} с ролью USER → 403
    @Test
    @DisplayName("SEC-003: DELETE /api/admin/swagger/{id} с ролью USER — возвращается 403")
    @WithMockUser(roles = "USER")
    public void testDeleteDocument_userRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/admin/swagger/doc-1"))
                .andExpect(status().isForbidden());
    }
}
