# Техническая документация компонентов

## Архитектура приложения

Приложение построено на принципе разделения ответственности по слоям:

1. **API Layer** - взаимодействие с backend
2. **Models Layer** - типизация данных
3. **Components Layer** - переиспользуемые UI компоненты
4. **Pages Layer** - композиция компонентов в страницы

## API Layer

### `src/api/documentsApi.ts`

**Назначение:** Централизованное управление HTTP запросами к backend.

**Экспортируемые функции:**

```typescript
documentsApi.uploadDocument(file: File, userId?: string): Promise<SwaggerDocument>
```
Загружает OpenAPI документ на сервер.

```typescript
documentsApi.getDocuments(userId?: string): Promise<SwaggerDocument[]>
```
Получает список всех документов.

```typescript
documentsApi.getDocument(documentId: string): Promise<SwaggerDocument>
```
Получает информацию о конкретном документе.

```typescript
documentsApi.sendChatMessage(documentId: string, request: ChatRequest): Promise<ChatResponse>
```
Отправляет вопрос по документу и получает ответ от LLM.

**Технические детали:**
- Использует Axios для HTTP запросов
- Base URL настраивается через переменную окружения
- Автоматическая обработка JSON (Content-Type: application/json)
- Поддержка multipart/form-data для загрузки файлов

---

## Models Layer

### `src/models/types.ts`

**Назначение:** Определение TypeScript интерфейсов для типобезопасности.

**Основные типы:**

```typescript
interface SwaggerDocument {
  id: string;
  summary?: string;
  userId?: string;
  uploadedAt?: string;
}
```
Представление Swagger документа.

```typescript
interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}
```
Сообщение в чате.

```typescript
interface ChatRequest {
  question: string;
}
```
Запрос к чат API.

```typescript
interface ChatResponse {
  answer: string;
}
```
Ответ от чат API.

---

## Components Layer

### `src/components/UploadDocument.tsx`

**Назначение:** Форма для загрузки OpenAPI документов.

**Props:**
```typescript
interface UploadDocumentProps {
  onUploadSuccess: () => void;
}
```

**Состояние:**
- `selectedFile` - выбранный файл
- `userId` - ID пользователя (опционально)
- `loading` - индикатор загрузки
- `error` - сообщение об ошибке
- `success` - сообщение об успехе

**Основные функции:**
- `handleFileChange()` - валидация выбранного файла (только JSON)
- `handleUpload()` - отправка файла на сервер

**UI элементы:**
- TextField для ввода User ID
- File input (скрытый) для выбора файла
- Button для выбора файла
- Button для загрузки
- Alert для отображения ошибок/успеха

---

### `src/components/DocumentList.tsx`

**Назначение:** Отображение списка загруженных документов.

**Props:**
```typescript
interface DocumentListProps {
  documents: SwaggerDocument[];
  loading: boolean;
  error: string | null;
  onSelectDocument: (document: SwaggerDocument) => void;
}
```

**Условные состояния рендеринга:**
1. `loading === true` → показать CircularProgress
2. `error !== null` → показать Alert с ошибкой
3. `documents.length === 0` → показать "Нет документов"
4. Иначе → показать List с документами

**UI элементы:**
- List с ListItem для каждого документа
- ListItemButton для выбора документа
- Icon (DescriptionIcon) для визуального оформления
- ListItemText с primary (название) и secondary (ID и дата)

---

### `src/components/ChatMessageItem.tsx`

**Назначение:** Отображение одного сообщения в чате.

**Props:**
```typescript
interface ChatMessageItemProps {
  message: ChatMessage;
}
```

**Логика отображения:**
- Сообщения пользователя выровнены справа (primary.light)
- Сообщения ассистента выровнены слева (grey.100)
- Avatar с разными иконками (PersonIcon / BotIcon)
- Timestamp внизу каждого сообщения

**UI элементы:**
- Box для выравнивания
- Avatar для иконки отправителя
- Paper для фона сообщения
- Typography для текста и времени

**CSS особенности:**
- `whiteSpace: 'pre-wrap'` - сохранение переносов строк
- `wordBreak: 'break-word'` - перенос длинных слов

---

## Pages Layer

### `src/pages/MainPage.tsx`

**Назначение:** Главная страница с загрузкой документов и списком.

**Состояние:**
- `documents` - массив документов
- `selectedDocument` - текущий выбранный документ
- `loading` - индикатор загрузки списка
- `error` - ошибка при загрузке списка

**Основные функции:**
- `loadDocuments()` - загрузка списка документов с сервера
- `handleUploadSuccess()` - обработчик успешной загрузки (обновляет список)
- `handleSelectDocument()` - переход к чату с документом
- `handleBackToList()` - возврат к списку документов

**Логика рендеринга:**
- Если `selectedDocument !== null` → показать ChatInterface
- Иначе → показать UploadDocument + DocumentList

**UI элементы:**
- Container для ограничения ширины
- Grid layout (1 колонка на мобильных, 2 на desktop)
- Typography для заголовка
- Divider для разделения
- Button для обновления списка

**useEffect:**
Автоматическая загрузка списка документов при монтировании компонента.

---

### `src/pages/ChatInterface.tsx`

