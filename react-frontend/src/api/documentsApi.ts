import axios from 'axios';
import type {
  SwaggerDocument,
  ChatRequest,
  ChatResponse,
} from '../models/types';
import { authStorage } from '../services/authStorage';

// Базовый URL для API (можно вынести в переменные окружения)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Интерсептор для автоматического добавления токена к запросам
apiClient.interceptors.request.use(
  (config) => {
    const token = authStorage.getToken();
    if (token && authStorage.isTokenValid()) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Интерсептор для обработки ошибок авторизации
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Токен истек или невалиден - перенаправляем на страницу входа
      authStorage.clearAuth();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// API для работы с документами
export const documentsApi = {
  // Загрузка Swagger документа
  uploadDocument: async (file: File, name: string): Promise<SwaggerDocument> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('name', name);

    const response = await apiClient.post<SwaggerDocument>('swagger/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  // Получение списка документов
  getDocuments: async (): Promise<SwaggerDocument[]> => {
    const response = await apiClient.get<SwaggerDocument[]>('swagger', {});
    return response.data;
  },

  // Получение конкретного документа
  getDocument: async (documentId: string): Promise<SwaggerDocument> => {
    const response = await apiClient.get<SwaggerDocument>(`swagger/${documentId}`);
    return response.data;
  },

  // Отправка вопроса по документу
  sendChatMessage: async (documentId: string, request: ChatRequest): Promise<ChatResponse> => {
    const response = await apiClient.post<ChatResponse>(
      `swagger/${documentId}/chat`,
      {
        query: request.question,
        role: request.role || 'analytic'
      }
    );
    return response.data;
  },

  // Удаление документа
  deleteDocument: async (documentId: string): Promise<void> => {
    await apiClient.delete(`swagger/${documentId}`);
  },
};

