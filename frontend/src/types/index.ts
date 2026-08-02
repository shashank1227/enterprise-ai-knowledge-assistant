// ── Auth ───────────────────────────────────────────────────

export interface UserProfile {
  id: string
  email: string
  fullName: string
  avatarUrl?: string
  department?: string
  jobTitle?: string
  roles: string[]
  isActive: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

export interface LoginRequest {
  email: string
  password: string
}

export interface SignupRequest {
  email: string
  password: string
  fullName: string
  department?: string
  jobTitle?: string
}

// ── Conversations & Messages ───────────────────────────────

export interface Conversation {
  id: string
  title: string
  isPinned: boolean
  isArchived: boolean
  messageCount: number
  lastMessageAt?: string
  createdAt: string
  updatedAt: string
}

export interface Citation {
  index: number
  documentId: string
  documentTitle: string
  chunkId: string
  excerpt: string
  pageNumber?: number
  sectionTitle?: string
  relevanceScore: number
}

export interface Message {
  id: string
  conversationId: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations?: Citation[]
  tokensUsed?: number
  latencyMs?: number
  model?: string
  createdAt: string
}

export interface ChatRequest {
  message: string
  conversationId?: string
  topK?: number
  searchMode?: 'HYBRID' | 'VECTOR' | 'KEYWORD'
}

export interface ChatResponse {
  messageId: string
  conversationId: string
  answer: string
  citations: Citation[]
  tokensUsed: number
  latencyMs: number
  model: string
}

// ── Documents ──────────────────────────────────────────────

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'INDEXED' | 'FAILED'
export type FileType = 'PDF' | 'DOCX' | 'TXT' | 'MD' | 'HTML'

export interface Document {
  id: string
  title: string
  description?: string
  fileName: string
  fileType: FileType
  fileSizeBytes: number
  status: DocumentStatus
  processingError?: string
  tags?: string[]
  category?: string
  pageCount?: number
  wordCount?: number
  tokenCount?: number
  indexedAt?: string
  createdAt: string
  uploadedBy: string
}

export interface DocumentUploadRequest {
  file: File
  title?: string
  description?: string
  tags?: string[]
  category?: string
}

// ── Pagination ─────────────────────────────────────────────

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
}

// ── Feedback ───────────────────────────────────────────────

export type FeedbackType = 'HELPFUL' | 'NOT_HELPFUL' | 'INCORRECT' | 'INCOMPLETE'

export interface FeedbackRequest {
  rating: 1 | 2 | 3 | 4 | 5
  comment?: string
  feedbackType?: FeedbackType
}

// ── API Errors ─────────────────────────────────────────────

export interface ApiError {
  status: number
  error: string
  message: string
  timestamp: string
  path?: string
}

export interface ValidationError extends ApiError {
  errors: Record<string, string>
}

// ── Analytics ──────────────────────────────────────────────

export interface IngestionStats {
  totalDocuments: number
  indexedDocuments: number
  processingDocuments: number
  failedDocuments: number
  totalChunks: number
  totalStorageBytes: number
}

// ── SSE Streaming ──────────────────────────────────────────

export type StreamEvent =
  | { type: 'token'; content: string }
  | { type: 'citations'; citations: Citation[] }
  | { type: 'done'; messageId: string; conversationId: string }
  | { type: 'error'; message: string }
