// Сервис для управления токенами авторизации

const TOKEN_KEY = 'auth_token';
const EXPIRES_AT_KEY = 'token_expires_at';
const USERNAME_KEY = 'username';

export const authStorage = {
  // Сохранить токен и данные авторизации
  saveAuth: (accessToken: string, expiresAt: string, username: string): void => {
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(EXPIRES_AT_KEY, expiresAt);
    localStorage.setItem(USERNAME_KEY, username);
  },

  // Получить текущий токен
  getToken: (): string | null => {
    return localStorage.getItem(TOKEN_KEY);
  },

  // Получить время истечения токена
  getExpiresAt: (): string | null => {
    return localStorage.getItem(EXPIRES_AT_KEY);
  },

  // Получить имя пользователя
  getUsername: (): string | null => {
    return localStorage.getItem(USERNAME_KEY);
  },

  // Проверить, не истек ли токен
  isTokenValid: (): boolean => {
    const token = authStorage.getToken();
    const expiresAt = authStorage.getExpiresAt();

    if (!token || !expiresAt) {
      return false;
    }

    const expirationTime = new Date(expiresAt).getTime();
    const currentTime = new Date().getTime();

    // Добавляем небольшой буфер (30 секунд) для обновления токена заранее
    return currentTime < expirationTime - 30000;
  },

  // Очистить данные авторизации
  clearAuth: (): void => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    localStorage.removeItem(USERNAME_KEY);
  },

  // Проверить, авторизован ли пользователь
  isAuthenticated: (): boolean => {
    return authStorage.isTokenValid();
  },
};

