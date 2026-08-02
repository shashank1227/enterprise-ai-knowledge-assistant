import { formatDistanceToNow, format, isToday, isYesterday } from 'date-fns'

export function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr)
  return formatDistanceToNow(date, { addSuffix: true })
}

export function formatConversationDate(dateStr: string): string {
  const date = new Date(dateStr)
  if (isToday(date)) return format(date, 'HH:mm')
  if (isYesterday(date)) return 'Yesterday'
  return format(date, 'MMM d')
}

export function formatFullDate(dateStr: string): string {
  return format(new Date(dateStr), 'MMM d, yyyy HH:mm')
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}
