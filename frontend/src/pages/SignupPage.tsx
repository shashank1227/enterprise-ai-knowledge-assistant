import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/utils/cn'
import { BookOpen, Loader2 } from 'lucide-react'

interface FormState {
  email: string
  password: string
  confirmPassword: string
  fullName: string
  department: string
  jobTitle: string
}

export default function SignupPage() {
  const navigate = useNavigate()
  const { signup, isLoading, error, clearError } = useAuthStore()

  const [form, setForm] = useState<FormState>({
    email: '',
    password: '',
    confirmPassword: '',
    fullName: '',
    department: '',
    jobTitle: '',
  })
  const [localError, setLocalError] = useState<string | null>(null)

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    clearError()
    setLocalError(null)
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (form.password !== form.confirmPassword) {
      setLocalError('Passwords do not match')
      return
    }
    if (form.password.length < 8) {
      setLocalError('Password must be at least 8 characters')
      return
    }
    try {
      await signup({
        email: form.email,
        password: form.password,
        fullName: form.fullName,
        department: form.department || undefined,
        jobTitle: form.jobTitle || undefined,
      })
      navigate('/chat', { replace: true })
    } catch {
      // error already set in store
    }
  }

  const displayError = localError ?? error

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 py-12">
      <div className="w-full max-w-sm space-y-8">
        {/* Logo */}
        <div className="flex flex-col items-center gap-2">
          <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-primary text-primary-foreground">
            <BookOpen className="w-6 h-6" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">Create account</h1>
          <p className="text-sm text-muted-foreground">Join Knowledge Assistant</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {displayError && (
            <div className="rounded-md bg-destructive/10 border border-destructive/20 px-4 py-3 text-sm text-destructive">
              {displayError}
            </div>
          )}

          <Field
            id="fullName"
            label="Full name"
            type="text"
            placeholder="Jane Smith"
            value={form.fullName}
            onChange={handleChange}
            disabled={isLoading}
            required
          />
          <Field
            id="email"
            label="Work email"
            type="email"
            placeholder="jane@company.com"
            value={form.email}
            onChange={handleChange}
            disabled={isLoading}
            required
          />

          <div className="grid grid-cols-2 gap-3">
            <Field
              id="department"
              label="Department"
              type="text"
              placeholder="Engineering"
              value={form.department}
              onChange={handleChange}
              disabled={isLoading}
            />
            <Field
              id="jobTitle"
              label="Job title"
              type="text"
              placeholder="Engineer"
              value={form.jobTitle}
              onChange={handleChange}
              disabled={isLoading}
            />
          </div>

          <Field
            id="password"
            label="Password"
            type="password"
            placeholder="Min. 8 characters"
            value={form.password}
            onChange={handleChange}
            disabled={isLoading}
            required
          />
          <Field
            id="confirmPassword"
            label="Confirm password"
            type="password"
            placeholder="••••••••"
            value={form.confirmPassword}
            onChange={handleChange}
            disabled={isLoading}
            required
          />

          <button
            type="submit"
            disabled={isLoading}
            className={cn(
              'w-full flex items-center justify-center gap-2 rounded-md',
              'bg-primary text-primary-foreground px-4 py-2 text-sm font-medium',
              'hover:bg-primary/90 transition-colors',
              'disabled:opacity-50 disabled:cursor-not-allowed'
            )}
          >
            {isLoading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Creating account…
              </>
            ) : (
              'Create account'
            )}
          </button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}

// Small reusable field component local to this file
interface FieldProps {
  id: string
  label: string
  type: string
  placeholder?: string
  value: string
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void
  disabled?: boolean
  required?: boolean
}

function Field({ id, label, type, placeholder, value, onChange, disabled, required }: FieldProps) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        name={id}
        type={type}
        placeholder={placeholder}
        value={value}
        onChange={onChange}
        disabled={disabled}
        required={required}
        className={cn(
          'w-full rounded-md border bg-background px-3 py-2 text-sm',
          'placeholder:text-muted-foreground',
          'focus:outline-none focus:ring-2 focus:ring-ring focus:border-transparent',
          'disabled:opacity-50'
        )}
      />
    </div>
  )
}
