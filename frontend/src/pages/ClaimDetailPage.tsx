import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import {
  AlertTriangle, ArrowLeft, Bot, Check, CheckCircle2, Clock, FileText, Lock, MessageSquare, RefreshCw,
  Send, UserPlus, XCircle,
} from 'lucide-react'
import FileDrop from '../components/FileDrop'
import { Badge, Button, EmptyState, ErrorBanner, Field, Modal, PageLoader, RiskBadge, StatusBadge } from '../components/ui'
import { api, ApiError, openDocument } from '../lib/api'
import { isStaff, useAuth } from '../lib/auth'
import { date, dateTime, fileSize, inr, relative, titleCase } from '../lib/format'
import { CUSTOMER_STEPS, RISK_META } from '../lib/statusMeta'
import { useToast } from '../lib/toast'
import type { ClaimAction, ClaimDetail } from '../lib/types'

export default function ClaimDetailPage() {
  const { id } = useParams()
  const claimId = Number(id)
  const { user } = useAuth()
  const staff = isStaff(user)

  const { data, isLoading, error } = useQuery({
    queryKey: ['claim', claimId],
    queryFn: () => api.get<ClaimDetail>(`/claims/${claimId}`),
    // safety net in case a WebSocket event is missed while the AI is working
    refetchInterval: (q) => (q.state.data?.claim.status === 'AI_REVIEW' ? 2500 : false),
  })

  if (isLoading) return <PageLoader />
  if (error || !data) {
    return (
      <div className="card">
        <EmptyState icon={<FileText className="size-6" />} title="Claim not found">
          <Link to="/claims" className="text-brand-600 hover:underline">Back to claims</Link>
        </EmptyState>
      </div>
    )
  }
  const c = data.claim

  return (
    <>
      <Link to="/claims" className="mb-4 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="size-4" /> {staff ? 'Work queue' : 'My claims'}
      </Link>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{c.title}</h1>
            <StatusBadge status={c.status} customer={!staff} />
            {staff && c.slaBreached && !['APPROVED', 'REJECTED', 'PAID'].includes(c.status) && (
              <Badge className="bg-rose-50 text-rose-700 ring-rose-200"><AlertTriangle className="size-3" /> SLA overdue</Badge>
            )}
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {c.claimNumber} · {titleCase(c.type)} · filed {date(c.createdAt)}
            {staff && c.customerName && <> · {c.customerName}</>}
          </p>
        </div>
        <div className="text-right">
          <div className="text-xs uppercase tracking-wide text-slate-500">Claimed</div>
          <div className="tabular text-2xl font-semibold">{inr(c.claimedAmount)}</div>
          {c.approvedAmount !== null && (
            <div className="tabular text-sm font-medium text-emerald-600">
              {c.status === 'PENDING_APPROVAL' ? 'Recommended' : 'Approved'} {inr(c.approvedAmount)}
            </div>
          )}
        </div>
      </div>

      {!staff && <CustomerProgress detail={data} />}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {!staff && <CustomerActionBox detail={data} />}
          <Section title="Details">
            <p className="whitespace-pre-line text-sm leading-relaxed text-slate-700">{data.description}</p>
            <dl className="mt-4 grid grid-cols-2 gap-4 border-t border-slate-100 pt-4 text-sm sm:grid-cols-4">
              <Info label="Incident date" value={date(data.incidentDate)} />
              <Info label="Policy" value={data.policy.policyNumber} />
              <Info label="Sum insured" value={inr(data.policy.sumInsured)} />
              <Info label="Adjuster" value={c.adjusterName ?? 'Not assigned yet'} />
            </dl>
            {data.decisionReason && (
              <div className={clsx('mt-4 rounded-lg px-3 py-2 text-sm', c.status === 'REJECTED' ? 'bg-rose-50 text-rose-800' : 'bg-slate-50 text-slate-700')}>
                <b>{c.status === 'REJECTED' ? 'Reason for rejection: ' : 'Decision note: '}</b>{data.decisionReason}
              </div>
            )}
          </Section>
          <Documents detail={data} />
          <Conversation detail={data} />
        </div>

        <div className="space-y-6">
          {staff && <ActionsPanel detail={data} />}
          {staff && <AiPanel detail={data} />}
          <Timeline detail={data} />
        </div>
      </div>
    </>
  )
}

function Section({ title, icon, right, children }: { title: string; icon?: ReactNode; right?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">{icon}{title}</h2>
        {right}
      </div>
      <div className="p-5">{children}</div>
    </section>
  )
}

