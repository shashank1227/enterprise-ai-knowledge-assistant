import { apiClient } from './api'
import type { Document, DocumentStatus, FileType, IngestionStats, Page } from '@/types'

export const documentService = {
  async getDocuments(params?: {
    page?: number
    size?: number
    status?: DocumentStatus
    fileType?: FileType
    search?: string
    sortBy?: string
    sortDir?: 'asc' | 'desc'
  }): Promise<Page<Document>> {
    const { data } = await apiClient.get<Page<Document>>('/documents', { params })
    return data
  },

  async getDocument(id: string): Promise<Document> {
    const { data } = await apiClient.get<Document>(`/documents/${id}`)
    return data
  },

  async uploadDocument(
    file: File,
    options?: {
      title?: string
      description?: string
      tags?: string[]
      category?: string
    },
    onProgress?: (percent: number) => void
  ): Promise<Document> {
    const formData = new FormData()
    formData.append('file', file)
    if (options?.title) formData.append('title', options.title)
    if (options?.description) formData.append('description', options.description)
    if (options?.tags?.length) {
      options.tags.forEach((t) => formData.append('tags', t))
    }
    if (options?.category) formData.append('category', options.category)

    const { data } = await apiClient.post<Document>('/documents', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (event.total && onProgress) {
          onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
    return data
  },

  async uploadDocuments(
    files: File[],
    options?: { tags?: string[]; category?: string }
  ): Promise<{ accepted: number; rejected: number; documents: Document[]; errors: string[] }> {
    const formData = new FormData()
    files.forEach((f) => formData.append('files', f))
    if (options?.tags?.length) options.tags.forEach((t) => formData.append('tags', t))
    if (options?.category) formData.append('category', options.category)

    const { data } = await apiClient.post('/documents/bulk', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async deleteDocument(id: string): Promise<void> {
    await apiClient.delete(`/documents/${id}`)
  },

  async reindexDocument(id: string): Promise<void> {
    await apiClient.post(`/documents/${id}/reindex`)
  },

  async getDownloadUrl(id: string): Promise<string> {
    const { data } = await apiClient.get<{ downloadUrl: string; expiresAt: string }>(
      `/documents/${id}/download`
    )
    return data.downloadUrl
  },

  async getStats(): Promise<IngestionStats> {
    const { data } = await apiClient.get<IngestionStats>('/documents/stats')
    return data
  },

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
  },
}
