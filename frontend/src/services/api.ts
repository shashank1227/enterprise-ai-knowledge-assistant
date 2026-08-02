import axios, { AxiosError, AxiosInstance } from 'axios'
import type { ApiError } from '@/types'

const BASE_URL = import.meta.env.VITE_API_URL ?? '/api/v1'

// Singleton axios instance
export const apiClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 60_000,
})

// ── Request interceptor: attach access token ──────────────

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// ── Response interceptor: handle 401 → token refresh ─────

let isRefreshing = false
let failedQueue: Array<{
  resolve: (value: string) => void
  reject: (reason?: unknown) => void
}> = []

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token!)
    }
  })
  failedQueue = []
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as typeof error.config & { _retry?: boolean }

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then((token) => {
            originalRequest!.headers!.Authorization = `Bearer ${token}`
            return apiClient(originalRequest!)
          })
          .catch((err) => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        clearTokens()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      try {
        const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken })
        setTokens(data.accessToken, data.refreshToken)
        processQueue(null, data.accessToken)
        originalRequest!.headers!.Authorization = `Bearer ${data.accessToken}`
        return apiClient(originalRequest!)
      } catch (refreshError) {
        processQueue(refreshError, null)
        clearTokens()
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(normalizeError(error))
  }
)

// ── Token helpers (localStorage) ──────────────────────────

const ACCESS_TOKEN_KEY = 'ka_access_token'
const REFRESH_TOKEN_KEY = 'ka_refresh_token'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

// ── Error normalisation ────────────────────────────────────

function normalizeError(error: AxiosError): ApiError {
  const data = error.response?.data as Partial<ApiError> | undefined
  return {
    status: error.response?.status ?? 0,
    error: data?.error ?? 'Unknown error',
    message: data?.message ?? error.message ?? 'An unexpected error occurred',
    timestamp: data?.timestamp ?? new Date().toISOString(),
    path: data?.path,
  }
}
