import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import { BookOpen, Menu } from 'lucide-react'

export default function MainLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="flex flex-col md:flex-row h-screen overflow-hidden bg-background">
      {/* Mobile top bar */}
      <header className="md:hidden flex items-center gap-3 px-4 py-3 border-b border-border bg-card flex-shrink-0">
        <button
          onClick={() => setSidebarOpen(true)}
          className="p-1 -ml-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
          aria-label="Open menu"
        >
          <Menu className="w-5 h-5" />
        </button>
        <div className="w-6 h-6 rounded-md bg-primary flex items-center justify-center">
          <BookOpen className="w-3.5 h-3.5 text-primary-foreground" />
        </div>
        <span className="font-semibold text-sm">Knowledge Assistant</span>
      </header>

      {/* Mobile backdrop */}
      {sidebarOpen && (
        <div
          className="md:hidden fixed inset-0 z-30 bg-black/60 animate-fade-in"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <main className="flex flex-1 min-w-0 min-h-0 overflow-hidden">
        <Outlet />
      </main>
    </div>
  )
}
