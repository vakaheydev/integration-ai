import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { authApi } from '../api/authApi';
import { authStorage } from '../services/authStorage';
import type { LoginRequest, AuthState } from '../models/auth.types';

interface AuthContextType extends AuthState {
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
  refreshToken: (credentials: LoginRequest) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth должен использоваться внутри AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [authState, setAuthState] = useState<AuthState>({
    isAuthenticated: false,
    accessToken: null,
    expiresAt: null,
    username: null,
  });

  // Проверяем авторизацию при загрузке
  useEffect(() => {
    const token = authStorage.getToken();
    const expiresAt = authStorage.getExpiresAt();
    const username = authStorage.getUsername();

    if (token && expiresAt && username && authStorage.isTokenValid()) {
      setAuthState({
        isAuthenticated: true,
        accessToken: token,
        expiresAt,
        username,
      });
    }
  }, []);

  // Автоматическая проверка истечения токена
  useEffect(() => {
    if (!authState.isAuthenticated || !authState.expiresAt) {
      return;
    }

    const expirationTime = new Date(authState.expiresAt).getTime();
    const currentTime = new Date().getTime();
    const timeUntilExpiration = expirationTime - currentTime;

    // Если токен истечет через 30 секунд или меньше
    if (timeUntilExpiration <= 30000) {
      logout();
      return;
    }

    // Устанавливаем таймер для выхода из системы
    const timeout = setTimeout(() => {
      logout();
    }, timeUntilExpiration);

    return () => clearTimeout(timeout);
  }, [authState.expiresAt, authState.isAuthenticated]);

  const login = async (credentials: LoginRequest): Promise<void> => {
    try {
      const response = await authApi.login(credentials);

      authStorage.saveAuth(response.accessToken, response.expiresAt, credentials.username);

      setAuthState({
        isAuthenticated: true,
        accessToken: response.accessToken,
        expiresAt: response.expiresAt,
        username: credentials.username,
      });
    } catch (error) {
      authStorage.clearAuth();
      throw error;
    }
  };

  const logout = (): void => {
    authStorage.clearAuth();
    setAuthState({
      isAuthenticated: false,
      accessToken: null,
      expiresAt: null,
      username: null,
    });
  };

  const refreshToken = async (credentials: LoginRequest): Promise<void> => {
    try {
      const response = await authApi.refreshToken(credentials);

      authStorage.saveAuth(response.accessToken, response.expiresAt, credentials.username);

      setAuthState({
        isAuthenticated: true,
        accessToken: response.accessToken,
        expiresAt: response.expiresAt,
        username: credentials.username,
      });
    } catch (error) {
      authStorage.clearAuth();
      logout();
      throw error;
    }
  };

  return (
    <AuthContext.Provider value={{ ...authState, login, logout, refreshToken }}>
      {children}
    </AuthContext.Provider>
  );
};

