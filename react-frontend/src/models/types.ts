// Модели данных для API

export interface SwaggerDocument {
  id: string;
  name?: string;
  summary?: string;
  documentSummary?: string;
  content?: string; // OpenAPI document content (.yaml or .json)
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

