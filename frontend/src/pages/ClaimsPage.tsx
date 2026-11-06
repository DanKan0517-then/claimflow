import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { AlertTriangle, FileStack, PlusCircle, Search } from 'lucide-react'
import { Button, EmptyState, PageHeader, Pagination, RiskBadge, Spinner, StatusBadge } from '../components/ui'
import { api, qs } from '../lib/api'
import { isStaff, useAuth } from '../lib/auth'
import { date, inr, relative, titleCase } from '../lib/format'
import { ALL_STATUSES, STATUS_META } from '../lib/statusMeta'
import type { ClaimSummary, Page } from '../lib/types'

const SCOPES = [
  { key: 'all', label: 'All claims' },
  { key: 'mine', label: 'Assigned to me' },
  { key: 'unassigned', label: 'Unassigned' },
]

export default function ClaimsPage() {
  const { user } = useAuth()
  const staff = isStaff(user)
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')

  const filters = {
    status: params.get('status') ?? '',
    scope: params.get('scope') ?? 'all',
    minFraud: params.get('minFraud') ?? '',
    q: params.get('q') ?? '',
    page: Number(params.get('page') ?? 0),
    sort: params.get('sort') ?? 'createdAt',
  }
  const update = (patch: Record<string, string | number>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v === '' || v === undefined) next.delete(k)
      else next.set(k, String(v))
    }
    if (!('page' in patch)) next.delete('page')
    setParams(next, { replace: true })
  }

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['claims', filters],
    queryFn: () =>
      api.get<Page<ClaimSummary>>(
        `/claims${qs({ ...filters, scope: staff ? filters.scope : undefined, size: 15, dir: filters.sort === 'slaDueAt' ? 'asc' : 'desc' })}`,
      ),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader
        title={staff ? 'Work queue' : 'My claims'}
        subtitle={staff ? 'Every submitted claim, newest first. Updates live.' : 'Track the status of everything you have filed.'}
        actions={
          !staff && (
            <Link to="/claims/new">
              <Button>
                <PlusCircle className="size-4" /> File a claim
              </Button>
            </Link>
          )
        }
      />

      <div className="card overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 p-3">
          {staff && (
            <div className="flex rounded-lg bg-slate-100 p-0.5">
              {SCOPES.map((s) => (
                <button
                  key={s.key}
                  onClick={() => update({ scope: s.key })}
                  className={clsx(
                    'rounded-md px-3 py-1.5 text-sm font-medium transition',
                    filters.scope === s.key ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
                  )}
                >
                  {s.label}
                </button>
              ))}
            </div>
          )}
          <form
            className="relative min-w-48 flex-1"
            onSubmit={(e) => {
              e.preventDefault()
              update({ q: search.trim() })
            }}
          >
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <input className="input pl-9" placeholder="Search claim number or title" value={search} onChange={(e) => setSearch(e.target.value)} />
          </form>
          <select className="input w-auto" value={filters.status} onChange={(e) => update({ status: e.target.value })} aria-label="Status">
            <option value="">All statuses</option>
            {ALL_STATUSES.filter((s) => staff ? s !== 'DRAFT' : true).map((s) => (
              <option key={s} value={s}>{STATUS_META[s].label}</option>
            ))}
          </select>
          {staff && (
            <>
              <select className="input w-auto" value={filters.minFraud} onChange={(e) => update({ minFraud: e.target.value })} aria-label="Risk">
                <option value="">Any risk</option>
                <option value="40">Medium risk +</option>
                <option value="70">High risk only</option>
              </select>
              <select className="input w-auto" value={filters.sort} onChange={(e) => update({ sort: e.target.value })} aria-label="Sort">
                <option value="createdAt">Newest</option>
                <option value="slaDueAt">SLA due soonest</option>
                <option value="fraudScore">Highest risk</option>
                <option value="claimedAmount">Largest amount</option>
              </select>
            </>
          )}
          {isFetching && !isLoading && <Spinner className="size-4" />}
        </div>

        {isLoading ? (
          <div className="flex h-48 items-center justify-center"><Spinner /></div>
        ) : !data?.content.length ? (
          <EmptyState icon={<FileStack className="size-6" />} title="No claims here">
            {staff ? 'Try a different filter.' : 'When you file a claim it will show up here.'}
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-2.5">Claim</th>
                  {staff && <th className="px-4 py-2.5">Customer</th>}
                  <th className="px-4 py-2.5 text-right">Amount</th>
                  <th className="px-4 py-2.5">Status</th>
                  {staff && <th className="px-4 py-2.5">Risk</th>}
                  {staff && <th className="px-4 py-2.5">Adjuster</th>}
                  <th className="px-4 py-2.5">{staff ? 'SLA' : 'Filed'}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((c) => (
                  <tr key={c.id} onClick={() => navigate(`/claims/${c.id}`)} className="cursor-pointer hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-900">{c.title}</div>
                      <div className="text-xs text-slate-500">
                        {c.claimNumber} · {titleCase(c.type)}
                      </div>
                    </td>
                    {staff && <td className="px-4 py-3 text-slate-700">{c.customerName}</td>}
                    <td className="tabular px-4 py-3 text-right font-medium">
                      {inr(c.claimedAmount)}
                      {c.approvedAmount !== null && c.approvedAmount !== c.claimedAmount && (
                        <div className="text-xs font-normal text-emerald-600">{inr(c.approvedAmount)} approved</div>
                      )}
                    </td>
                    <td className="px-4 py-3"><StatusBadge status={c.status} customer={!staff} /></td>
                    {staff && <td className="px-4 py-3"><RiskBadge level={c.riskLevel} score={c.fraudScore} /></td>}
                    {staff && <td className="px-4 py-3 text-slate-600">{c.adjusterName ?? <span className="text-slate-400">Unassigned</span>}</td>}
                    <td className="whitespace-nowrap px-4 py-3 text-slate-600">
                      {staff ? (
                        STATUS_META[c.status] && ['APPROVED', 'REJECTED', 'PAID'].includes(c.status) ? (
                          <span className={c.slaBreached ? 'text-rose-600' : 'text-slate-400'}>{c.slaBreached ? 'Missed' : 'Met'}</span>
                        ) : (
                          <span className={clsx('inline-flex items-center gap-1', c.slaBreached && 'font-medium text-rose-600')}>
                            {c.slaBreached && <AlertTriangle className="size-3.5" />}
                            {c.slaBreached ? `overdue ${relative(c.slaDueAt).replace(' ago', '')}` : `due ${relative(c.slaDueAt)}`}
                          </span>
                        )
                      ) : (
                        date(c.createdAt)
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => update({ page: p })} />}
      </div>
    </>
  )
}
