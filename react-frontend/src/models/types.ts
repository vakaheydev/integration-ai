// Модели данных для API

export interface SwaggerDocument {
  id: string;
  name?: string;
  summary?: string;
  documentSummary?: string;
  methodSummary?: string;
  content?: string;
  userId?: string;
  uploadedAt?: string;
}

export interface UploadDocumentRequest {
  file: File;
  userId?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

export interface ChatRequest {
  question: string;
  role?: string; // Роль для обработки запроса, по умолчанию "analytic"
}

export interface ChatResponse {
  answer?: string;
  // API может возвращать разные поля, используем индексную сигнатуру
  [key: string]: any;
}

export interface ErrorResponse {
  message: string;
  error?: string;
}

export interface SearchRequest {
  query: string;
}

export interface SearchResponse {
  present: boolean;
  document?: SwaggerDocument;
  modelResponse: string;
}

// ---- Task models ----

export type TaskType = 'CODE' | 'ANALYZE' | 'TEST';
export type TaskStatus = 'CREATED' | 'RUNNING' | 'WAITING' | 'COMPLETED' | 'FAILED';

export interface TaskStage {
  id: number;
  name: string;
  description: string;
  instantStart: string;
  instantEnd: string;
  duration: string | null;
  status: TaskStatus;
}

export interface Task {
  id: string;
  documentId: string;
  userId: string;
  type: TaskType;
  description: string;
  status: TaskStatus;
  currentStage: TaskStage | null;
  statusDescription: string | null;
  completedDatetime: string | null;
  result: string | null;
  stageHistory: TaskStage[];
}

export interface CreateTaskRequest {
  type: TaskType;
  description: string;
}

