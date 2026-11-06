import type { ClaimStatus, RiskLevel } from './types'

export const STATUS_META: Record<ClaimStatus, { label: string; tone: string; customerLabel?: string }> = {
  DRAFT: { label: 'Draft', tone: 'bg-slate-100 text-slate-700 ring-slate-200' },
  SUBMITTED: { label: 'Submitted', tone: 'bg-sky-50 text-sky-700 ring-sky-200' },
  AI_REVIEW: { label: 'AI review', tone: 'bg-violet-50 text-violet-700 ring-violet-200', customerLabel: 'Being checked' },
  UNDER_REVIEW: { label: 'Under review', tone: 'bg-amber-50 text-amber-800 ring-amber-200' },
  INFO_REQUESTED: { label: 'Info requested', tone: 'bg-orange-50 text-orange-700 ring-orange-200', customerLabel: 'Action needed' },
  PENDING_APPROVAL: { label: 'Pending approval', tone: 'bg-indigo-50 text-indigo-700 ring-indigo-200', customerLabel: 'Final approval' },
  APPROVED: { label: 'Approved', tone: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  REJECTED: { label: 'Rejected', tone: 'bg-rose-50 text-rose-700 ring-rose-200' },
  PAID: { label: 'Paid', tone: 'bg-emerald-600 text-white ring-emerald-600' },
}

export const RISK_META: Record<RiskLevel, { label: string; tone: string; bar: string }> = {
  LOW: { label: 'Low risk', tone: 'bg-emerald-50 text-emerald-700 ring-emerald-200', bar: 'bg-emerald-500' },
  MEDIUM: { label: 'Medium risk', tone: 'bg-amber-50 text-amber-800 ring-amber-200', bar: 'bg-amber-500' },
  HIGH: { label: 'High risk', tone: 'bg-rose-50 text-rose-700 ring-rose-200', bar: 'bg-rose-500' },
}

export const ALL_STATUSES = Object.keys(STATUS_META) as ClaimStatus[]

/** Customer-facing progress steps. */
export const CUSTOMER_STEPS: { key: string; label: string; statuses: ClaimStatus[] }[] = [
  { key: 'submitted', label: 'Submitted', statuses: ['SUBMITTED', 'AI_REVIEW'] },
  { key: 'review', label: 'In review', statuses: ['UNDER_REVIEW', 'INFO_REQUESTED', 'PENDING_APPROVAL'] },
  { key: 'decision', label: 'Decision', statuses: ['APPROVED', 'REJECTED'] },
  { key: 'paid', label: 'Paid', statuses: ['PAID'] },
]
