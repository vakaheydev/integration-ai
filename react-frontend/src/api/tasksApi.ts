import axios from 'axios';
import type { Task, CreateTaskRequest, ModelsResponse } from '../models/types';
import { authStorage } from '../services/authStorage';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const API_PUBLIC_URL = import.meta.env.VITE_API_BASE_LOGIN_URL; // /api (no auth)

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Public client (no auth) for endpoints like /api/models
const publicClient = axios.create({
  baseURL: API_PUBLIC_URL,
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
  reloadTask: async (taskId: string, userMessage?: string): Promise<Task> => {
    const response = await apiClient.post<Task>(`tasks/${taskId}/restart`, { userMessage: userMessage ?? '' });
    return response.data;
  },

  // Delete a task
  deleteTask: async (taskId: string): Promise<void> => {
    await apiClient.delete(`tasks/${taskId}`);
  },

  // Delete all tasks
  deleteAllTasks: async (): Promise<void> => {
    await apiClient.delete('tasks/deleteAll');
  },

  // Chat with AI about a task
  chatWithTask: async (taskId: string, query: string, role: string, model?: string): Promise<any> => {
    const response = await apiClient.post(`tasks/${taskId}/chat`, { query, role, ...(model ? { model } : {}) });
    return response.data;
  },

  // Execute Python code for a task
  executeCode: async (taskId: string, code?: string): Promise<{
    success: boolean;
    exitCode: number;
    stdout: string;
    stderr: string;
    timedOut: boolean;
  }> => {
    const response = await apiClient.post(`tasks/${taskId}/executeCode`, code ? { code } : {});
    return response.data;
  },

  // Create a new task based on an existing one
  createFromBase: async (taskId: string, userMessage?: string): Promise<Task> => {
    const response = await apiClient.post<Task>(`tasks/fromBase/${taskId}`, { userMessage: userMessage ?? '' });
    return response.data;
  },

  // Resolve user input request from AI
  resolveInput: async (taskId: string, message: string): Promise<Task> => {
    const response = await apiClient.post<Task>(`tasks/${taskId}/resolve_input`, { message });
    return response.data;
  },

  // Approve or disapprove a task (WAITING_USER_APPROVE)
  approveTask: async (taskId: string, status: boolean, message?: string): Promise<Task> => {
    // Use /api/me/tasks/{id}/approve (client baseURL already includes /api/me)
    const response = await apiClient.post<Task>(
      `tasks/${taskId}/approve`,
      // send message body only when disapproving
      status ? {} : { message },
      { params: { status } }
    );
    return response.data;
  },

  // Get subtasks for a parent task
  getSubtasks: async (taskId: string): Promise<Task[]> => {
    const response = await apiClient.get<Task[]>(`tasks/${taskId}/subtasks`);
    return response.data;
  },

  // Get available AI models (public endpoint, no auth)
  getModels: async (): Promise<ModelsResponse> => {
    const response = await publicClient.get<ModelsResponse>('/models');
    return response.data;
  },
};
