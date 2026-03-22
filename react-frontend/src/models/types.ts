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
  model?: string; // AI model to use
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

export type TaskType = 'CODE' | 'ANALYZE' | 'TEST' | 'ANALYZE_CODE' | 'ANALYZE_TEST';
export type TaskStatus = 'CREATED' | 'RUNNING' | 'WAITING' | 'WAITING_USER_INPUT' | 'WAITING_USER_APPROVE' | 'WAITING_SUBTASK' | 'COMPLETED' | 'FAILED';

export interface TaskStage {
  id: number;
  name: string;
  description: string;
  instantStart: string;
  instantEnd: string;
  duration: string | null;
  status: TaskStatus;
  aiQuestion: string | null;
  userInputResponse: string | null;
  approveDescription: string | null;
  approveMessage: string | null;
  result: string | null;
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
  model: string | null;
  parentTaskId: string | null;
  scenarioType: string | null;
  scenarioStep: number | null;
}

export interface CreateTaskRequest {
  type: TaskType;
  description: string;
  modelName?: string;
}

// ---- AI Model models ----

export interface AIModel {
  id: string;
  name: string;
}

export interface ModelsResponse {
  defaultModel: string;
  availableModels: AIModel[];
}

