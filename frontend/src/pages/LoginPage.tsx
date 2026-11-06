import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Button, ErrorBanner, Field } from '../components/ui'
import { isStaff, useAuth } from '../lib/auth'
import AuthShell from './AuthShell'

const DEMO = [
  { label: 'Customer', email: 'customer@claimflow.dev' },
  { label: 'Adjuster', email: 'adjuster@claimflow.dev' },
  { label: 'Manager', email: 'manager@claimflow.dev' },
  { label: 'Admin', email: 'admin@claimflow.dev' },
]

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={isStaff(user) ? '/dashboard' : '/claims'} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const u = await login(email, password)
      navigate(isStaff(u) ? '/dashboard' : '/claims')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Sign in" subtitle="Track your claims or work the review queue.">
      <form onSubmit={submit} className="space-y-4">
        <ErrorBanner message={error} />
        <Field label="Email">
          <input className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password">
          <input className="input" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Button type="submit" className="w-full" loading={busy}>
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        New policy holder?{' '}
        <Link to="/register" className="font-medium text-brand-600 hover:underline">
          Create an account
        </Link>
      </p>
      <div className="mt-8 rounded-lg border border-dashed border-slate-300 p-3">
        <div className="text-xs font-medium text-slate-500">Demo accounts (password Demo@1234)</div>
        <div className="mt-2 grid grid-cols-2 gap-2">
          {DEMO.map((d) => (
            <button
              key={d.email}
              type="button"
              onClick={() => {
                setEmail(d.email)
                setPassword('Demo@1234')
              }}
              className="rounded-md border border-slate-200 bg-white px-2 py-1.5 text-xs font-medium text-slate-700 hover:border-brand-500 hover:text-brand-700"
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>
    </AuthShell>
  )
}
