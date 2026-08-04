import { cn } from '@/utils/cn'
import type { Citation } from '@/types'
import { FileText, X } from 'lucide-react'

interface Props {
  citations: Citation[]
  onClose: () => void
}

export default function CitationPanel({ citations, onClose }: Props) {
  if (citations.length === 0) return null

  return (
    <>
      {/* Backdrop on small screens where the panel overlays the chat */}
      <div
        className="lg:hidden fixed inset-0 z-30 bg-black/60 animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <aside
        className={cn(
          'fixed inset-y-0 right-0 z-40 w-80 max-w-[85vw] shadow-2xl',
          'border-l border-border bg-card flex flex-col h-full',
          'lg:static lg:z-auto lg:w-80 lg:max-w-none lg:flex-shrink-0 lg:shadow-none'
        )}
      >
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <h2 className="text-sm font-semibold">Sources ({citations.length})</h2>
        <button
          onClick={onClose}
          className="p-1 rounded hover:bg-muted transition-colors text-muted-foreground"
          aria-label="Close sources panel"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {citations.map((cite) => (
          <div
            key={cite.chunkId}
            className="rounded-lg border border-border p-3 space-y-2 hover:bg-muted/50 transition-colors"
          >
            <div className="flex items-start gap-2">
              <span className="flex-shrink-0 w-5 h-5 rounded-full bg-primary/10 text-primary text-xs font-semibold flex items-center justify-center">
                {cite.index}
              </span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1">
                  <FileText className="w-3.5 h-3.5 text-muted-foreground flex-shrink-0" />
                  <span className="text-xs font-medium truncate">{cite.documentTitle}</span>
                </div>
                {(cite.sectionTitle || cite.pageNumber) && (
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {cite.sectionTitle && <span>{cite.sectionTitle}</span>}
                    {cite.sectionTitle && cite.pageNumber && <span> · </span>}
                    {cite.pageNumber && <span>Page {cite.pageNumber}</span>}
                  </p>
                )}
              </div>
            </div>

            {cite.excerpt && (
              <p className="text-xs text-muted-foreground leading-relaxed line-clamp-4 italic border-l-2 border-muted pl-2">
                "{cite.excerpt}"
              </p>
            )}

            <div className="flex items-center justify-between">
              <RelevanceBar score={cite.relevanceScore} />
            </div>
          </div>
        ))}
      </div>
      </aside>
    </>
  )
}

function RelevanceBar({ score }: { score: number }) {
  const pct = Math.round(score * 100)
  const color =
    pct >= 70 ? 'bg-emerald-400' : pct >= 40 ? 'bg-amber-400' : 'bg-muted-foreground'

  return (
    <div className="flex items-center gap-2 w-full">
      <div className="flex-1 h-1 rounded-full bg-muted overflow-hidden">
        <div
          className={cn('h-full rounded-full transition-all', color)}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="text-xs text-muted-foreground whitespace-nowrap">{pct}% match</span>
    </div>
  )
}
