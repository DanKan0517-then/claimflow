import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Button, ErrorBanner, Field } from '../components/ui'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import AuthShell from './AuthShell'

export default function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/" replace />
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors({})
    try {
      await register({ ...form, phone: form.phone || undefined })
      navigate('/claims')
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setError(err instanceof Error ? err.message : 'Sign up failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your account" subtitle="For policy holders. Staff accounts are created by an admin.">
      <form onSubmit={submit} className="space-y-4">
        <ErrorBanner message={error} />
        <Field label="Full name" error={fieldErrors.fullName}>
          <input className="input" required value={form.fullName} onChange={set('fullName')} autoComplete="name" />
        </Field>
        <Field label="Email" error={fieldErrors.email}>
          <input className="input" type="email" required value={form.email} onChange={set('email')} autoComplete="email" />
        </Field>
        <Field label="Phone (optional)" error={fieldErrors.phone}>
          <input className="input" value={form.phone} onChange={set('phone')} autoComplete="tel" placeholder="+91 98765 43210" />
        </Field>
        <Field label="Password" error={fieldErrors.password} hint="At least 8 characters">
          <input className="input" type="password" required minLength={8} value={form.password} onChange={set('password')} autoComplete="new-password" />
        </Field>
        <Button type="submit" className="w-full" loading={busy}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-600 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthShell>
  )
}
