import { cn } from '@/utils/cn'
import type { Document, DocumentStatus } from '@/types'
import { formatFileSize, formatRelativeTime } from '@/utils/format'
import {
  FileText,
  FileCode,
  FileType,
  MoreVertical,
  Trash2,
  RefreshCw,
  Download,
  Loader2,
} from 'lucide-react'
import { useState } from 'react'
import { documentService } from '@/services/documents'

interface Props {
  documents: Document[]
  isLoading: boolean
  onDeleted: (id: string) => void
  onReindexed: (id: string) => void
}

const STATUS_CONFIG: Record<
  DocumentStatus,
  { label: string; className: string; dot: string }
> = {
  PENDING: {
    label: 'Pending',
    className: 'bg-yellow-500/10 text-yellow-600 border-yellow-500/20',
    dot: 'bg-yellow-500',
  },
  PROCESSING: {
    label: 'Processing',
    className: 'bg-blue-500/10 text-blue-600 border-blue-500/20',
    dot: 'bg-blue-500 animate-pulse',
  },
  INDEXED: {
    label: 'Indexed',
    className: 'bg-green-500/10 text-green-600 border-green-500/20',
    dot: 'bg-green-500',
  },
  FAILED: {
    label: 'Failed',
    className: 'bg-destructive/10 text-destructive border-destructive/20',
    dot: 'bg-destructive',
  },
}

function FileIcon({ fileType }: { fileType: string }) {
  if (fileType === 'PDF') return <FileText className="w-5 h-5 text-red-500" />
  if (fileType === 'DOCX') return <FileText className="w-5 h-5 text-blue-500" />
  if (fileType === 'MD' || fileType === 'TXT') return <FileCode className="w-5 h-5 text-gray-500" />
  return <FileType className="w-5 h-5 text-muted-foreground" />
}

export default function DocumentList({ documents, isLoading, onDeleted, onReindexed }: Props) {
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [openMenu, setOpenMenu] = useState<string | null>(null)

  const handleDelete = async (id: string) => {
    setActionLoading(id)
    setOpenMenu(null)
    try {
      await documentService.deleteDocument(id)
      onDeleted(id)
    } finally {
      setActionLoading(null)
    }
  }

  const handleReindex = async (id: string) => {
    setActionLoading(id)
    setOpenMenu(null)
    try {
      await documentService.reindexDocument(id)
      onReindexed(id)
    } finally {
      setActionLoading(null)
    }
  }

  const handleDownload = async (id: string) => {
    setOpenMenu(null)
    const url = await documentService.getDownloadUrl(id)
    window.open(url, '_blank')
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (documents.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 gap-3 text-center">
        <FileText className="w-10 h-10 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">No documents yet. Upload one to get started.</p>
      </div>
    )
  }

  return (
    <div className="divide-y divide-border">
      {documents.map((doc) => {
        const status = STATUS_CONFIG[doc.status]
        return (
          <div
            key={doc.id}
            className="flex items-center gap-3 px-4 py-3 hover:bg-muted/30 transition-colors"
          >
            <FileIcon fileType={doc.fileType} />

            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate">{doc.title}</p>
              <div className="flex items-center gap-2 mt-0.5 text-xs text-muted-foreground">
                <span>{formatFileSize(doc.fileSizeBytes)}</span>
                {doc.pageCount && <span>· {doc.pageCount} pages</span>}
                {doc.tokenCount && <span>· {doc.tokenCount.toLocaleString()} tokens</span>}
                <span>· {formatRelativeTime(doc.createdAt)}</span>
              </div>
              {doc.processingError && (
                <p className="text-xs text-destructive mt-0.5 truncate">{doc.processingError}</p>
              )}
            </div>

            {/* Tags */}
            {doc.tags && doc.tags.length > 0 && (
              <div className="hidden sm:flex items-center gap-1">
                {doc.tags.slice(0, 2).map((tag) => (
                  <span
                    key={tag}
                    className="px-1.5 py-0.5 rounded bg-muted text-muted-foreground text-xs"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            )}

            {/* Status badge */}
            <span
              className={cn(
                'hidden sm:flex items-center gap-1.5 px-2 py-0.5 rounded-full border text-xs font-medium',
                status.className
              )}
            >
              <span className={cn('w-1.5 h-1.5 rounded-full', status.dot)} />
              {status.label}
            </span>

            {/* Actions menu */}
            <div className="relative">
              {actionLoading === doc.id ? (
                <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />
              ) : (
                <button
                  onClick={() => setOpenMenu(openMenu === doc.id ? null : doc.id)}
                  className="p-1 rounded hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                  aria-label="Document actions"
                >
                  <MoreVertical className="w-4 h-4" />
                </button>
              )}

              {openMenu === doc.id && (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    onClick={() => setOpenMenu(null)}
                  />
                  <div className="absolute right-0 top-8 z-20 w-44 rounded-lg border border-border bg-card shadow-lg py-1">
                    <button
                      onClick={() => handleDownload(doc.id)}
                      className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-muted transition-colors"
                    >
                      <Download className="w-4 h-4" />
                      Download
                    </button>
                    <button
                      onClick={() => handleReindex(doc.id)}
                      className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-muted transition-colors"
                    >
                      <RefreshCw className="w-4 h-4" />
                      Re-index
                    </button>
                    <div className="my-1 border-t border-border" />
                    <button
                      onClick={() => handleDelete(doc.id)}
                      className="w-full flex items-center gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive/10 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                      Delete
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
