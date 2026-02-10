import axios from 'axios';
import type {
  SwaggerDocument,
  ChatRequest,
  ChatResponse,
} from '../models/types';

// Базовый URL для API (можно вынести в переменные окружения)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// API для работы с документами
export const documentsApi = {
  // Загрузка Swagger документа
  uploadDocument: async (file: File, userId?: string): Promise<SwaggerDocument> => {
    const formData = new FormData();
    formData.append('file', file);
    if (userId) {
      formData.append('userId', userId);
    }

    const response = await apiClient.post<SwaggerDocument>('/documents/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  // Получение списка документов
  getDocuments: async (userId?: string): Promise<SwaggerDocument[]> => {
    const response = await apiClient.get<SwaggerDocument[]>('/documents', {
      params: userId ? { userId } : undefined,
    });
    return response.data;
  },

  // Получение конкретного документа
  getDocument: async (documentId: string): Promise<SwaggerDocument> => {
    const response = await apiClient.get<SwaggerDocument>(`/documents/${documentId}`);
    return response.data;
  },

  // Отправка вопроса по документу
  sendChatMessage: async (documentId: string, request: ChatRequest): Promise<ChatResponse> => {
    const response = await apiClient.post<ChatResponse>(
      `/documents/${documentId}/chat`,
      request
    );
    return response.data;
  },
};

