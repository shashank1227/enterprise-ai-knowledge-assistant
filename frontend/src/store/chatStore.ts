import { create } from 'zustand'
import { chatService } from '@/services/chat'
import type { ChatRequest, Citation, Conversation, Message } from '@/types'

interface ChatState {
  conversations: Conversation[]
  activeConversationId: string | null
  messages: Message[]
  isLoading: boolean
  isSending: boolean
  streamingContent: string
  isStreaming: boolean
  error: string | null

  loadConversations: () => Promise<void>
  selectConversation: (id: string | null) => void
  createConversation: (title?: string) => Promise<Conversation>
  deleteConversation: (id: string) => Promise<void>
  togglePin: (id: string) => Promise<void>
  sendMessage: (request: ChatRequest) => Promise<void>
  clearError: () => void
}

export const useChatStore = create<ChatState>()((set, get) => ({
  conversations: [],
  activeConversationId: null,
  messages: [],
  isLoading: false,
  isSending: false,
  streamingContent: '',
  isStreaming: false,
  error: null,

  loadConversations: async () => {
    set({ isLoading: true, error: null })
    try {
      const page = await chatService.getConversations(0, 50)
      set({ conversations: page.content, isLoading: false })
    } catch (err: unknown) {
      set({ error: getErrorMessage(err), isLoading: false })
    }
  },

  selectConversation: (id) => {
    set({ activeConversationId: id, messages: [] })
  },

  createConversation: async (title) => {
    const conv = await chatService.createConversation(title)
    set((state) => ({ conversations: [conv, ...state.conversations] }))
    return conv
  },

  deleteConversation: async (id) => {
    await chatService.deleteConversation(id)
    set((state) => ({
      conversations: state.conversations.filter((c) => c.id !== id),
      activeConversationId: state.activeConversationId === id ? null : state.activeConversationId,
      messages: state.activeConversationId === id ? [] : state.messages,
    }))
  },

  togglePin: async (id) => {
    const updated = await chatService.togglePin(id)
    set((state) => ({
      conversations: state.conversations.map((c) => (c.id === id ? updated : c)),
    }))
  },

  sendMessage: async (request) => {
    const tempUserMessage: Message = {
      id: `temp-${Date.now()}`,
      conversationId: request.conversationId ?? '',
      role: 'USER',
      content: request.message,
      createdAt: new Date().toISOString(),
    }

    set((state) => ({
      messages: [...state.messages, tempUserMessage],
      isSending: true,
      isStreaming: true,
      streamingContent: '',
      error: null,
    }))

    let streamingText = ''
    let finalCitations: Citation[] = []
    let stopStream: (() => void) | null = null

    stopStream = chatService.streamChat(
      request,
      (event) => {
        if (event.type === 'token') {
          streamingText += event.content
          set({ streamingContent: streamingText })
        } else if (event.type === 'citations') {
          finalCitations = event.citations
        } else if (event.type === 'done') {
          const assistantMessage: Message = {
            id: event.messageId,
            conversationId: event.conversationId,
            role: 'ASSISTANT',
            content: streamingText,
            citations: finalCitations,
            createdAt: new Date().toISOString(),
          }

          set((state) => {
            const updatedConversations = state.conversations.map((c) => {
              if (c.id === event.conversationId) {
                return { ...c, lastMessageAt: new Date().toISOString() }
              }
              return c
            })
            // If this was a new conversation, update the ID on the temp user message
            const updatedMessages = state.messages.map((m) =>
              m.id === tempUserMessage.id
                ? { ...m, conversationId: event.conversationId }
                : m
            )
            return {
              messages: [...updatedMessages, assistantMessage],
              activeConversationId: event.conversationId,
              streamingContent: '',
              isStreaming: false,
              isSending: false,
              conversations: updatedConversations,
            }
          })
        } else if (event.type === 'error') {
          set({ error: event.message, isStreaming: false, isSending: false, streamingContent: '' })
        }
      },
      (err) => {
        set({ error: err.message, isStreaming: false, isSending: false, streamingContent: '' })
      }
    )

    // Store abort fn so it can be called (currently unused externally)
    void stopStream
  },

  clearError: () => set({ error: null }),
}))

function getErrorMessage(err: unknown): string {
  if (err && typeof err === 'object' && 'message' in err) {
    return String((err as { message: string }).message)
  }
  return 'An unexpected error occurred'
}