function Info({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{value}</dd>
    </div>
  )
}

// ------------------------------------------------------------------ customer

function CustomerProgress({ detail }: { detail: ClaimDetail }) {
  const status = detail.claim.status
  if (status === 'DRAFT') return null
  const current = CUSTOMER_STEPS.findIndex((s) => s.statuses.includes(status))
  const rejected = status === 'REJECTED'
  return (
    <div className="card mb-6 px-6 py-5">
      <ol className="flex items-center">
        {CUSTOMER_STEPS.map((step, i) => {
          const done = i < current || (i === current && ['APPROVED', 'PAID'].includes(status))
          const active = i === current
          const label = i === 2 && rejected ? 'Rejected' : i === 2 && current >= 2 && !rejected ? 'Approved' : step.label
          return (
            <li key={step.key} className={clsx('flex items-center', i < CUSTOMER_STEPS.length - 1 && 'flex-1')}>
              <div className="flex flex-col items-center gap-1.5">
                <span
                  className={clsx(
                    'flex size-8 items-center justify-center rounded-full text-xs font-semibold ring-4 ring-white',
                    rejected && i === 2 ? 'bg-rose-600 text-white' : done ? 'bg-emerald-600 text-white' : active ? 'bg-brand-600 text-white' : 'bg-slate-200 text-slate-500',
                  )}
                >
                  {rejected && i === 2 ? <XCircle className="size-4" /> : done ? <Check className="size-4" /> : i + 1}
                </span>
                <span className={clsx('text-xs font-medium', active || done ? 'text-slate-900' : 'text-slate-400')}>{label}</span>
              </div>
              {i < CUSTOMER_STEPS.length - 1 && (
                <div className={clsx('mx-2 mb-5 h-0.5 flex-1', i < current ? 'bg-emerald-600' : 'bg-slate-200')} />
              )}
            </li>
          )
        })}
      </ol>
      {status === 'AI_REVIEW' && (
        <p className="mt-4 flex items-center justify-center gap-2 text-sm text-violet-700">
          <Bot className="size-4 animate-pulse" /> Our AI is reading your documents. This usually takes under a minute.
        </p>
      )}
    </div>
  )
}

