import { useEffect, useRef, useState } from 'react'
import jsQR from 'jsqr'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, ErrorBanner, Input } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { StudentAttendanceRecord } from '@/lib/types'

/**
 * Scans the QR a teacher shows on-screen (AttendanceQrModal) using the
 * device camera, decoded fully client-side with jsQR - no network call
 * until the decoded token is actually submitted. Manual token entry is
 * the fallback for devices without camera access or in low light.
 */
export function StudentCheckIn() {
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const rafRef = useRef<number | null>(null)

  const [scanning, setScanning] = useState(false)
  const [cameraError, setCameraError] = useState<string | null>(null)
  const [manualToken, setManualToken] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<StudentAttendanceRecord | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function startCamera() {
    setCameraError(null)
    setError(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play()
      }
      setScanning(true)
      tick()
    } catch {
      setCameraError('Camera access is unavailable. Enter the code shown below the QR image instead.')
    }
  }

  function stopCamera() {
    if (rafRef.current) cancelAnimationFrame(rafRef.current)
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    setScanning(false)
  }

  function tick() {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (video && canvas && video.readyState === video.HAVE_ENOUGH_DATA) {
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      const ctx = canvas.getContext('2d')
      if (ctx) {
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
        const code = jsQR(imageData.data, imageData.width, imageData.height)
        if (code?.data) {
          stopCamera()
          submitToken(code.data)
          return
        }
      }
    }
    rafRef.current = requestAnimationFrame(tick)
  }

  useEffect(() => () => stopCamera(), [])

  async function submitToken(token: string) {
    setSubmitting(true)
    setError(null)
    try {
      const record = await api.post<StudentAttendanceRecord>('/api/v1/attendance/self-checkin', { token })
      setResult(record)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not mark attendance.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Layout>
      <PageHeader title="Check in" description="Scan the QR code your teacher is showing to mark yourself present." />

      {result ? (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 border border-success/20 max-w-md">
          Marked present. You're all set for today.
        </div>
      ) : (
        <div className="max-w-md">
          {error && <ErrorBanner message={error} />}

          {!scanning ? (
            <Button onClick={startCamera}>Open camera</Button>
          ) : (
            <div className="relative rounded-lg overflow-hidden border border-line">
              <video ref={videoRef} className="w-full" muted playsInline />
              <canvas ref={canvasRef} className="hidden" />
              <Button variant="secondary" className="absolute bottom-3 right-3" onClick={stopCamera}>
                Cancel
              </Button>
            </div>
          )}
          {cameraError && <p className="text-sm text-slate mt-3">{cameraError}</p>}

          <div className="mt-6 pt-6 border-t border-line">
            <p className="text-sm text-slate mb-2">Or enter the code manually:</p>
            <div className="flex gap-2">
              <div className="flex-1">
                <Input
                  value={manualToken}
                  onChange={(e) => setManualToken(e.target.value)}
                  placeholder="Paste code here"
                />
              </div>
              <Button
                variant="secondary"
                disabled={!manualToken || submitting}
                onClick={() => submitToken(manualToken)}
              >
                {submitting ? 'Checking in…' : 'Check in'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
