import { apiClient } from './api'
import type {
  ChatRequest,
  ChatResponse,
  Conversation,
  FeedbackRequest,
  Page,
  StreamEvent,
} from '@/types'

export const chatService = {
  // ── Conversations ──────────────────────────────────────

  async getConversations(page = 0, size = 20, pinned = false): Promise<Page<Conversation>> {
    const { data } = await apiClient.get<Page<Conversation>>('/chat/conversations', {
      params: { page, size, pinned },
    })
    return data
  },

  async createConversation(title?: string): Promise<Conversation> {
    const { data } = await apiClient.post<Conversation>('/chat/conversations', { title })
    return data
  },

  async deleteConversation(id: string): Promise<void> {
    await apiClient.delete(`/chat/conversations/${id}`)
  },

  async togglePin(id: string): Promise<Conversation> {
    const { data } = await apiClient.put<Conversation>(`/chat/conversations/${id}/pin`)
    return data
  },

  // ── Chat ───────────────────────────────────────────────

  async ask(request: ChatRequest): Promise<ChatResponse> {
    const { data } = await apiClient.post<ChatResponse>('/chat/ask', request)
    return data
  },

  /**
   * Stream chat response using Server-Sent Events.
   * Calls onEvent for each token/citation/done event, onError on failure.
   */
  streamChat(
    request: ChatRequest,
    onEvent: (event: StreamEvent) => void,
    onError: (error: Error) => void
  ): () => void {
    const controller = new AbortController()

    const token = localStorage.getItem('ka_access_token')

    fetch('/api/v1/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(request),
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }

        const reader = response.body?.getReader()
        if (!reader) throw new Error('No response body')

        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() ?? ''

          for (const line of lines) {
            if (line.startsWith('data:')) {
              try {
                const json = JSON.parse(line.slice(5).trim())
                onEvent(json as StreamEvent)
              } catch {
                // ignore malformed SSE lines
              }
            }
          }
        }
      })
      .catch((err: Error) => {
        if (err.name !== 'AbortError') {
          onError(err)
        }
      })

    return () => controller.abort()
  },

  // ── Feedback ───────────────────────────────────────────

  async submitFeedback(messageId: string, request: FeedbackRequest): Promise<void> {
    await apiClient.post(`/chat/messages/${messageId}/feedback`, request)
  },
}
