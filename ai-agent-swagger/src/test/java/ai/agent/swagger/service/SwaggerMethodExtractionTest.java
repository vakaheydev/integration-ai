package ai.agent.swagger.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FT-001/FT-002/FT-003/FT-004 - Выделение методов из Swagger")
public class SwaggerMethodExtractionTest {

    private final SwaggerServiceDocument swaggerService = new SwaggerServiceDocument(null, null, null);

    @Test
    @DisplayName("FT-001: валидный OpenAPI - извлекаются все методы")
    public void testExtractMethods_validSwagger_returnsAllMethods() {
        String swagger = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Test API", "version": "1.0" },
                  "paths": {
                    "/users": {
                      "get": { "operationId": "getUsers", "description": "Get all users", "responses": {} },
                      "post": { "operationId": "createUser", "description": "Create user", "responses": {} }
                    },
                    "/users/{id}": {
                      "delete": { "operationId": "deleteUser", "description": "Delete user", "responses": {} }
                    }
                  }
                }
                """;

        String result = swaggerService.resolveSwaggerMethodSummary(swagger);

        assertNotNull(result);
        assertFalse(result.isEmpty(), "Результат не должен быть пустым");
        assertTrue(result.contains("/users"), "Должен содержать /users");
        assertTrue(result.contains("/users/{id}"), "Должен содержать /users/{id}");
        // Проверяем что дубликатов нет - каждый метод встречается ровно один раз
        long getCount = result.lines().filter(l -> l.startsWith("GET /users:")).count();
        long postCount = result.lines().filter(l -> l.startsWith("POST /users:")).count();
        assertEquals(1, getCount, "GET /users должен быть ровно 1 раз");
        assertEquals(1, postCount, "POST /users должен быть ровно 1 раз");
    }

    @Test
    @DisplayName("FT-002: пустой paths - NullPointerException не должен выбрасываться")
    public void testExtractMethods_emptyPaths_doesNotCrash() {
        String swagger = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Empty API", "version": "1.0" },
                  "paths": {}
                }
                """;

        assertDoesNotThrow(() -> {
            String result = swaggerService.resolveSwaggerMethodSummary(swagger);
            assertNotNull(result);
        }, "Приложение не должно падать с NPE на пустом paths");
    }

    @Test
    @DisplayName("FT-003: чтение валидного JSON - ключевые поля присутствуют в результате")
    public void testReadSwagger_validJson_keyFieldsPresent() {
        String swagger = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Pet Store", "version": "2.0" },
                  "paths": {
                    "/pets": {
                      "get": { "operationId": "listPets", "description": "List all pets", "responses": {} }
                    }
                  }
                }
                """;

        String result = swaggerService.resolveSwaggerMethodSummary(swagger);

        assertNotNull(result, "Результат не должен быть null");
        assertFalse(result.isEmpty(), "Результат не должен быть пустым");
        assertTrue(result.contains("/pets"), "Должен содержать путь /pets");
    }

    @Test
    @DisplayName("FT-004: повреждённый JSON - не должно быть неконтролируемого NPE")
    public void testReadSwagger_invalidJson_doesNotCrash() {
        String brokenSwagger = "{ this is not valid JSON !!!";

        try {
            swaggerService.resolveSwaggerMethodSummary(brokenSwagger);
        } catch (NullPointerException e) {
            fail("Не должно падать с NPE на повреждённом JSON: " + e.getMessage());
        } catch (Exception e) {
            assertNotNull(e.getMessage(), "Исключение должно иметь сообщение");
        }
    }
}
