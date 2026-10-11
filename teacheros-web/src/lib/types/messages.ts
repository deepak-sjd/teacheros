// --- Messages (parent-teacher messaging) ---
// Mirrors backend/.../modules/message/dto/MessageDtos.java exactly.

export type SenderRole = 'PARENT' | 'STAFF'

export interface Message {
  id: string
  senderRole: SenderRole
  senderName: string
  body: string
  // Whether the CALLER's side sent this message, resolved server-side -
  // see MessageDtos.MessageResponse's javadoc. Lets the UI align bubbles
  // without needing to know the viewer's own role.
  mine: boolean
  createdAt: string
}
