import { useEffect, useState, useCallback } from 'react'
import DocumentUpload from '@/components/documents/DocumentUpload'
import DocumentList from '@/components/documents/DocumentList'
import { documentService } from '@/services/documents'
import type { Document, DocumentStatus, FileType } from '@/types'
import { Search, SlidersHorizontal, Upload, RefreshCw } from 'lucide-react'
import { cn } from '@/utils/cn'

const STATUS_OPTIONS: { value: DocumentStatus | ''; label: string }[] = [
  { value: '', label: 'All statuses' },
  { value: 'INDEXED', label: 'Indexed' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'FAILED', label: 'Failed' },
]

const FILE_TYPE_OPTIONS: { value: FileType | ''; label: string }[] = [
  { value: '', label: 'All types' },
  { value: 'PDF', label: 'PDF' },
  { value: 'DOCX', label: 'DOCX' },
  { value: 'TXT', label: 'TXT' },
  { value: 'MD', label: 'Markdown' },
  { value: 'HTML', label: 'HTML' },
]

export default function DocumentsPage() {
  const [documents, setDocuments] = useState<Document[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [showUpload, setShowUpload] = useState(false)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<DocumentStatus | ''>('')
  const [fileTypeFilter, setFileTypeFilter] = useState<FileType | ''>('')
  const [totalElements, setTotalElements] = useState(0)
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 20

  const load = useCallback(async () => {
    setIsLoading(true)
    try {
      const result = await documentService.getDocuments({
        page,
        size: PAGE_SIZE,
        search: search || undefined,
        status: statusFilter || undefined,
        fileType: fileTypeFilter || undefined,
        sortBy: 'createdAt',
        sortDir: 'desc',
      })
      setDocuments(result.content)
      setTotalElements(result.totalElements)
    } finally {
      setIsLoading(false)
    }
  }, [page, search, statusFilter, fileTypeFilter])

  useEffect(() => {
    const timer = setTimeout(load, search ? 300 : 0)
    return () => clearTimeout(timer)
  }, [load, search])

  const handleUploaded = (doc: Document) => {
    setDocuments((prev) => [doc, ...prev])
    setTotalElements((n) => n + 1)
  }

  const handleDeleted = (id: string) => {
    setDocuments((prev) => prev.filter((d) => d.id !== id))
    setTotalElements((n) => n - 1)
  }

  const handleReindexed = (id: string) => {
    setDocuments((prev) =>
      prev.map((d) => (d.id === id ? { ...d, status: 'PENDING' as DocumentStatus } : d))
    )
  }

  const totalPages = Math.ceil(totalElements / PAGE_SIZE)

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="flex-shrink-0 px-4 sm:px-6 py-4 border-b border-border">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h1 className="text-lg font-semibold">Documents</h1>
            <p className="text-sm text-muted-foreground">
              {totalElements} document{totalElements !== 1 ? 's' : ''} in the knowledge base
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={load}
              className="p-2 rounded-md hover:bg-muted transition-colors text-muted-foreground"
              aria-label="Refresh"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
            <button
              onClick={() => setShowUpload((v) => !v)}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors',
                showUpload
                  ? 'bg-muted text-foreground'
                  : 'bg-primary text-primary-foreground hover:bg-primary/90'
              )}
            >
              <Upload className="w-4 h-4" />
              Upload
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {/* Upload panel */}
        {showUpload && (
          <div className="px-4 sm:px-6 py-4 border-b border-border bg-muted/20">
            <DocumentUpload onUploaded={handleUploaded} />
          </div>
        )}

        {/* Filters */}
        <div className="px-4 sm:px-6 py-3 border-b border-border flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-48">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search documents…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              className="w-full pl-9 pr-3 py-1.5 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            />
          </div>

          <div className="flex items-center gap-2">
            <SlidersHorizontal className="w-4 h-4 text-muted-foreground" />
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as DocumentStatus | '')
                setPage(0)
              }}
              className="rounded-md border border-border bg-background px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            >
              {STATUS_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>

            <select
              value={fileTypeFilter}
              onChange={(e) => {
                setFileTypeFilter(e.target.value as FileType | '')
                setPage(0)
              }}
              className="rounded-md border border-border bg-background px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
            >
              {FILE_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Document list */}
        <DocumentList
          documents={documents}
          isLoading={isLoading}
          onDeleted={handleDeleted}
          onReindexed={handleReindexed}
        />

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 py-4 border-t border-border">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1 rounded-md text-sm border border-border hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <span className="text-sm text-muted-foreground">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1 rounded-md text-sm border border-border hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