function CustomerActionBox({ detail }: { detail: ClaimDetail }) {
  const c = detail.claim
  const qc = useQueryClient()
  const toast = useToast()
  const [files, setFiles] = useState<File[]>([])
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const lastRequest = [...detail.comments].reverse().find((cm) => cm.authorRole && cm.authorRole !== 'CUSTOMER')

  if (c.status !== 'DRAFT' && c.status !== 'INFO_REQUESTED') return null

  async function uploadAll() {
    for (const f of files) {
      const body = new FormData()
      body.append('file', f)
      await api.post(`/claims/${c.id}/documents`, body)
    }
  }

  async function go() {
    setBusy(true)
    setError(null)
    try {
      await uploadAll()
      if (c.status === 'DRAFT') {
        await api.post(`/claims/${c.id}/submit`)
        toast('Claim submitted. Our AI is reviewing it now.')
      } else {
        await api.post(`/claims/${c.id}/respond`, { message })
        toast('Thanks, your response was sent to the adjuster')
      }
      setFiles([])
      setMessage('')
      qc.invalidateQueries({ queryKey: ['claim', c.id] })
      qc.invalidateQueries({ queryKey: ['claims'] })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={clsx('card border-l-4 p-5', c.status === 'DRAFT' ? 'border-l-slate-400' : 'border-l-orange-500')}>
      <h2 className="text-sm font-semibold">{c.status === 'DRAFT' ? 'This claim is a draft' : 'We need a bit more from you'}</h2>
      {c.status === 'INFO_REQUESTED' && lastRequest && (
        <p className="mt-2 rounded-lg bg-orange-50 px-3 py-2 text-sm text-orange-900">
          <b>{lastRequest.authorName}:</b> {lastRequest.body}
        </p>
      )}
      <div className="mt-4 space-y-4">
        <ErrorBanner message={error} />
        <FileDrop files={files} onChange={setFiles} />
        {c.status === 'INFO_REQUESTED' && (
          <Field label="Your reply">
            <textarea className="input min-h-20" value={message} onChange={(e) => setMessage(e.target.value)} placeholder="Explain or point to what you uploaded" />
          </Field>
        )}
        <Button onClick={go} loading={busy} disabled={c.status === 'INFO_REQUESTED' && !message.trim()}>
          <Send className="size-4" /> {c.status === 'DRAFT' ? 'Submit claim' : 'Send response'}
        </Button>
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ staff

function ActionsPanel({ detail }: { detail: ClaimDetail }) {
  const c = detail.claim
  const qc = useQueryClient()
  const toast = useToast()
  const [modal, setModal] = useState<ClaimAction | null>(null)
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const act = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api.post<ClaimDetail>(`/claims/${c.id}/actions`, { ...body, expectedVersion: detail.version }),
    onSuccess: (updated, body) => {
      qc.setQueryData(['claim', c.id], updated)
      qc.invalidateQueries({ queryKey: ['claims'] })
      setModal(null)
      const s = updated.claim.status
      toast(
        body.action === 'APPROVE' && s === 'PENDING_APPROVAL' ? 'Sent to a manager for final approval'
          : body.action === 'ASSIGN_TO_ME' ? 'Assigned to you'
            : `Claim is now ${titleCase(s)}`,
      )
    },
    onError: (err) => {
      if (err instanceof ApiError && err.status === 409) {
        qc.invalidateQueries({ queryKey: ['claim', c.id] })
        toast('Someone else just updated this claim. Reloaded the latest version.', 'error')
        setModal(null)
      } else setError(err instanceof Error ? err.message : 'Action failed')
    },
  })

  const open = (a: ClaimAction) => {
    setError(null)
    setReason('')
    setAmount(String(c.approvedAmount ?? c.claimedAmount))
    setModal(a)
  }
  const has = (a: ClaimAction) => detail.allowedActions.includes(a)
  const decisionActions = detail.allowedActions.filter((a) => a !== 'ASSIGN_TO_ME')

  return (
    <section className="card p-5">
      <h2 className="text-sm font-semibold">Actions</h2>
      {c.status === 'PENDING_APPROVAL' && (
        <p className="mt-2 rounded-lg bg-indigo-50 px-3 py-2 text-xs text-indigo-800">
          Adjuster recommended {inr(c.approvedAmount)}. Needs a manager's sign-off.
        </p>
      )}
      {c.status === 'AI_REVIEW' && (
        <p className="mt-2 flex items-center gap-2 text-sm text-violet-700"><Bot className="size-4 animate-pulse" /> AI analysis running…</p>
      )}
      {!detail.allowedActions.length && c.status !== 'AI_REVIEW' && (
        <p className="mt-2 text-sm text-slate-500">
          {['APPROVED', 'REJECTED', 'PAID'].includes(c.status) && !has('MARK_PAID') ? 'This claim is closed.' : 'Nothing for you to do right now.'}
        </p>
      )}
      <div className="mt-3 grid gap-2">
        {has('APPROVE') && <Button variant="success" onClick={() => open('APPROVE')}><CheckCircle2 className="size-4" /> Approve</Button>}
        {has('REJECT') && <Button variant="danger" onClick={() => open('REJECT')}><XCircle className="size-4" /> Reject</Button>}
        {has('REQUEST_INFO') && <Button variant="secondary" onClick={() => open('REQUEST_INFO')}><MessageSquare className="size-4" /> Request info</Button>}
        {has('MARK_PAID') && <Button onClick={() => act.mutate({ action: 'MARK_PAID' })} loading={act.isPending}>Mark as paid</Button>}
        {has('ASSIGN_TO_ME') && (
          <Button variant={decisionActions.length ? 'ghost' : 'secondary'} onClick={() => act.mutate({ action: 'ASSIGN_TO_ME' })} loading={act.isPending && !modal}>
            <UserPlus className="size-4" /> Assign to me
          </Button>
        )}
        {has('REANALYZE') && (
          <Button variant="ghost" onClick={() => act.mutate({ action: 'REANALYZE' })}>
            <RefreshCw className="size-4" /> Re-run AI analysis
          </Button>
        )}
      </div>

      <Modal
        open={modal !== null}
        onClose={() => setModal(null)}
        title={modal === 'APPROVE' ? 'Approve claim' : modal === 'REJECT' ? 'Reject claim' : 'Request more information'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModal(null)}>Cancel</Button>
            <Button
              variant={modal === 'REJECT' ? 'danger' : modal === 'APPROVE' ? 'success' : 'primary'}
              loading={act.isPending}
              onClick={() =>
                act.mutate(modal === 'APPROVE' ? { action: 'APPROVE', approvedAmount: Number(amount), reason } : { action: modal, reason })
              }
            >
              Confirm
            </Button>
          </>
        }
      >
        <ErrorBanner message={error} />
        {modal === 'APPROVE' && (
          <Field label="Amount to approve (₹)" hint={`Claimed ${inr(c.claimedAmount)} · cover ${inr(detail.policy.sumInsured)}`}>
            <input className="input tabular" type="number" min={1} max={c.claimedAmount} value={amount} onChange={(e) => setAmount(e.target.value)} />
          </Field>
        )}
        <Field
          label={modal === 'APPROVE' ? 'Note (optional)' : modal === 'REJECT' ? 'Reason the customer will see' : 'What do you need from the customer?'}
          hint={modal === 'REJECT' ? 'At least 10 characters. Quote the policy clause if you can.' : undefined}
        >
          <textarea className="input min-h-24" value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </Modal>
    </section>
  )
}

