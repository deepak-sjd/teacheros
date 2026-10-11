// --- AI doubt solving ---

export type DoubtSender = 'STUDENT' | 'AI'

export interface DoubtMessage {
  id: string
  sender: DoubtSender
  body: string
  createdAt: string
}

export interface AskDoubtRequest {
  question: string
}

export interface DoubtThreadResponse {
  messages: DoubtMessage[]
  questionsAskedToday: number
  dailyQuestionLimit: number
}

export interface AskDoubtResponse {
  question: DoubtMessage
  answer: DoubtMessage
}
