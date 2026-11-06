export type Role = 'CUSTOMER' | 'ADJUSTER' | 'MANAGER' | 'ADMIN'
export type PolicyType = 'HEALTH' | 'MOTOR' | 'PROPERTY' | 'TRAVEL'
export type ClaimStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'AI_REVIEW'
  | 'UNDER_REVIEW'
  | 'INFO_REQUESTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID'
export type ClaimAction = 'APPROVE' | 'REJECT' | 'REQUEST_INFO' | 'MARK_PAID' | 'ASSIGN_TO_ME' | 'REANALYZE'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface User {
  id: number
  email: string
  fullName: string
  phone: string | null
  role: Role
  active: boolean
  createdAt: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface Policy {
  id: number
  policyNumber: string
  holderId: number
  holderName: string
  type: PolicyType
  sumInsured: number
  premium: number
  startDate: string
  endDate: string
  status: 'ACTIVE' | 'LAPSED' | 'CANCELLED'
}

export interface ClaimSummary {
  id: number
  claimNumber: string
  title: string
  type: PolicyType
  status: ClaimStatus
  claimedAmount: number
  approvedAmount: number | null
  fraudScore: number | null
  riskLevel: RiskLevel | null
  customerId: number
  customerName: string | null
  assignedAdjusterId: number | null
  adjusterName: string | null
  slaDueAt: string
  slaBreached: boolean
  createdAt: string
  updatedAt: string
}

export interface ClaimDocument {
  id: number
  fileName: string
  contentType: string
  sizeBytes: number
  textExtracted: boolean
  uploadedAt: string
}

export interface ClaimComment {
  id: number
  authorId: number
  authorName: string
  authorRole: Role | null
  body: string
  internal: boolean
  createdAt: string
}

export interface TimelineEntry {
  id: number
  action: string
  fromStatus: ClaimStatus | null
  toStatus: ClaimStatus | null
  details: string | null
  actorName: string | null
  createdAt: string
}

export interface ClaimDetail {
  claim: ClaimSummary
  description: string
  incidentDate: string
  policy: { id: number; policyNumber: string; type: PolicyType; sumInsured: number; startDate: string; endDate: string }
  decisionReason: string | null
  decidedAt: string | null
  paidAt: string | null
  aiSummary: string | null
  aiExtracted: Record<string, unknown> | null
  aiProvider: string | null
  fraudFlags: string[] | null
  documents: ClaimDocument[]
  comments: ClaimComment[]
  timeline: TimelineEntry[]
  allowedActions: ClaimAction[]
  version: number
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface NotificationItem {
  id: number
  claimId: number | null
  message: string
  read: boolean
  createdAt: string
}

export interface NotificationFeed {
  unread: number
  items: NotificationItem[]
}

export interface AnalyticsSummary {
  totalClaims: number
  openClaims: number
  slaBreached: number
  highRisk: number
  totalClaimed: number
  totalApproved: number
  approvalRate: number
  avgDecisionHours: number
  byStatus: Record<string, number>
  byType: Record<string, number>
  byRisk: Record<string, number>
  daily: { date: string; submitted: number; decided: number }[]
  adjusters: { id: number; name: string; open: number; decided: number }[]
  generatedAt: string
}

export interface ClaimEvent {
  type: string
  claimId: number
  claimNumber: string
  status: ClaimStatus
  customerId: number
  assignedAdjusterId: number | null
  message: string | null
  at: string
}
