import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PlusCircle, ShieldCheck } from 'lucide-react'
import { Badge, Button, EmptyState, ErrorBanner, Field, Modal, PageHeader, PageLoader } from '../components/ui'
import { api, ApiError, qs } from '../lib/api'
import { isStaff, useAuth } from '../lib/auth'
import { date, inr, titleCase } from '../lib/format'
import { useToast } from '../lib/toast'
import type { Policy, PolicyType, User } from '../lib/types'

const TYPES: PolicyType[] = ['HEALTH', 'MOTOR', 'PROPERTY', 'TRAVEL']

export default function PoliciesPage() {
  const { user, hasRole } = useAuth()
  const staff = isStaff(user)
  const [open, setOpen] = useState(false)
  const { data, isLoading } = useQuery({ queryKey: ['policies'], queryFn: () => api.get<Policy[]>('/policies') })

  if (isLoading) return <PageLoader />
  return (
    <>
      <PageHeader
        title="Policies"
        subtitle={staff ? 'All policies issued to customers.' : 'Your insurance cover.'}
        actions={hasRole('ADMIN', 'MANAGER') && <Button onClick={() => setOpen(true)}><PlusCircle className="size-4" /> Issue policy</Button>}
      />
      {!data?.length ? (
        <div className="card"><EmptyState icon={<ShieldCheck className="size-6" />} title="No policies yet" /></div>
      ) : staff ? (
        <div className="card overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2.5">Policy</th><th className="px-4 py-2.5">Holder</th><th className="px-4 py-2.5">Type</th>
                <th className="px-4 py-2.5 text-right">Sum insured</th><th className="px-4 py-2.5 text-right">Premium</th>
                <th className="px-4 py-2.5">Valid</th><th className="px-4 py-2.5">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((p) => (
                <tr key={p.id}>
                  <td className="px-4 py-3 font-medium">{p.policyNumber}</td>
                  <td className="px-4 py-3">{p.holderName}</td>
                  <td className="px-4 py-3">{titleCase(p.type)}</td>
                  <td className="tabular px-4 py-3 text-right">{inr(p.sumInsured)}</td>
                  <td className="tabular px-4 py-3 text-right">{inr(p.premium)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{date(p.startDate)} – {date(p.endDate)}</td>
                  <td className="px-4 py-3"><PolicyStatus p={p} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((p) => (
            <div key={p.id} className="card p-5">
              <div className="flex items-start justify-between">
                <div>
                  <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{titleCase(p.type)}</div>
                  <div className="mt-0.5 font-semibold">{p.policyNumber}</div>
                </div>
                <PolicyStatus p={p} />
              </div>
              <div className="tabular mt-4 text-2xl font-semibold">{inr(p.sumInsured)}</div>
              <div className="text-xs text-slate-500">sum insured</div>
              <div className="mt-4 border-t border-slate-100 pt-3 text-sm text-slate-600">
                {date(p.startDate)} – {date(p.endDate)} · premium {inr(p.premium)}
              </div>
            </div>
          ))}
        </div>
      )}
      {open && <IssuePolicyModal onClose={() => setOpen(false)} />}
    </>
  )
}

function PolicyStatus({ p }: { p: Policy }) {
  const expired = new Date(p.endDate) < new Date()
  if (p.status !== 'ACTIVE') return <Badge className="bg-slate-100 text-slate-600 ring-slate-200">{titleCase(p.status)}</Badge>
  if (expired) return <Badge className="bg-slate-100 text-slate-600 ring-slate-200">Expired</Badge>
  return <Badge className="bg-emerald-50 text-emerald-700 ring-emerald-200">Active</Badge>
}

function IssuePolicyModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [form, setForm] = useState(() => ({
    holderId: '',
    type: 'HEALTH',
    sumInsured: '500000',
    premium: '12000',
    startDate: new Date().toISOString().slice(0, 10),
    endDate: new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10),
  }))
  const customers = useQuery({ queryKey: ['users', 'CUSTOMER'], queryFn: () => api.get<User[]>(`/users${qs({ role: 'CUSTOMER' })}`) })
  const issue = useMutation({
    mutationFn: () => api.post<Policy>('/policies', { ...form, holderId: Number(form.holderId), sumInsured: Number(form.sumInsured), premium: Number(form.premium) }),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['policies'] })
      toast(`Issued ${p.policyNumber} to ${p.holderName}`)
      onClose()
    },
  })
  const err = issue.error instanceof ApiError ? issue.error : null
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [k]: e.target.value })

  return (
    <Modal open onClose={onClose} title="Issue a policy" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={issue.isPending} disabled={!form.holderId} onClick={() => issue.mutate()}>Issue</Button></>}>
      <ErrorBanner message={issue.error?.message} />
      <Field label="Customer" error={err?.fieldErrors.holderId}>
        <select className="input" value={form.holderId} onChange={set('holderId')}>
          <option value="">Select a customer</option>
          {customers.data?.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.email})</option>)}
        </select>
      </Field>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Type">
          <select className="input" value={form.type} onChange={set('type')}>{TYPES.map((t) => <option key={t} value={t}>{titleCase(t)}</option>)}</select>
        </Field>
        <Field label="Sum insured (₹)" error={err?.fieldErrors.sumInsured}><input className="input" type="number" value={form.sumInsured} onChange={set('sumInsured')} /></Field>
        <Field label="Premium (₹)" error={err?.fieldErrors.premium}><input className="input" type="number" value={form.premium} onChange={set('premium')} /></Field>
        <div />
        <Field label="Start"><input className="input" type="date" value={form.startDate} onChange={set('startDate')} /></Field>
        <Field label="End"><input className="input" type="date" value={form.endDate} onChange={set('endDate')} /></Field>
      </div>
    </Modal>
  )
}
