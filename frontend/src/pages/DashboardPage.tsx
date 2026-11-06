import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PageHeader, PageLoader, RiskBadge, Stat, StatusBadge } from '../components/ui'
import { api, qs } from '../lib/api'
import { useAuth } from '../lib/auth'
import { compactInr, inr, titleCase } from '../lib/format'
import { STATUS_META } from '../lib/statusMeta'
import type { AnalyticsSummary, ClaimStatus, ClaimSummary, Page } from '../lib/types'

const PIPELINE: ClaimStatus[] = ['AI_REVIEW', 'UNDER_REVIEW', 'INFO_REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PAID']

export default function DashboardPage() {
  const { user } = useAuth()
  const { data, isLoading } = useQuery({
    queryKey: ['analytics'],
    queryFn: () => api.get<AnalyticsSummary>('/analytics/summary'),
    refetchInterval: 30_000,
  })
  const queueScope = user?.role === 'ADJUSTER' ? 'mine' : 'all'
  const urgent = useQuery({
    queryKey: ['claims', 'urgent', queueScope],
    queryFn: () =>
      api.get<Page<ClaimSummary>>(`/claims${qs({ scope: queueScope, status: user?.role === 'ADJUSTER' ? 'UNDER_REVIEW' : 'PENDING_APPROVAL', sort: 'slaDueAt', dir: 'asc', size: 6 })}`),
  })

  if (isLoading || !data) return <PageLoader />
  const maxPipeline = Math.max(1, ...PIPELINE.map((s) => data.byStatus[s] ?? 0))
  const daily = data.daily.map((d) => ({ ...d, label: new Date(d.date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) }))

  return (
    <>
      <PageHeader title="Dashboard" subtitle={`Live view of claims operations · refreshed ${new Date(data.generatedAt).toLocaleTimeString('en-IN')}`} />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Open claims" value={data.openClaims} sub={`${data.totalClaims} total`} />
        <Stat label="SLA breached" value={data.slaBreached} tone={data.slaBreached ? 'danger' : 'good'} sub="decision later than target" />
        <Stat label="High risk" value={data.highRisk} tone={data.highRisk ? 'danger' : undefined} sub="fraud score 70+" />
        <Stat
          label="Approval rate"
          value={`${Math.round(data.approvalRate * 100)}%`}
          sub={`avg ${data.avgDecisionHours}h to decide`}
        />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <div className="card p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold">Claims in the last 14 days</h2>
            <div className="flex gap-4 text-xs text-slate-500">
              <span className="flex items-center gap-1.5"><span className="size-2 rounded-full bg-brand-600" />Submitted</span>
              <span className="flex items-center gap-1.5"><span className="size-2 rounded-full bg-emerald-500" />Decided</span>
            </div>
          </div>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={daily} margin={{ left: -20, right: 8, top: 4 }}>
                <defs>
                  <linearGradient id="sub" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#4f46e5" stopOpacity={0.25} />
                    <stop offset="100%" stopColor="#4f46e5" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#f1f5f9" vertical={false} />
                <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={false} interval={1} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} tickLine={false} axisLine={false} />
                <Tooltip contentStyle={{ borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12 }} />
                <Area type="monotone" dataKey="submitted" name="Submitted" stroke="#4f46e5" strokeWidth={2} fill="url(#sub)" />
                <Area type="monotone" dataKey="decided" name="Decided" stroke="#10b981" strokeWidth={2} fill="none" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-5">
          <h2 className="mb-4 text-sm font-semibold">Pipeline</h2>
          <ul className="space-y-3">
            {PIPELINE.map((s) => {
              const n = data.byStatus[s] ?? 0
              return (
                <li key={s}>
                  <Link to={`/claims?status=${s}`} className="group block">
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-600 group-hover:text-slate-900">{STATUS_META[s].label}</span>
                      <span className="tabular font-medium">{n}</span>
                    </div>
                    <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-brand-500" style={{ width: `${(n / maxPipeline) * 100}%` }} />
                    </div>
                  </Link>
                </li>
              )
            })}
          </ul>
        </div>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <div className="card lg:col-span-2">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
            <h2 className="text-sm font-semibold">
              {user?.role === 'ADJUSTER' ? 'My claims to review' : 'Waiting for manager approval'}
            </h2>
            <Link to="/claims" className="text-xs font-medium text-brand-600 hover:underline">Open queue</Link>
          </div>
          {urgent.data?.content.length ? (
            <ul className="divide-y divide-slate-100">
              {urgent.data.content.map((c) => (
                <li key={c.id}>
                  <Link to={`/claims/${c.id}`} className="flex items-center gap-4 px-5 py-3 hover:bg-slate-50">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{c.title}</div>
                      <div className="text-xs text-slate-500">{c.claimNumber} · {c.customerName}</div>
                    </div>
                    <RiskBadge level={c.riskLevel} />
                    <span className="tabular w-24 text-right text-sm font-medium">{inr(c.claimedAmount)}</span>
                    <StatusBadge status={c.status} />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="px-5 py-10 text-center text-sm text-slate-500">Nothing waiting. Nice.</p>
          )}
        </div>

        <div className="space-y-6">
          <div className="card p-5">
            <h2 className="text-sm font-semibold">Money</h2>
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex justify-between"><dt className="text-slate-500">Total claimed</dt><dd className="tabular font-medium">{compactInr(data.totalClaimed)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Approved / paid</dt><dd className="tabular font-medium text-emerald-600">{compactInr(data.totalApproved)}</dd></div>
            </dl>
            <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
              {(['LOW', 'MEDIUM', 'HIGH'] as const).map((r) => (
                <div key={r} className="rounded-lg bg-slate-50 py-2">
                  <div className="tabular text-lg font-semibold">{data.byRisk[r] ?? 0}</div>
                  <div className="text-slate-500">{titleCase(r)} risk</div>
                </div>
              ))}
            </div>
          </div>
          <div className="card p-5">
            <h2 className="mb-3 text-sm font-semibold">Adjuster workload</h2>
            <div className="h-40">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.adjusters} layout="vertical" margin={{ left: 0, right: 8 }}>
                  <XAxis type="number" hide allowDecimals={false} />
                  <YAxis type="category" dataKey="name" width={90} tick={{ fontSize: 11, fill: '#475569' }} tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12 }} />
                  <Bar dataKey="open" name="Open" stackId="a" fill="#6366f1" radius={[0, 0, 0, 0]} />
                  <Bar dataKey="decided" name="Decided" stackId="a" fill="#cbd5e1" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
