import axios from 'axios';
import type { LoginRequest, LoginResponse } from '../models/auth.types';

// Базовый URL для API
const API_BASE_URL = import.meta.env.VITE_API_BASE_LOGIN_URL;

const authClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// API для авторизации
export const authApi = {
  // Вход в систему
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const response = await authClient.post<LoginResponse>('/auth/login', credentials);
    return response.data;
  },

  // Обновление токена (повторная авторизация с теми же данными)
  refreshToken: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const response = await authClient.post<LoginResponse>('/auth/login', credentials);
    return response.data;
  },
};

