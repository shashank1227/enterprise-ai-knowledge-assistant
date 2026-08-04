import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useChatStore } from '@/store/chatStore'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/utils/cn'
import { formatConversationDate } from '@/utils/format'
import {
  BookOpen,
  MessageSquare,
  Files,
  Pin,
  Trash2,
  Plus,
  LogOut,
  User,
  ChevronRight,
  Loader2,
} from 'lucide-react'
import { useEffect, useState } from 'react'

interface SidebarProps {
  isOpen: boolean
  onClose: () => void
}

export default function Sidebar({ isOpen, onClose }: SidebarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()
  const {
    conversations,
    activeConversationId,
    selectConversation,
    createConversation,
    deleteConversation,
    togglePin,
    loadConversations,
    isLoading,
  } = useChatStore()

  const [creating, setCreating] = useState(false)

  useEffect(() => {
    loadConversations()
  }, [loadConversations])

  const handleNewChat = async () => {
    setCreating(true)
    try {
      const conv = await createConversation()
      selectConversation(conv.id)
      navigate('/chat')
      onClose()
    } finally {
      setCreating(false)
    }
  }

  const handleSelectConversation = (id: string) => {
    selectConversation(id)
    navigate('/chat')
    onClose()
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const isChat = location.pathname === '/chat'
  const isDocs = location.pathname === '/documents'

  const pinned = conversations.filter((c) => c.isPinned)
  const recent = conversations.filter((c) => !c.isPinned)

  return (
    <aside
      className={cn(
        'fixed inset-y-0 left-0 z-40 w-64 flex flex-col h-full bg-card border-r border-border',
        'transform transition-transform duration-200 ease-out',
        'md:static md:z-auto md:flex-shrink-0 md:translate-x-0',
        isOpen ? 'translate-x-0' : '-translate-x-full'
      )}
    >
      {/* Logo */}
      <div className="flex items-center gap-2 px-4 py-4 border-b border-border">
        <div className="w-7 h-7 rounded-lg bg-primary flex items-center justify-center">
          <BookOpen className="w-4 h-4 text-primary-foreground" />
        </div>
        <span className="font-semibold text-sm">Knowledge Assistant</span>
      </div>

      {/* Nav */}
      <nav className="px-2 pt-3 space-y-1">
        <button
          onClick={handleNewChat}
          disabled={creating}
          className={cn(
            'w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors',
            'bg-primary/10 text-primary hover:bg-primary/20'
          )}
        >
          {creating ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <Plus className="w-4 h-4" />
          )}
          New chat
        </button>

        <Link
          to="/chat"
          onClick={onClose}
          className={cn(
            'flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors',
            isChat
              ? 'bg-muted text-foreground font-medium'
              : 'text-muted-foreground hover:text-foreground hover:bg-muted'
          )}
        >
          <MessageSquare className="w-4 h-4" />
          Chat
        </Link>

        <Link
          to="/documents"
          onClick={onClose}
          className={cn(
            'flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors',
            isDocs
              ? 'bg-muted text-foreground font-medium'
              : 'text-muted-foreground hover:text-foreground hover:bg-muted'
          )}
        >
          <Files className="w-4 h-4" />
          Documents
        </Link>
      </nav>

      {/* Conversations */}
      <div className="flex-1 overflow-y-auto px-2 pt-4 pb-2">
        {isLoading ? (
          <div className="flex justify-center py-6">
            <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            {pinned.length > 0 && (
              <ConversationGroup
                label="Pinned"
                items={pinned}
                activeId={activeConversationId}
                onSelect={handleSelectConversation}
                onTogglePin={togglePin}
                onDelete={deleteConversation}
              />
            )}
            {recent.length > 0 && (
              <ConversationGroup
                label="Recent"
                items={recent}
                activeId={activeConversationId}
                onSelect={handleSelectConversation}
                onTogglePin={togglePin}
                onDelete={deleteConversation}
              />
            )}
            {conversations.length === 0 && (
              <p className="text-xs text-muted-foreground text-center py-4">
                No conversations yet
              </p>
            )}
          </>
        )}
      </div>

      {/* User footer */}
      <div className="border-t border-border px-2 py-3">
        <div className="flex items-center gap-2 px-2 py-2 rounded-md hover:bg-muted transition-colors group">
          <div className="w-7 h-7 rounded-full bg-muted flex items-center justify-center flex-shrink-0">
            <User className="w-4 h-4 text-muted-foreground" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-medium truncate">{user?.fullName}</p>
            <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
          </div>
          <button
            onClick={handleLogout}
            className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-muted text-muted-foreground transition-opacity"
            aria-label="Sign out"
          >
            <LogOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </aside>
  )
}

interface ConversationGroupProps {
  label: string
  items: ReturnType<typeof useChatStore.getState>['conversations']
  activeId: string | null
  onSelect: (id: string) => void
  onTogglePin: (id: string) => void
  onDelete: (id: string) => Promise<void>
}

function ConversationGroup({
  label,
  items,
  activeId,
  onSelect,
  onTogglePin,
  onDelete,
}: ConversationGroupProps) {
  const [hoveredId, setHoveredId] = useState<string | null>(null)

  return (
    <div className="mb-3">
      <p className="px-3 py-1 text-xs font-medium text-muted-foreground uppercase tracking-wider">
        {label}
      </p>
      <ul className="space-y-0.5">
        {items.map((conv) => (
          <li
            key={conv.id}
            onMouseEnter={() => setHoveredId(conv.id)}
            onMouseLeave={() => setHoveredId(null)}
          >
            <button
              onClick={() => onSelect(conv.id)}
              className={cn(
                'w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors group text-left',
                activeId === conv.id
                  ? 'bg-muted text-foreground'
                  : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              )}
            >
              <ChevronRight
                className={cn(
                  'w-3 h-3 flex-shrink-0 transition-transform',
                  activeId === conv.id && 'rotate-90'
                )}
              />
              <span className="flex-1 truncate text-xs">
                {conv.title || 'Untitled conversation'}
              </span>

              {hoveredId === conv.id ? (
                <span className="flex items-center gap-0.5">
                  <span
                    role="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onTogglePin(conv.id)
                    }}
                    className="p-0.5 rounded hover:bg-muted"
                    aria-label={conv.isPinned ? 'Unpin' : 'Pin'}
                  >
                    <Pin
                      className={cn(
                        'w-3 h-3',
                        conv.isPinned ? 'text-primary fill-primary' : 'text-muted-foreground'
                      )}
                    />
                  </span>
                  <span
                    role="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onDelete(conv.id)
                    }}
                    className="p-0.5 rounded hover:bg-destructive/10 text-muted-foreground hover:text-destructive"
                    aria-label="Delete"
                  >
                    <Trash2 className="w-3 h-3" />
                  </span>
                </span>
              ) : (
                conv.lastMessageAt && (
                  <span className="text-xs text-muted-foreground flex-shrink-0">
                    {formatConversationDate(conv.lastMessageAt)}
                  </span>
                )
              )}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
