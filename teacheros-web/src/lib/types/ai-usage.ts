// --- Owner: AI usage dashboard ---
// Mirrors backend/.../modules/ai/dto/AiUsageDtos.java exactly.

export interface AiFeatureSummary {
  feature: string
  label: string
  calls: number
  failed: number
  failureRate: number
  inputTokens: number
  outputTokens: number
  estimatedCost: number
  usedToday: number
  dailyLimit: number
}

export interface AiDailyUsageRow {
  date: string
  feature: string
  calls: number
  failed: number
}

export interface AiUsageDashboardResponse {
  from: string
  to: string
  costCurrency: string
  totalCalls: number
  totalFailed: number
  failureRate: number
  estimatedCost: number
  features: AiFeatureSummary[]
  daily: AiDailyUsageRow[]
}
