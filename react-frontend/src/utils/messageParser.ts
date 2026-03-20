// Утилита для парсинга сообщений с блоками кода

export interface MessagePart {
  type: 'text' | 'code';
  content: string;
  language?: string;
}

/**
 * Парсит сообщение и разделяет на текстовые части и блоки кода.
 * Поддерживает два формата:
 *   1) <<<CODE_START language=python>>> код <<<CODE_END>>>
 *   2) ```python  код  ```
 */
export const parseMessageWithCode = (message: string): MessagePart[] => {
  const parts: MessagePart[] = [];

  // Объединённое регулярное выражение: сначала <<<CODE_START>>>, потом ```lang
  const codeBlockRegex = /<<<CODE_START(?:\s+language=(\w+))?>>>([\s\S]*?)<<<CODE_END>>>|```(\w+)?\n([\s\S]*?)```/g;

  let lastIndex = 0;
  let match;

  while ((match = codeBlockRegex.exec(message)) !== null) {
    // Добавляем текст перед блоком кода
    if (match.index > lastIndex) {
      const textBefore = message.substring(lastIndex, match.index).trim();
      if (textBefore) {
        parts.push({ type: 'text', content: textBefore });
      }
    }

    // match[1] — язык из <<<CODE_START language=...>>>
    // match[2] — код из <<<CODE_START>>>
    // match[3] — язык из ```lang
    // match[4] — код из ```
    const language = match[1] || match[3] || 'plaintext';
    const code = (match[2] ?? match[4] ?? '').trim();

    parts.push({ type: 'code', content: code, language });

    lastIndex = codeBlockRegex.lastIndex;
  }

  // Добавляем оставшийся текст после последнего блока кода
  if (lastIndex < message.length) {
    const textAfter = message.substring(lastIndex).trim();
    if (textAfter) {
      parts.push({
        type: 'text',
        content: textAfter,
      });
    }
  }

  // Если не найдено блоков кода, возвращаем всё как текст
  if (parts.length === 0) {
    parts.push({
      type: 'text',
      content: message,
    });
  }

  return parts;
};

