export interface Batch {
  id: string
  name: string
  subject: string | null
  scheduleSummary: string | null
}

export interface CreateBatchRequest {
  name: string
  subject?: string
  scheduleSummary?: string
}

// Mirrors BatchDtos.UpdateBatchRequest exactly.
export interface UpdateBatchRequest {
  name: string
  subject?: string
  scheduleSummary?: string
}
