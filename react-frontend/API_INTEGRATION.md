# API Integration Guide

Данный документ описывает API endpoints, которые должен предоставить backend для работы с frontend приложением.

## Base URL

По умолчанию: `http://localhost:8080/api`

Настраивается через переменную окружения `VITE_API_BASE_URL` в файле `.env`

## Endpoints

### 1. Загрузка документа

**Endpoint:** `POST /api/documents/upload`

**Content-Type:** `multipart/form-data`

**Параметры:**
- `file` (File, обязательный) - JSON файл с OpenAPI документом
- `userId` (String, опциональный) - ID пользователя

**Пример запроса:**
```http
POST /api/documents/upload
Content-Type: multipart/form-data

file: [binary data]
userId: "user123"
```

**Успешный ответ (200):**
```json
{
  "id": "doc-uuid-123",
  "summary": "Sample Pet Store API",
  "userId": "user123",
  "uploadedAt": "2026-02-09T10:30:00Z"
}
```

**Ошибка (400/500):**
```json
{
  "message": "Описание ошибки",
  "error": "Детали ошибки (опционально)"
}
```

---

### 2. Получение списка документов

**Endpoint:** `GET /api/documents`

**Параметры запроса:**
- `userId` (String, опциональный) - фильтр по пользователю

**Пример запроса:**
```http
GET /api/documents?userId=user123
```

**Успешный ответ (200):**
```json
[
  {
    "id": "doc-uuid-123",
    "summary": "Sample Pet Store API",
    "userId": "user123",
    "uploadedAt": "2026-02-09T10:30:00Z"
  },
  {
    "id": "doc-uuid-456",
    "summary": "E-commerce API",
    "userId": "user123",
    "uploadedAt": "2026-02-08T15:20:00Z"
  }
]
```

**Ошибка (500):**
```json
{
  "message": "Ошибка при получении списка документов",
  "error": "Database connection failed"
}
```

---

### 3. Получение конкретного документа

**Endpoint:** `GET /api/documents/{documentId}`

**Параметры пути:**
- `documentId` (String, обязательный) - ID документа

**Пример запроса:**
```http
GET /api/documents/doc-uuid-123
```

**Успешный ответ (200):**
```json
{
  "id": "doc-uuid-123",
  "summary": "Sample Pet Store API",
  "userId": "user123",
  "uploadedAt": "2026-02-09T10:30:00Z"
}
```

**Ошибка (404):**
```json
{
  "message": "Документ не найден",
  "error": "Document with id 'doc-uuid-123' not found"
}
```

---

### 4. Отправка вопроса по документу (Chat)

**Endpoint:** `POST /api/documents/{documentId}/chat`

**Content-Type:** `application/json`

**Параметры пути:**
- `documentId` (String, обязательный) - ID документа

**Тело запроса:**
```json
{
  "question": "Какие эндпоинты есть в этом API?"
}
```

**Пример запроса:**
```http
POST /api/documents/doc-uuid-123/chat
Content-Type: application/json

{
  "question": "Какие эндпоинты есть в этом API?"
}
```

**Успешный ответ (200):**
```json
{
  "answer": "В данном API есть следующие эндпоинты:\n1. GET /pets - получение списка всех питомцев\n2. POST /pets - создание нового питомца\n3. GET /pets/{petId} - получение информации о конкретном питомце"
}
```

**Ошибка (404):**
```json
{
  "message": "Документ не найден",
  "error": "Document with id 'doc-uuid-123' not found"
}
```

**Ошибка (400):**
```json
{
  "message": "Вопрос не может быть пустым",
  "error": "Question field is required"
}
```

**Ошибка (500):**
```json
{
  "message": "Ошибка при генерации ответа",
  "error": "LLM service unavailable"
}
```

---

## Дополнительные требования

### CORS

Backend должен поддерживать CORS для localhost:3000 (или другого порта, на котором запущен frontend).

**Пример конфигурации (Spring Boot):**
```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*");
            }
        };
    }
}
```

### Кодировка

Все ответы должны быть в UTF-8 для корректного отображения русскоязычного контента.

### Обработка ошибок

- Используйте правильные HTTP статус коды (200, 201, 400, 404, 500)
- Всегда возвращайте объект с полем `message` для ошибок
- Поле `error` опционально и может содержать технические детали

### Формат даты и времени

Используйте ISO 8601 формат: `2026-02-09T10:30:00Z`

---

## Примеры использования

### cURL примеры

**Загрузка документа:**
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@sample-swagger.json" \
  -F "userId=user123"
```

**Получение списка:**
```bash
curl http://localhost:8080/api/documents
```

**Чат:**
```bash
curl -X POST http://localhost:8080/api/documents/doc-uuid-123/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Какие эндпоинты есть в API?"}'
```

---

## Рекомендации по реализации

1. **Валидация загружаемых файлов**: проверяйте, что файл является валидным OpenAPI документом
2. **Хранение документов**: используйте базу данных или файловую систему
3. **Векторизация для RAG**: при загрузке документа создавайте векторные embeddings для эффективного поиска
4. **Кэширование**: рассмотрите кэширование частых запросов к LLM
5. **Rate limiting**: ограничивайте количество запросов к LLM API
6. **Логирование**: логируйте все запросы для отладки и аналитики

---

## Версионирование API

Текущая версия: **v1**

При изменении API используйте версионирование в URL: `/api/v2/documents`

