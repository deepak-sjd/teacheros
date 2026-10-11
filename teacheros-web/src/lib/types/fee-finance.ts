// Fee heads, instalment plans, structures, discounts, rules, ledger and
// accountant reports. Mirrors FeeStructureDtos / FeeFinanceDtos on the backend.
// BigDecimal fields arrive as JSON numbers.
import type { FeeStatus } from './fees'

export type FeeAmountType = 'NONE' | 'FIXED' | 'PERCENT'
export type FeeLineType = 'STANDARD' | 'INSTALMENT' | 'LATE_FEE'

// ---- heads ----
export interface FeeHead {
  id: string
  name: string
  code: string | null
  siblingConcessionEligible: boolean
  active: boolean
  sortOrder: number
}

// ---- instalment plans ----
export interface Instalment {
  instalmentNo: number
  dueDate: string
  percentage: number
}

export interface InstalmentPlan {
  id: string
  name: string
  numberOfInstalments: number
  active: boolean
  instalments: Instalment[]
}

// ---- structures ----
export interface FeeStructureItem {
  headId: string
  headName: string
  amount: number
}

export interface FeeStructure {
  id: string
  name: string
  academicYear: string
  grade: string | null
  batchId: string | null
  planId: string
  planName: string
  active: boolean
  items: FeeStructureItem[]
  totalAmount: number
}

// ---- assignment ----
export interface AssignmentPreviewRow {
  studentId: string
  studentName: string
  grade: string | null
  instalmentNo: number
  dueDate: string
  grossAmount: number
  discountAmount: number
  amountDue: number
  discountNotes: string | null
  alreadyAssigned: boolean
}

export interface AssignmentPreview {
  structureId: string
  structureName: string
  studentCount: number
  feesToCreate: number
  feesAlreadyAssigned: number
  totalToCharge: number
  totalDiscount: number
  rows: AssignmentPreviewRow[]
}

export interface AssignmentResult {
  studentCount: number
  created: number
  skipped: number
  totalCharged: number
}

// ---- discounts & rules ----
export interface FeeDiscount {
  id: string
  studentId: string
  studentName: string
  feeHeadId: string | null
  feeHeadName: string | null
  discountType: FeeAmountType
  value: number
  reason: string
  approvedByUserId: string | null
  active: boolean
  createdAt: string
}

export interface FeeRules {
  siblingConcessionType: FeeAmountType
  siblingConcessionValue: number
  lateFeeType: FeeAmountType
  lateFeeValue: number
  lateFeeGraceDays: number
  updatedAt: string | null
}

// ---- ledger ----
export interface LedgerHeadLine {
  headName: string
  grossAmount: number
  discountAmount: number
}

export interface FeeLedgerLine {
  feeId: string
  lineType: FeeLineType
  instalmentNo: number | null
  description: string
  dueDate: string
  grossAmount: number
  discountAmount: number
  amountDue: number
  amountPaid: number
  status: FeeStatus
  heads: LedgerHeadLine[]
}

// ---- accountant reports ----
export interface MethodTotal {
  method: string
  count: number
  amount: number
}

export interface PaymentEntry {
  paidAt: string
  studentName: string
  method: string
  amount: number
}

export interface DailyCollection {
  date: string
  totalCollected: number
  paymentCount: number
  byMethod: MethodTotal[]
  payments: PaymentEntry[]
}

export interface OutstandingRow {
  group: string
  batchId: string | null
  studentCount: number
  feeCount: number
  totalDue: number
  totalPaid: number
  outstanding: number
}

export interface OutstandingReport {
  groupBy: string
  asOf: string
  totalOutstanding: number
  rows: OutstandingRow[]
}

export interface DefaulterRow {
  studentId: string
  studentName: string
  grade: string | null
  batchName: string | null
  guardianPhone: string | null
  overdueFeeCount: number
  overdueAmount: number
  oldestDueDate: string
  maxDaysOverdue: number
}

export interface DefaultersReport {
  asOf: string
  totalOverdue: number
  rows: DefaulterRow[]
}

export interface HeadCollectionRow {
  head: string
  amount: number
}

export interface HeadCollectionReport {
  from: string
  to: string
  totalCollected: number
  rows: HeadCollectionRow[]
}
