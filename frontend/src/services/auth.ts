import { apiClient, setTokens, clearTokens } from './api'
import type { AuthResponse, LoginRequest, SignupRequest, UserProfile } from '@/types'

export const authService = {
  async login(request: LoginRequest): Promise<AuthResponse> {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', request)
    setTokens(data.accessToken, data.refreshToken)
    return data
  },

  async signup(request: SignupRequest): Promise<AuthResponse> {
    const { data } = await apiClient.post<AuthResponse>('/auth/signup', request)
    setTokens(data.accessToken, data.refreshToken)
    return data
  },

  async logout(refreshToken: string): Promise<void> {
    try {
      await apiClient.post('/auth/logout', { refreshToken })
    } finally {
      clearTokens()
    }
  },

  async getMe(): Promise<UserProfile> {
    const { data } = await apiClient.get<UserProfile>('/auth/me')
    return data
  },
}
