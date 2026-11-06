import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import FileDrop from '../components/FileDrop'
import { Button, EmptyState, ErrorBanner, Field, PageHeader, PageLoader } from '../components/ui'
import { api, ApiError } from '../lib/api'
import { date, inr, titleCase } from '../lib/format'
import { useToast } from '../lib/toast'
import type { ClaimDetail, Policy } from '../lib/types'

const today = () => new Date().toISOString().slice(0, 10)

export default function NewClaimPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const toast = useToast()
  const { data: policies, isLoading } = useQuery({ queryKey: ['policies'], queryFn: () => api.get<Policy[]>('/policies') })
  const active = useMemo(() => (policies ?? []).filter((p) => p.status === 'ACTIVE'), [policies])

  const [form, setForm] = useState({ policyId: '', title: '', incidentDate: today(), claimedAmount: '', description: '' })
  const [files, setFiles] = useState<File[]>([])
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [step, setStep] = useState<string | null>(null)

  const policy = active.find((p) => String(p.id) === form.policyId) ?? (active.length === 1 ? active[0] : undefined)
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setForm({ ...form, [k]: e.target.value })

  async function save(e: FormEvent, submitNow: boolean) {
    e.preventDefault()
    if (!policy) return setError('Choose a policy')
    setError(null)
    setFieldErrors({})
    try {
      setStep('Creating claim')
      const created = await api.post<ClaimDetail>('/claims', {
        policyId: policy.id,
        title: form.title,
        description: form.description,
        incidentDate: form.incidentDate,
        claimedAmount: Number(form.claimedAmount),
      })
      const id = created.claim.id
      for (let i = 0; i < files.length; i++) {
        setStep(`Uploading ${i + 1} of ${files.length}`)
        const body = new FormData()
        body.append('file', files[i])
        await api.post(`/claims/${id}/documents`, body)
      }
      if (submitNow) {
        setStep('Submitting')
        await api.post(`/claims/${id}/submit`)
        toast('Claim submitted. Our AI is reviewing it now.')
      } else {
        toast('Draft saved')
      }
      qc.invalidateQueries({ queryKey: ['claims'] })
      navigate(`/claims/${id}`)
    } catch (err) {
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors)
      setError(err instanceof Error ? err.message : 'Could not save the claim')
    } finally {
      setStep(null)
    }
  }

  if (isLoading) return <PageLoader />
  if (!active.length) {
    return (
      <div className="card">
        <EmptyState icon={<ShieldCheck className="size-6" />} title="No active policy">
          You need an active policy to file a claim. Contact your insurer to get one issued.
        </EmptyState>
      </div>
    )
  }

  return (
    <>
      <PageHeader title="File a claim" subtitle="Tell us what happened. Attach bills so we can process it faster." />
      <form onSubmit={(e) => save(e, true)} className="grid gap-6 lg:grid-cols-3">
        <div className="card space-y-5 p-6 lg:col-span-2">
          <ErrorBanner message={error} />
          <Field label="Policy" error={fieldErrors.policyId}>
            <select className="input" value={policy ? String(policy.id) : ''} onChange={set('policyId')} required>
              {active.length > 1 && <option value="">Choose a policy</option>}
              {active.map((p) => (
                <option key={p.id} value={p.id}>
                  {titleCase(p.type)} · {p.policyNumber} · cover {inr(p.sumInsured)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Short title" error={fieldErrors.title}>
            <input className="input" required minLength={5} maxLength={160} value={form.title} onChange={set('title')} placeholder="e.g. Hospital stay for appendix surgery" />
          </Field>
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Date of incident" error={fieldErrors.incidentDate}>
              <input className="input" type="date" required max={today()} min={policy?.startDate} value={form.incidentDate} onChange={set('incidentDate')} />
            </Field>
            <Field label="Amount claimed (₹)" error={fieldErrors.claimedAmount} hint={policy ? `Up to ${inr(policy.sumInsured)}` : undefined}>
              <input className="input tabular" type="number" required min={1} max={policy?.sumInsured} step="1" value={form.claimedAmount} onChange={set('claimedAmount')} placeholder="25000" />
            </Field>
          </div>
          <Field label="What happened?" error={fieldErrors.description} hint="Where, when, what was damaged or treated. At least 20 characters.">
            <textarea className="input min-h-32" required minLength={20} maxLength={5000} value={form.description} onChange={set('description')} />
          </Field>
          <div>
            <span className="label">Supporting documents</span>
            <FileDrop files={files} onChange={setFiles} />
          </div>
        </div>

        <div className="space-y-4">
          {policy && (
            <div className="card p-5 text-sm">
              <div className="text-xs font-medium uppercase tracking-wide text-slate-500">Your cover</div>
              <div className="mt-2 text-lg font-semibold">{titleCase(policy.type)} insurance</div>
              <dl className="mt-3 space-y-1.5 text-slate-600">
                <div className="flex justify-between"><dt>Policy</dt><dd className="font-medium text-slate-900">{policy.policyNumber}</dd></div>
                <div className="flex justify-between"><dt>Sum insured</dt><dd className="tabular font-medium text-slate-900">{inr(policy.sumInsured)}</dd></div>
                <div className="flex justify-between"><dt>Valid</dt><dd>{date(policy.startDate)} – {date(policy.endDate)}</dd></div>
              </dl>
            </div>
          )}
          <div className="card space-y-3 p-5">
            <Button type="submit" className="w-full" loading={!!step}>
              {step ?? 'Submit claim'}
            </Button>
            <Button type="button" variant="secondary" className="w-full" disabled={!!step} onClick={(e) => save(e, false)}>
              Save as draft
            </Button>
            <p className="text-xs text-slate-500">
              After you submit, our AI checks your documents in under a minute and a claims adjuster reviews it.
            </p>
            <Link to="/claims" className="block text-center text-sm text-slate-500 hover:text-slate-700">Cancel</Link>
          </div>
        </div>
      </form>
    </>
  )
}