**Назначение:** Интерфейс чата для работы с конкретным документом.

**Props:**
```typescript
interface ChatInterfaceProps {
  document: SwaggerDocument;
  onBack: () => void;
}
```

**Состояние:**
- `messages` - массив сообщений чата
- `input` - текущий текст в поле ввода
- `loading` - индикатор загрузки ответа
- `error` - ошибка при отправке сообщения
- `messagesEndRef` - ref для автопрокрутки

**Основные функции:**

```typescript
handleSend()
```
1. Валидация непустого input
2. Добавление сообщения пользователя в массив
3. Очистка поля ввода
4. Отправка запроса к API
5. Добавление ответа ассистента в массив
6. Обработка ошибок (возврат текста в input)

```typescript
handleKeyDown(event)
```
Отправка сообщения по Enter (без Shift).

```typescript
scrollToBottom()
```
Прокрутка к последнему сообщению.

**useEffect:**
Автопрокрутка при изменении массива сообщений.

**UI структура:**

1. **Заголовок (Paper):**
   - IconButton "Назад"
   - DescriptionIcon
   - Название документа
   - Chip "Активен"

2. **Область сообщений (Paper):**
   - Пустое состояние с подсказкой
   - Список ChatMessageItem
   - Индикатор загрузки
   - Ref для автопрокрутки

3. **Поле ввода (Paper):**
   - Alert для ошибок
   - TextField (multiline)
   - IconButton для отправки

**Особенности:**
- `maxRows={4}` - ограничение высоты поля ввода
- `disabled={loading}` - блокировка во время загрузки
- Placeholder с подсказкой

---

## Root Components

### `src/App.tsx`

**Назначение:** Корневой компонент приложения.

**Структура:**
```typescript
<ThemeProvider theme={theme}>
  <CssBaseline />
  <MainPage />
</ThemeProvider>
```

**Тема Material UI:**
```typescript
const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#1976d2' },
    secondary: { main: '#dc004e' },
  },
  typography: {
    fontFamily: [...],
  },
});
```

**Компоненты:**
- `ThemeProvider` - предоставляет тему для всех MUI компонентов
- `CssBaseline` - нормализация CSS стилей

---

### `src/main.tsx`

**Назначение:** Точка входа приложения.

```typescript
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

**Функции:**
- Создание React root для React 18
- Оборачивание в StrictMode для выявления проблем в dev режиме

---

## Утилиты и конфигурация

### `src/vite-env.d.ts`

**Назначение:** TypeScript декларации для Vite.

Определяет типы для `import.meta.env`:
```typescript
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
}
```

---

## Паттерны и Best Practices

### 1. Композиция компонентов

Компоненты строятся по принципу композиции:
- Маленькие переиспользуемые компоненты (ChatMessageItem)
- Средние композитные компоненты (DocumentList, UploadDocument)
- Большие страницы (MainPage, ChatInterface)

### 2. Props Drilling

Избегаем глубокой передачи props:
- Максимум 2-3 уровня вложенности
- Callback функции передаются напрямую

### 3. Обработка асинхронности

```typescript
const handleSend = async () => {
  setLoading(true);
  try {
    const response = await api.call();
    // обработка успеха
  } catch (err) {
    // обработка ошибки
  } finally {
    setLoading(false);
  }
};
```

### 4. Типизация

Все компоненты и функции строго типизированы:
```typescript
export const Component: React.FC<Props> = ({ prop1, prop2 }) => {
  // ...
};
```

### 5. Условный рендеринг

```typescript
{loading && <CircularProgress />}
{error && <Alert severity="error">{error}</Alert>}
{data ? <Content data={data} /> : <EmptyState />}
```

### 6. useCallback для оптимизации

```typescript
const memoizedCallback = useCallback(() => {
  // логика
}, [dependencies]);
```

---

## Тестирование (рекомендации)

### Unit тесты
- Тестирование чистых функций (валидация)
- Тестирование API функций с mock данными

### Component тесты
- Рендеринг компонентов с разными props
- Взаимодействие пользователя (клики, ввод)
- Условные состояния (loading, error, success)

### Integration тесты
- Полный flow: загрузка → выбор → чат
- Обработка ошибок API

### Инструменты
- Vitest для unit тестов
- React Testing Library для component тестов
- MSW для мокирования API

---

## Производительность

### Оптимизации
1. **Code Splitting:** Vite автоматически разделяет код
2. **Lazy Loading:** Можно добавить для больших компонентов
3. **Memoization:** useCallback для функций, useMemo для вычислений
4. **Virtual Scrolling:** Для больших списков документов (react-window)

### Метрики текущей сборки
- Bundle size: 472 KB (150 KB gzipped)
- Build time: ~8.5s
- Modules: 11737

---

## Безопасность

### XSS Protection
- React автоматически экранирует вывод
- Нет использования `dangerouslySetInnerHTML`

### CSRF
- Не требуется для текущей реализации (без cookies)
- При добавлении аутентификации - использовать CSRF токены

### Валидация
- Клиентская валидация формата файлов
- Серверная валидация обязательна на backend

---

Эта документация покрывает все основные компоненты и их взаимодействие в приложении.

