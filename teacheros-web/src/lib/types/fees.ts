export type FeeStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE'

export interface Fee {
  id: string
  studentId: string
  amountDue: number
  amountPaid: number
  dueDate: string
  status: FeeStatus
}

export interface CreateFeeRequest {
  studentId: string
  amountDue: number
  dueDate: string
}

export interface RecordPaymentRequest {
  amountPaid: number
  method?: string
}
