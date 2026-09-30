import { useState } from 'react'
import { Modal, Button, ErrorBanner } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import type { CreateOrderResponse, Fee } from '@/lib/types'

declare global {
  interface Window {
    Razorpay: new (options: Record<string, unknown>) => { open: () => void }
  }
}

let razorpayScriptPromise: Promise<void> | null = null

// Loaded lazily, only when someone actually opens this modal in real
// (non-mock) mode - most schools testing this feature will stay in mock
// mode and never need Razorpay's script at all.
function loadRazorpayScript(): Promise<void> {
  if (razorpayScriptPromise) return razorpayScriptPromise
  razorpayScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Could not load the payment page. Check your connection and try again.'))
    document.body.appendChild(script)
  })
  return razorpayScriptPromise
}

export function PayFeeModal({
  open,
  fee,
  onClose,
  onPaid,
}: {
  open: boolean
  fee: Fee | null
  onClose: () => void
  onPaid: () => void
}) {
  const [stage, setStage] = useState<'confirm' | 'processing' | 'error'>('confirm')
  const [error, setError] = useState<string | null>(null)
  const [order, setOrder] = useState<CreateOrderResponse | null>(null)

  if (!fee) return null
  const balance = fee.amountDue - fee.amountPaid

  async function startPayment() {
    setError(null)
    setStage('processing')
    try {
      const res = await api.post<CreateOrderResponse>('/api/v1/payments/orders', {
        feeId: fee!.id,
        amount: balance,
      })
      setOrder(res)

      if (res.provider === 'mock') {
        // No real gateway configured - stop here and let the person
        // confirm the simulated payment themselves (see the JSX below),
        // rather than auto-completing it silently.
        return
      }

      await loadRazorpayScript()
      const razorpay = new window.Razorpay({
        key: res.keyId,
        amount: res.amountInPaise,
        currency: res.currency,
        order_id: res.orderId,
        name: 'TeacherOS',
        description: 'Fee payment',
        handler: (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) =>
          verifyPayment(response.razorpay_order_id, response.razorpay_payment_id, response.razorpay_signature),
        modal: {
          ondismiss: () => setStage('confirm'),
        },
      })
      razorpay.open()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start the payment.')
      setStage('error')
    }
  }

  async function verifyPayment(orderId: string, paymentId: string, signature?: string) {
    setStage('processing')
    try {
      await api.post(`/api/v1/payments/verify`, {
        feeId: fee!.id,
        orderId,
        paymentId,
        signature,
      })
      onPaid()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Payment could not be verified.')
      setStage('error')
    }
  }

  function handleClose() {
    setStage('confirm')
    setError(null)
    setOrder(null)
    onClose()
  }

  return (
    <Modal open={open} onClose={handleClose} title="Pay fee">
      {error && <ErrorBanner message={error} />}

      {stage === 'confirm' && !order && (
        <div>
          <p className="text-slate text-sm mb-4">
            Outstanding balance: <span className="font-medium text-ink">{formatCurrency(balance)}</span>
          </p>
          <div className="flex gap-2 justify-end">
            <Button variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Button onClick={startPayment}>Pay {formatCurrency(balance)}</Button>
          </div>
        </div>
      )}

      {stage === 'processing' && order?.provider === 'mock' && (
        <div>
          <p className="text-sm text-slate mb-1">
            No live payment gateway is connected yet, so this simulates the payment instead of charging a real card.
          </p>
          <p className="text-sm text-ink font-medium mb-4">{formatCurrency(balance)} · Order {order.orderId}</p>
          <div className="flex gap-2 justify-end">
            <Button variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Button onClick={() => verifyPayment(order.orderId, 'mock_payment_' + Date.now())}>
              Simulate successful payment
            </Button>
          </div>
        </div>
      )}

      {stage === 'processing' && (!order || order.provider === 'razorpay') && (
        <p className="text-slate text-sm text-center py-6">Opening secure payment…</p>
      )}

      {stage === 'error' && (
        <div className="flex justify-end">
          <Button variant="secondary" onClick={handleClose}>
            Close
          </Button>
        </div>
      )}
    </Modal>
  )
}
