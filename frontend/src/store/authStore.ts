import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { authService } from '@/services/auth'
import { clearTokens, getRefreshToken } from '@/services/api'
import type { LoginRequest, SignupRequest, UserProfile } from '@/types'

interface AuthState {
  user: UserProfile | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null

  login: (request: LoginRequest) => Promise<void>
  signup: (request: SignupRequest) => Promise<void>
  logout: () => Promise<void>
  loadUser: () => Promise<void>
  clearError: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      login: async (request) => {
        set({ isLoading: true, error: null })
        try {
          const response = await authService.login(request)
          set({ user: response.user, isAuthenticated: true, isLoading: false })
        } catch (err: unknown) {
          const message = getErrorMessage(err)
          set({ error: message, isLoading: false })
          throw err
        }
      },

      signup: async (request) => {
        set({ isLoading: true, error: null })
        try {
          const response = await authService.signup(request)
          set({ user: response.user, isAuthenticated: true, isLoading: false })
        } catch (err: unknown) {
          const message = getErrorMessage(err)
          set({ error: message, isLoading: false })
          throw err
        }
      },

      logout: async () => {
        const refreshToken = getRefreshToken()
        try {
          if (refreshToken) {
            await authService.logout(refreshToken)
          }
        } finally {
          clearTokens()
          set({ user: null, isAuthenticated: false, error: null })
        }
      },

      loadUser: async () => {
        if (!get().isAuthenticated) return
        try {
          const user = await authService.getMe()
          set({ user })
        } catch {
          // Token likely expired — clear auth state
          clearTokens()
          set({ user: null, isAuthenticated: false })
        }
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'ka-auth',
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)

function getErrorMessage(err: unknown): string {
  if (err && typeof err === 'object' && 'message' in err) {
    return String((err as { message: string }).message)
  }
  return 'An unexpected error occurred'
}
