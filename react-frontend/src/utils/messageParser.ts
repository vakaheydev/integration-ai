// Утилита для парсинга сообщений с блоками кода

export interface MessagePart {
  type: 'text' | 'code';
  content: string;
  language?: string;
}

/**
 * Парсит сообщение и разделяет на текстовые части и блоки кода
 * Ищет паттерн: <<<CODE_START language=python>>> код <<<CODE_END>>>
 */
export const parseMessageWithCode = (message: string): MessagePart[] => {
  const parts: MessagePart[] = [];

  // Регулярное выражение для поиска блоков кода
  const codeBlockRegex = /<<<CODE_START(?:\s+language=(\w+))?>>>([\s\S]*?)<<<CODE_END>>>/g;

  let lastIndex = 0;
  let match;

  while ((match = codeBlockRegex.exec(message)) !== null) {
    // Добавляем текст перед блоком кода
    if (match.index > lastIndex) {
      const textBefore = message.substring(lastIndex, match.index).trim();
      if (textBefore) {
        parts.push({
          type: 'text',
          content: textBefore,
        });
      }
    }

    // Добавляем блок кода
    const language = match[1] || 'plaintext'; // язык из language=python
    const code = match[2].trim(); // код между маркерами

    parts.push({
      type: 'code',
      content: code,
      language: language,
    });

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

