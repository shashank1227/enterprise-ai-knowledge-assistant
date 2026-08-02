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

export const useChatStore = create<ChatState>()((set) => ({
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

    const finishWithResponse = (response: {
      messageId: string
      conversationId: string
      answer: string
      citations?: Citation[]
    }) => {
      const assistantMessage: Message = {
        id: response.messageId,
        conversationId: response.conversationId,
        role: 'ASSISTANT',
        content: response.answer,
        citations: response.citations,
        createdAt: new Date().toISOString(),
      }

      set((state) => {
        const updatedConversations = state.conversations.map((c) =>
          c.id === response.conversationId
            ? { ...c, lastMessageAt: new Date().toISOString() }
            : c
        )
        const updatedMessages = state.messages.map((m) =>
          m.id === tempUserMessage.id
            ? { ...m, conversationId: response.conversationId }
            : m
        )
        return {
          messages: [...updatedMessages, assistantMessage],
          activeConversationId: response.conversationId,
          streamingContent: '',
          isStreaming: false,
          isSending: false,
          conversations: updatedConversations,
        }
      })
    }

    let streamingText = ''
    let finalCitations: Citation[] = []
    let settled = false

    chatService.streamChat(
      request,
      (event) => {
        if (event.type === 'token') {
          streamingText += event.content
          set({ streamingContent: streamingText })
        } else if (event.type === 'citations') {
          finalCitations = event.citations
        } else if (event.type === 'done') {
          settled = true
          finishWithResponse({
            messageId: event.messageId,
            conversationId: event.conversationId,
            answer: streamingText,
            citations: finalCitations,
          })
        } else if (event.type === 'error') {
          settled = true
          set({ error: event.message, isStreaming: false, isSending: false, streamingContent: '' })
        }
      },
      async (err) => {
        if (settled) return
        // Fall back to non-streaming ask if SSE fails
        try {
          const response = await chatService.ask(request)
          finishWithResponse({
            messageId: response.messageId,
            conversationId: response.conversationId,
            answer: response.answer,
            citations: response.citations,
          })
        } catch (askErr: unknown) {
          set({
            error: getErrorMessage(askErr) || err.message,
            isStreaming: false,
            isSending: false,
            streamingContent: '',
          })
        }
      }
    )
  },

  clearError: () => set({ error: null }),
}))

function getErrorMessage(err: unknown): string {
  if (err && typeof err === 'object' && 'message' in err) {
    return String((err as { message: string }).message)
  }
  return 'An unexpected error occurred'
}
