import { useEffect, useRef, useState } from 'react'
import { useChatStore } from '@/store/chatStore'
import MessageBubble from '@/components/chat/MessageBubble'
import MessageInput from '@/components/chat/MessageInput'
import CitationPanel from '@/components/chat/CitationPanel'
import type { Citation } from '@/types'
import { BookOpen, PanelRight } from 'lucide-react'
import { cn } from '@/utils/cn'

export default function ChatPage() {
  const {
    messages,
    isSending,
    isStreaming,
    streamingContent,
    activeConversationId,
    sendMessage,
    error,
  } = useChatStore()

  const [activeCitations, setActiveCitations] = useState<Citation[]>([])
  const [showCitations, setShowCitations] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  // Scroll to bottom on new messages or streaming
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, streamingContent])

  // Show citations from the last assistant message
  useEffect(() => {
    const lastAssistant = [...messages].reverse().find((m) => m.role === 'ASSISTANT')
    if (lastAssistant?.citations?.length) {
      setActiveCitations(lastAssistant.citations)
    }
  }, [messages])

  const handleSend = (message: string, searchMode: 'HYBRID' | 'VECTOR' | 'KEYWORD') => {
    sendMessage({
      message,
      conversationId: activeConversationId ?? undefined,
      searchMode,
    })
  }

  return (
    <div className="flex flex-1 h-full overflow-hidden">
      {/* Main chat area */}
      <div className="flex flex-col flex-1 min-w-0">
        {/* Messages */}
        <div className="flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="max-w-3xl mx-auto py-4">
              {messages.map((message) => (
                <MessageBubble key={message.id} message={message} />
              ))}

              {/* Streaming assistant bubble */}
              {isStreaming && (
                <MessageBubble
                  message={{
                    id: 'streaming',
                    conversationId: activeConversationId ?? '',
                    role: 'ASSISTANT',
                    content: streamingContent,
                    createdAt: new Date().toISOString(),
                  }}
                  isStreaming
                  streamingContent={streamingContent}
                />
              )}

              {error && (
                <div className="mx-4 my-2 rounded-md bg-destructive/10 border border-destructive/20 px-4 py-3 text-sm text-destructive">
                  {error}
                </div>
              )}
            </div>
          )}
          <div ref={bottomRef} />
        </div>

        {/* Citation toggle button */}
        {activeCitations.length > 0 && (
          <div className="flex justify-end px-4 py-1 border-t border-border">
            <button
              onClick={() => setShowCitations((v) => !v)}
              className={cn(
                'flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-full border transition-colors',
                showCitations
                  ? 'bg-primary/10 border-primary/20 text-primary'
                  : 'border-border text-muted-foreground hover:text-foreground hover:bg-muted'
              )}
            >
              <PanelRight className="w-3.5 h-3.5" />
              {activeCitations.length} source{activeCitations.length !== 1 ? 's' : ''}
            </button>
          </div>
        )}

        {/* Input */}
        <MessageInput onSend={handleSend} disabled={isSending || isStreaming} />
      </div>

      {/* Citation panel */}
      {showCitations && (
        <CitationPanel
          citations={activeCitations}
          onClose={() => setShowCitations(false)}
        />
      )}
    </div>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center h-full gap-6 text-center px-4">
      <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center">
        <BookOpen className="w-8 h-8 text-primary" />
      </div>
      <div className="space-y-2">
        <h2 className="text-xl font-semibold">Ask your knowledge base</h2>
        <p className="text-muted-foreground max-w-sm">
          Ask any question and the AI will search your indexed documents to find the most relevant
          answer with citations.
        </p>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-w-md w-full">
        {SUGGESTIONS.map((s) => (
          <div
            key={s}
            className="rounded-lg border border-border px-3 py-2 text-sm text-left text-muted-foreground bg-card hover:bg-muted cursor-default transition-colors"
          >
            {s}
          </div>
        ))}
      </div>
    </div>
  )
}

const SUGGESTIONS = [
  'What is our vacation policy?',
  'How do I submit an expense report?',
  'What are the security requirements for passwords?',
  'Where can I find onboarding documents?',
]
