export type NotificationType = 'LOW_ATTENDANCE' | 'FEE_DUE' | 'FEE_OVERDUE'

export interface AppNotification {
  id: string
  studentId: string
  type: NotificationType
  title: string
  message: string
  read: boolean
  createdAt: string
}

export interface GenerateResult {
  created: number
}
