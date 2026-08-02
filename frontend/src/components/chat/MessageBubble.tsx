import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { cn } from '@/utils/cn'
import { formatRelativeTime } from '@/utils/format'
import type { Message } from '@/types'
import { ThumbsUp, ThumbsDown, Copy, Check } from 'lucide-react'
import { useState } from 'react'
import { chatService } from '@/services/chat'

interface Props {
  message: Message
  isStreaming?: boolean
  streamingContent?: string
}

export default function MessageBubble({ message, isStreaming, streamingContent }: Props) {
  const isUser = message.role === 'USER'
  const [copied, setCopied] = useState(false)
  const [feedback, setFeedback] = useState<'up' | 'down' | null>(null)

  const content = isStreaming ? streamingContent ?? '' : message.content

  const handleCopy = () => {
    navigator.clipboard.writeText(content)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleFeedback = async (type: 'up' | 'down') => {
    if (feedback) return
    setFeedback(type)
    await chatService.submitFeedback(message.id, {
      rating: type === 'up' ? 5 : 1,
      feedbackType: type === 'up' ? 'HELPFUL' : 'NOT_HELPFUL',
    })
  }

  return (
    <div className={cn('group flex gap-3 px-4 py-3', isUser && 'flex-row-reverse')}>
      {/* Avatar */}
      <div
        className={cn(
          'flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-xs font-semibold',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
        )}
      >
        {isUser ? 'U' : 'AI'}
      </div>

      {/* Bubble */}
      <div className={cn('flex flex-col gap-1 max-w-[80%]', isUser && 'items-end')}>
        <div
          className={cn(
            'rounded-2xl px-4 py-3 text-sm leading-relaxed',
            isUser
              ? 'bg-primary text-primary-foreground rounded-tr-sm'
              : 'bg-card border border-border rounded-tl-sm'
          )}
        >
          {isUser ? (
            <p className="whitespace-pre-wrap">{content}</p>
          ) : (
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                code({ className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className ?? '')
                  const isBlock = match !== null
                  return isBlock ? (
                    <SyntaxHighlighter
                      style={oneDark}
                      language={match[1]}
                      PreTag="div"
                      className="rounded-md text-xs my-2"
                    >
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code
                      className="bg-muted px-1 py-0.5 rounded text-xs font-mono"
                      {...props}
                    >
                      {children}
                    </code>
                  )
                },
                p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                ul: ({ children }) => (
                  <ul className="list-disc list-inside mb-2 space-y-1">{children}</ul>
                ),
                ol: ({ children }) => (
                  <ol className="list-decimal list-inside mb-2 space-y-1">{children}</ol>
                ),
                blockquote: ({ children }) => (
                  <blockquote className="border-l-2 border-muted-foreground pl-3 italic text-muted-foreground">
                    {children}
                  </blockquote>
                ),
              }}
            >
              {content}
            </ReactMarkdown>
          )}

          {/* Streaming cursor */}
          {isStreaming && (
            <span className="inline-block w-2 h-4 bg-current animate-pulse ml-0.5 align-middle" />
          )}
        </div>

        {/* Citations */}
        {!isUser && message.citations && message.citations.length > 0 && !isStreaming && (
          <div className="flex flex-wrap gap-1 mt-1">
            {message.citations.map((cite) => (
              <span
                key={cite.chunkId}
                title={cite.excerpt}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-accent text-accent-foreground text-xs cursor-default hover:bg-accent/80 transition-colors"
              >
                <span className="font-medium">[{cite.index}]</span>
                <span className="truncate max-w-[140px]">{cite.documentTitle}</span>
                {cite.pageNumber && (
                  <span className="text-muted-foreground">p.{cite.pageNumber}</span>
                )}
              </span>
            ))}
          </div>
        )}

        {/* Actions row */}
        {!isUser && !isStreaming && (
          <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              onClick={handleCopy}
              className="p-1 rounded hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
              aria-label="Copy response"
            >
              {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
            <button
              onClick={() => handleFeedback('up')}
              className={cn(
                'p-1 rounded hover:bg-muted transition-colors',
                feedback === 'up'
                  ? 'text-green-600'
                  : 'text-muted-foreground hover:text-foreground'
              )}
              aria-label="Helpful"
            >
              <ThumbsUp className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => handleFeedback('down')}
              className={cn(
                'p-1 rounded hover:bg-muted transition-colors',
                feedback === 'down'
                  ? 'text-red-600'
                  : 'text-muted-foreground hover:text-foreground'
              )}
              aria-label="Not helpful"
            >
              <ThumbsDown className="w-3.5 h-3.5" />
            </button>
            {message.createdAt && (
              <span className="text-xs text-muted-foreground ml-1">
                {formatRelativeTime(message.createdAt)}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
