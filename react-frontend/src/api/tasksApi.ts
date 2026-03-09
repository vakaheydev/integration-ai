import axios from 'axios';
import type { Task, CreateTaskRequest } from '../models/types';
import { authStorage } from '../services/authStorage';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to every request
apiClient.interceptors.request.use(
  (config) => {
    const token = authStorage.getToken();
    if (token && authStorage.isTokenValid()) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Handle 401 responses
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      authStorage.clearAuth();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const tasksApi = {
  // Create a task for a document
  createTask: async (documentId: string, request: CreateTaskRequest): Promise<Task> => {
    const response = await apiClient.post<Task>(
      `swagger/${documentId}/task`,
      request
    );
    return response.data;
  },

  // Create a task without a specific document
  createTaskWithoutDocument: async (request: CreateTaskRequest): Promise<Task> => {
    const response = await apiClient.post<Task>('tasks', request);
    return response.data;
  },

  // Get all tasks for the current user
  getAllTasks: async (): Promise<Task[]> => {
    const response = await apiClient.get<Task[]>('tasks');
    return response.data;
  },

  // Get a specific task by id
  getTask: async (taskId: string): Promise<Task> => {
    const response = await apiClient.get<Task>(`tasks/${taskId}`);
    return response.data;
  },

  // Reload (restart) a task
  reloadTask: async (taskId: string): Promise<Task> => {
    const response = await apiClient.post<Task>(`tasks/${taskId}/restart`);
    return response.data;
  },

  // Delete a task
  deleteTask: async (taskId: string): Promise<void> => {
    await apiClient.delete(`tasks/${taskId}`);
  },
};

