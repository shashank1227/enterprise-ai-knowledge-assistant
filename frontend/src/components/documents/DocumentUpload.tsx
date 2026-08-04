import { useCallback, useState } from 'react'
import { useDropzone } from 'react-dropzone'
import { cn } from '@/utils/cn'
import { documentService } from '@/services/documents'
import type { Document } from '@/types'
import { Upload, X, FileText, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react'
import { formatFileSize } from '@/utils/format'

interface UploadFile {
  file: File
  id: string
  status: 'pending' | 'uploading' | 'done' | 'error'
  progress: number
  error?: string
  result?: Document
}

interface Props {
  onUploaded: (doc: Document) => void
}

const ACCEPTED_TYPES: Record<string, string[]> = {
  'application/pdf': ['.pdf'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
  'text/plain': ['.txt'],
  'text/markdown': ['.md'],
  'text/html': ['.html', '.htm'],
}

export default function DocumentUpload({ onUploaded }: Props) {
  const [files, setFiles] = useState<UploadFile[]>([])

  const onDrop = useCallback((accepted: File[]) => {
    const newFiles: UploadFile[] = accepted.map((file) => ({
      file,
      id: `${file.name}-${Date.now()}`,
      status: 'pending',
      progress: 0,
    }))
    setFiles((prev) => [...prev, ...newFiles])
    newFiles.forEach((uf) => uploadFile(uf))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const uploadFile = async (uf: UploadFile) => {
    setFiles((prev) =>
      prev.map((f) => (f.id === uf.id ? { ...f, status: 'uploading' } : f))
    )
    try {
      const doc = await documentService.uploadDocument(
        uf.file,
        { title: uf.file.name },
        (progress) => {
          setFiles((prev) =>
            prev.map((f) => (f.id === uf.id ? { ...f, progress } : f))
          )
        }
      )
      setFiles((prev) =>
        prev.map((f) =>
          f.id === uf.id ? { ...f, status: 'done', progress: 100, result: doc } : f
        )
      )
      onUploaded(doc)
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'message' in err
          ? String((err as { message: string }).message)
          : 'Upload failed'
      setFiles((prev) =>
        prev.map((f) => (f.id === uf.id ? { ...f, status: 'error', error: message } : f))
      )
    }
  }

  const removeFile = (id: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: ACCEPTED_TYPES,
    maxSize: 50 * 1024 * 1024, // 50 MB
  })

  return (
    <div className="space-y-4">
      {/* Drop zone */}
      <div
        {...getRootProps()}
        className={cn(
          'border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors',
          isDragActive
            ? 'border-primary bg-primary/5'
            : 'border-border hover:border-primary/50 hover:bg-muted/30'
        )}
      >
        <input {...getInputProps()} />
        <Upload
          className={cn(
            'w-8 h-8 mx-auto mb-3 transition-colors',
            isDragActive ? 'text-primary' : 'text-muted-foreground'
          )}
        />
        {isDragActive ? (
          <p className="text-sm font-medium text-primary">Drop files here</p>
        ) : (
          <>
            <p className="text-sm font-medium">Drag &amp; drop files or click to browse</p>
            <p className="text-xs text-muted-foreground mt-1">
              PDF, DOCX, TXT, MD, HTML · Max 50 MB each
            </p>
          </>
        )}
      </div>

      {/* File list */}
      {files.length > 0 && (
        <ul className="space-y-2">
          {files.map((uf) => (
            <li
              key={uf.id}
              className="flex items-center gap-3 rounded-lg border border-border px-3 py-2 bg-card"
            >
              <FileText className="w-4 h-4 text-muted-foreground flex-shrink-0" />

              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{uf.file.name}</p>
                <div className="flex items-center gap-2 mt-0.5">
                  <p className="text-xs text-muted-foreground">{formatFileSize(uf.file.size)}</p>
                  {uf.status === 'uploading' && (
                    <div className="flex-1 h-1 rounded-full bg-muted overflow-hidden">
                      <div
                        className="h-full bg-primary rounded-full transition-all duration-300"
                        style={{ width: `${uf.progress}%` }}
                      />
                    </div>
                  )}
                  {uf.status === 'error' && (
                    <p className="text-xs text-destructive truncate">{uf.error}</p>
                  )}
                  {uf.status === 'done' && (
                    <p className="text-xs text-emerald-400">Processing…</p>
                  )}
                </div>
              </div>

              {/* Status icon */}
              <div className="flex-shrink-0">
                {uf.status === 'uploading' && (
                  <Loader2 className="w-4 h-4 animate-spin text-primary" />
                )}
                {uf.status === 'done' && (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                )}
                {uf.status === 'error' && (
                  <AlertCircle className="w-4 h-4 text-destructive" />
                )}
                {uf.status === 'pending' && (
                  <button
                    onClick={() => removeFile(uf.id)}
                    className="p-0.5 rounded hover:bg-muted text-muted-foreground"
                    aria-label="Remove"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
