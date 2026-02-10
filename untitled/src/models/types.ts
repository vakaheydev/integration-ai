// Модели данных для API

export interface SwaggerDocument {
  id: string;
  summary?: string;
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
}

export interface ChatResponse {
  answer: string;
}

export interface ErrorResponse {
  message: string;
  error?: string;
}