function AiPanel({ detail }: { detail: ClaimDetail }) {
  const c = detail.claim
  const extracted = detail.aiExtracted ?? {}
  const billTotal = typeof extracted.billTotal === 'number' ? extracted.billTotal : null
  const items = Array.isArray(extracted.items) ? (extracted.items as string[]) : []
  const provider = typeof extracted.provider === 'string' ? extracted.provider : null

  return (
    <Section title="AI analysis" icon={<Bot className="size-4 text-violet-600" />} right={detail.aiProvider && <span className="text-xs text-slate-400">{detail.aiProvider}</span>}>
      {c.fraudScore === null ? (
        <p className="text-sm text-slate-500">{c.status === 'AI_REVIEW' ? 'Analysing documents…' : 'Not analysed yet.'}</p>
      ) : (
        <div className="space-y-4 text-sm">
          <div>
            <div className="flex items-center justify-between">
              <span className="font-medium text-slate-700">Fraud risk</span>
              <RiskBadge level={c.riskLevel} />
            </div>
            <div className="mt-2 flex items-center gap-3">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div className={clsx('h-full rounded-full', c.riskLevel && RISK_META[c.riskLevel].bar)} style={{ width: `${Math.max(c.fraudScore, 3)}%` }} />
              </div>
              <span className="tabular w-12 text-right font-semibold">{c.fraudScore}/100</span>
            </div>
          </div>
          {detail.fraudFlags && detail.fraudFlags.length > 0 && (
            <ul className="space-y-1.5">
              {detail.fraudFlags.map((f) => (
                <li key={f} className="flex gap-2 text-slate-700">
                  <AlertTriangle className={clsx('mt-0.5 size-3.5 shrink-0', f.startsWith('AI:') ? 'text-violet-500' : 'text-amber-500')} />
                  {f}
                </li>
              ))}
            </ul>
          )}
          {detail.aiSummary && <p className="rounded-lg bg-violet-50/60 p-3 leading-relaxed text-slate-700">{detail.aiSummary}</p>}
          <dl className="grid grid-cols-2 gap-3 border-t border-slate-100 pt-3">
            <Info label="Bill total found" value={billTotal !== null ? inr(billTotal) : '—'} />
            <Info
              label="vs claimed"
              value={
                billTotal !== null ? (
                  <span className={billTotal >= c.claimedAmount ? 'text-emerald-600' : 'text-amber-600'}>
                    {billTotal >= c.claimedAmount ? 'Matches' : `${inr(c.claimedAmount - billTotal)} over`}
                  </span>
                ) : '—'
              }
            />
            {provider && <Info label="Provider" value={provider} />}
          </dl>
          {items.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {items.map((i) => <Badge key={i} className="bg-slate-50 text-slate-600 ring-slate-200">{i}</Badge>)}
            </div>
          )}
        </div>
      )}
    </Section>
  )
}

// ------------------------------------------------------------------ shared

