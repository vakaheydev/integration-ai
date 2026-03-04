package ai.agent.swagger.controller;

import ai.agent.swagger.config.MockServicesConfig;
import ai.agent.swagger.config.MongoConfig;
import ai.agent.swagger.service.UserService;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("FT-006 - Валидация данных пользователя при регистрации (некорректные данные)")
@Import(MockServicesConfig.class)
@WebMvcTest(value = UserController.class, excludeAutoConfiguration = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MongoConfig.class))
public class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("FT-006: пустой логин - возвращается 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    public void testCreateUser_blankLogin_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "login", "",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FT-006: логин короче 3 символов - возвращается 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    public void testCreateUser_tooShortLogin_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "login", "ab",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FT-006: пустой пароль - возвращается 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    public void testCreateUser_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "login", "validlogin",
                                "password", ""
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FT-006: пароль короче 6 символов - возвращается 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    public void testCreateUser_tooShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "login", "validlogin",
                                "password", "123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FT-006: некорректный email - возвращается 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    public void testCreateUser_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "login", "validlogin",
                                "password", "password123"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FT-006: пустое тело запроса - возвращается 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    public void testCreateUser_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}

