import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { UserPlus } from 'lucide-react'
import clsx from 'clsx'
import { Badge, Button, ErrorBanner, Field, Modal, PageHeader, PageLoader } from '../components/ui'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { date, titleCase } from '../lib/format'
import { useToast } from '../lib/toast'
import type { Role, User } from '../lib/types'

const ROLE_TONE: Record<Role, string> = {
  ADMIN: 'bg-slate-900 text-white ring-slate-900',
  MANAGER: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  ADJUSTER: 'bg-sky-50 text-sky-700 ring-sky-200',
  CUSTOMER: 'bg-slate-50 text-slate-600 ring-slate-200',
}

export default function UsersPage() {
  const { user: me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const { data, isLoading } = useQuery({ queryKey: ['users'], queryFn: () => api.get<User[]>('/users') })
  const toggle = useMutation({
    mutationFn: (u: User) => api.patch<User>(`/users/${u.id}`, { active: !u.active }),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast(`${u.fullName} ${u.active ? 'enabled' : 'disabled'}`)
    },
    onError: (e) => toast(e.message, 'error'),
  })

  if (isLoading) return <PageLoader />
  return (
    <>
      <PageHeader title="Users" subtitle="Staff accounts and policy holders." actions={<Button onClick={() => setOpen(true)}><UserPlus className="size-4" /> Add user</Button>} />
      <div className="card overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr><th className="px-4 py-2.5">Name</th><th className="px-4 py-2.5">Role</th><th className="px-4 py-2.5">Joined</th><th className="px-4 py-2.5">Status</th><th /></tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data?.map((u) => (
              <tr key={u.id} className={clsx(!u.active && 'opacity-60')}>
                <td className="px-4 py-3"><div className="font-medium">{u.fullName}</div><div className="text-xs text-slate-500">{u.email}</div></td>
                <td className="px-4 py-3"><Badge className={ROLE_TONE[u.role]}>{titleCase(u.role)}</Badge></td>
                <td className="px-4 py-3 text-slate-600">{date(u.createdAt)}</td>
                <td className="px-4 py-3">{u.active ? 'Active' : 'Disabled'}</td>
                <td className="px-4 py-3 text-right">
                  {u.id !== me?.id && (
                    <Button variant="ghost" onClick={() => toggle.mutate(u)}>{u.active ? 'Disable' : 'Enable'}</Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {open && <AddUserModal onClose={() => setOpen(false)} />}
    </>
  )
}

function AddUserModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'ADJUSTER' as Role })
  const create = useMutation({
    mutationFn: () => api.post<User>('/users', form),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ['users'] })
      toast(`Created ${u.fullName}`)
      onClose()
    },
  })
  const err = create.error instanceof ApiError ? create.error : null
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value })

  return (
    <Modal open onClose={onClose} title="Add user" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={create.isPending} onClick={() => create.mutate()}>Create</Button></>}>
      <ErrorBanner message={create.error?.message} />
      <Field label="Full name" error={err?.fieldErrors.fullName}><input className="input" value={form.fullName} onChange={set('fullName')} /></Field>
      <Field label="Email" error={err?.fieldErrors.email}><input className="input" type="email" value={form.email} onChange={set('email')} /></Field>
      <Field label="Temporary password" error={err?.fieldErrors.password} hint="At least 8 characters. Share it with them securely."><input className="input" type="text" value={form.password} onChange={set('password')} /></Field>
      <Field label="Role">
        <select className="input" value={form.role} onChange={set('role')}>
          {(['ADJUSTER', 'MANAGER', 'ADMIN', 'CUSTOMER'] as Role[]).map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}
        </select>
      </Field>
    </Modal>
  )
}
