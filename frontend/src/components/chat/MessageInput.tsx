import { useState, useRef, useEffect, KeyboardEvent } from 'react'
import { cn } from '@/utils/cn'
import { Send, Loader2, ChevronDown } from 'lucide-react'

interface Props {
  onSend: (message: string, searchMode: 'HYBRID' | 'VECTOR' | 'KEYWORD') => void
  disabled?: boolean
  placeholder?: string
}

const SEARCH_MODES = [
  { value: 'HYBRID', label: 'Hybrid', description: 'Vector + keyword search' },
  { value: 'VECTOR', label: 'Vector', description: 'Semantic similarity only' },
  { value: 'KEYWORD', label: 'Keyword', description: 'Full-text search only' },
] as const

export default function MessageInput({ onSend, disabled, placeholder }: Props) {
  const [message, setMessage] = useState('')
  const [searchMode, setSearchMode] = useState<'HYBRID' | 'VECTOR' | 'KEYWORD'>('HYBRID')
  const [showModes, setShowModes] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Auto-resize textarea
  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  }, [message])

  const handleSend = () => {
    const trimmed = message.trim()
    if (!trimmed || disabled) return
    onSend(trimmed, searchMode)
    setMessage('')
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const selectedMode = SEARCH_MODES.find((m) => m.value === searchMode)!

  return (
    <div className="border-t border-border bg-background px-4 py-3">
      <div className="max-w-3xl mx-auto">
        <div className="flex items-end gap-2 rounded-xl border border-border bg-card px-3 py-2 shadow-sm focus-within:ring-2 focus-within:ring-ring focus-within:border-transparent transition-shadow">
          <textarea
            ref={textareaRef}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder ?? 'Ask a question about your documents…'}
            disabled={disabled}
            rows={1}
            className={cn(
              'flex-1 resize-none bg-transparent text-sm outline-none',
              'placeholder:text-muted-foreground min-h-[24px] max-h-[200px]',
              'disabled:opacity-50'
            )}
            aria-label="Message input"
          />

          <div className="flex items-center gap-1 flex-shrink-0">
            {/* Search mode selector */}
            <div className="relative">
              <button
                onClick={() => setShowModes((v) => !v)}
                className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                aria-label="Search mode"
              >
                {selectedMode.label}
                <ChevronDown className="w-3 h-3" />
              </button>

              {showModes && (
                <div className="absolute bottom-full right-0 mb-2 w-48 rounded-lg border border-border bg-card shadow-lg z-10 py-1">
                  {SEARCH_MODES.map((mode) => (
                    <button
                      key={mode.value}
                      onClick={() => {
                        setSearchMode(mode.value)
                        setShowModes(false)
                      }}
                      className={cn(
                        'w-full text-left px-3 py-2 text-sm hover:bg-muted transition-colors',
                        searchMode === mode.value && 'text-primary font-medium'
                      )}
                    >
                      <div>{mode.label}</div>
                      <div className="text-xs text-muted-foreground">{mode.description}</div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Send button */}
            <button
              onClick={handleSend}
              disabled={!message.trim() || disabled}
              className={cn(
                'flex items-center justify-center w-8 h-8 rounded-lg transition-colors',
                message.trim() && !disabled
                  ? 'bg-primary text-primary-foreground hover:bg-primary/90'
                  : 'bg-muted text-muted-foreground cursor-not-allowed'
              )}
              aria-label="Send message"
            >
              {disabled ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>

        <p className="text-center text-xs text-muted-foreground mt-2">
          Press <kbd className="px-1 py-0.5 rounded bg-muted font-mono text-xs">Enter</kbd> to send
          &nbsp;·&nbsp;
          <kbd className="px-1 py-0.5 rounded bg-muted font-mono text-xs">Shift+Enter</kbd> for
          new line
        </p>
      </div>
    </div>
  )
}