function Documents({ detail }: { detail: ClaimDetail }) {
  const toast = useToast()
  return (
    <Section title={`Documents (${detail.documents.length})`} icon={<FileText className="size-4 text-slate-400" />}>
      {detail.documents.length === 0 ? (
        <p className="text-sm text-slate-500">No documents attached.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {detail.documents.map((d) => (
            <li key={d.id} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
              <FileText className="size-5 text-slate-400" />
              <button
                className="flex-1 truncate text-left text-sm font-medium text-brand-700 hover:underline"
                onClick={() => openDocument(detail.claim.id, d.id).catch(() => toast('Could not open file', 'error'))}
              >
                {d.fileName}
              </button>
              {d.textExtracted && <Badge className="bg-violet-50 text-violet-700 ring-violet-200">read by AI</Badge>}
              <span className="text-xs text-slate-500">{fileSize(d.sizeBytes)}</span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}

function Conversation({ detail }: { detail: ClaimDetail }) {
  const { user } = useAuth()
  const staff = isStaff(user)
  const qc = useQueryClient()
  const [body, setBody] = useState('')
  const [internal, setInternal] = useState(false)
  const send = useMutation({
    mutationFn: () => api.post(`/claims/${detail.claim.id}/comments`, { body, internal }),
    onSuccess: () => {
      setBody('')
      qc.invalidateQueries({ queryKey: ['claim', detail.claim.id] })
    },
  })

  return (
    <Section title="Messages" icon={<MessageSquare className="size-4 text-slate-400" />}>
      <div className="space-y-3">
        {detail.comments.length === 0 && <p className="text-sm text-slate-500">No messages yet.</p>}
        {detail.comments.map((cm) => {
          const mine = cm.authorId === user?.id
          return (
            <div key={cm.id} className={clsx('flex', mine && 'justify-end')}>
              <div
                className={clsx(
                  'max-w-[85%] rounded-xl px-3.5 py-2 text-sm',
                  cm.internal ? 'border border-dashed border-amber-300 bg-amber-50' : mine ? 'bg-brand-600 text-white' : 'bg-slate-100',
                )}
              >
                <div className={clsx('mb-0.5 flex items-center gap-1.5 text-xs', mine && !cm.internal ? 'text-brand-100' : 'text-slate-500')}>
                  {cm.internal && <Lock className="size-3" />}
                  <span className="font-medium">{cm.authorName}</span>
                  {cm.authorRole && cm.authorRole !== 'CUSTOMER' && <span>· {titleCase(cm.authorRole)}</span>}
                  <span>· {relative(cm.createdAt)}</span>
                </div>
                <div className="whitespace-pre-line">{cm.body}</div>
              </div>
            </div>
          )
        })}
      </div>
      <form
        className="mt-4 space-y-2 border-t border-slate-100 pt-4"
        onSubmit={(e) => {
          e.preventDefault()
          if (body.trim()) send.mutate()
        }}
      >
        <textarea className="input min-h-16" placeholder={staff ? 'Write a message or internal note' : 'Write a message to your claims team'} value={body} onChange={(e) => setBody(e.target.value)} />
        <div className="flex items-center justify-between">
          {staff ? (
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} className="rounded border-slate-300" />
              <Lock className="size-3.5" /> Internal note (staff only)
            </label>
          ) : <span />}
          <Button type="submit" variant="secondary" loading={send.isPending} disabled={!body.trim()}>
            <Send className="size-4" /> Send
          </Button>
        </div>
      </form>
    </Section>
  )
}

const ACTION_LABEL: Record<string, string> = {
  CREATED: 'Claim created',
  DOCUMENT_UPLOADED: 'Document uploaded',
  SUBMITTED: 'Submitted',
  AI_REVIEW_STARTED: 'AI review started',
  AI_ANALYZED: 'AI review finished',
  ASSIGNED: 'Assigned',
  INFO_REQUESTED: 'More info requested',
  CUSTOMER_RESPONDED: 'Customer responded',
  SENT_FOR_MANAGER_APPROVAL: 'Sent for manager approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PAID: 'Paid out',
  REANALYZE_REQUESTED: 'Re-analysis requested',
}

function Timeline({ detail }: { detail: ClaimDetail }) {
  return (
    <Section title="Timeline" icon={<Clock className="size-4 text-slate-400" />}>
      <ol className="relative space-y-4 border-l border-slate-200 pl-5">
        {[...detail.timeline].reverse().map((t) => (
          <li key={t.id} className="relative">
            <span className="absolute -left-[25px] top-1 size-2.5 rounded-full bg-white ring-2 ring-brand-500" />
            <div className="text-sm font-medium text-slate-800">{ACTION_LABEL[t.action] ?? titleCase(t.action)}</div>
            {t.details && <div className="text-xs text-slate-600">{t.details}</div>}
            <div className="text-xs text-slate-400">
              {dateTime(t.createdAt)}
              {t.actorName && <> · {t.actorName}</>}
            </div>
          </li>
        ))}
      </ol>
    </Section>
  )
}
