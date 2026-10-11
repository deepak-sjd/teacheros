// --- Payment gateway (fee payments via Razorpay or the free mock provider) ---
// Mirrors backend/.../modules/payment/dto/PaymentDtos.java exactly.

export interface CreateOrderResponse {
  // "mock": no real gateway configured - frontend shows its own "Simulate
  // successful payment" button instead of opening Razorpay Checkout.
  // "razorpay": real gateway - frontend loads checkout.js and opens it
  // using keyId below.
  provider: 'mock' | 'razorpay'
  orderId: string
  amountInPaise: number
  currency: string
  // Razorpay's PUBLIC key - safe to expose to the frontend. Null in mock
  // mode, where it's never used.
  keyId: string | null
  feeId: string
}

export interface VerifyPaymentRequest {
  feeId: string
  orderId: string
  paymentId: string
  // Blank/omitted in mock mode - there's no real signature to check since
  // no real money or real Razorpay order is involved.
  signature?: string
}

// --- Fee receipts ---
// Mirrors backend/.../modules/payment/dto/ReceiptDtos.java exactly.

export interface FeePaymentRecord {
  id: string
  receiptNumber: string // e.g. "RCP-000123"
  amountPaid: number
  paidAt: string
  method: string | null
}
