import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner, Select } from '@/components/ui'
import { BarChart, LineChart } from '@/components/Charts'
import { api, ApiError } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import type {
  AttendanceTrendResponse,
  Batch,
  FeeCollectionTrendResponse,
  Page,
  SubjectPerformanceResponse,
} from '@/lib/types'

const RANGE_OPTIONS = [
  { label: 'Last 7 days', days: 7 },
  { label: 'Last 14 days', days: 14 },
  { label: 'Last 30 days', days: 30 },
  { label: 'Last 90 days', days: 90 },
]

function shortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

export function AnalyticsPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [days, setDays] = useState(14)

  const [attendanceTrend, setAttendanceTrend] = useState<AttendanceTrendResponse | null>(null)
  const [feeTrend, setFeeTrend] = useState<FeeCollectionTrendResponse | null>(null)
  const [subjectPerformance, setSubjectPerformance] = useState<SubjectPerformanceResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => setBatches(res.content))
  }, [])

  useEffect(() => {
    setLoading(true)
    setError(null)
    Promise.all([
      api.get<AttendanceTrendResponse>('/api/v1/analytics/attendance-trend', {
        days,
        batchId: batchId || undefined,
      }),
      // No batchId param - fee payments aren't tied to a batch (see
      // FeeCollectionTrendResponse's comment in lib/types.ts), so this
      // figure is always tenant-wide regardless of the batch filter above.
      api.get<FeeCollectionTrendResponse>('/api/v1/analytics/fee-collection-trend', { days }),
      api.get<SubjectPerformanceResponse>('/api/v1/analytics/subject-performance', {
        days,
        batchId: batchId || undefined,
      }),
    ])
      .then(([att, fee, subj]) => {
        setAttendanceTrend(att)
        setFeeTrend(fee)
        setSubjectPerformance(subj)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load analytics.'))
      .finally(() => setLoading(false))
  }, [days, batchId])

  const feeMax = feeTrend ? Math.max(...feeTrend.points.map((p) => p.amountCollected), 1) : 1

  return (
    <Layout>
      <PageHeader title="Analytics" description="Trends across attendance, fee collection, and test performance." />

      <div className="flex flex-wrap items-end gap-3 mb-8">
        <div className="w-44">
          <Select label="Range" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {RANGE_OPTIONS.map((r) => (
              <option key={r.days} value={r.days}>
                {r.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-56">
          <Select label="Batch (attendance & marks only)" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
            <option value="">All batches</option>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : (
        <div className="space-y-8">
          <section className="border border-line rounded-lg bg-white p-5">
            <h2 className="font-display text-lg text-ink mb-1">Attendance trend</h2>
            <p className="text-xs text-slate-light mb-4">
              Days with a class held, over the selected range{batchId ? '' : ', across all batches'}.
            </p>
            {!attendanceTrend || attendanceTrend.points.length === 0 ? (
              <EmptyState title="No attendance recorded yet" description="Mark attendance to see the trend here." />
            ) : (
              <LineChart
                data={attendanceTrend.points.map((p) => ({ label: shortDate(p.date), value: p.attendancePercentage }))}
                formatValue={(v) => `${v}%`}
                yMax={100}
              />
            )}
          </section>

          <section className="border border-line rounded-lg bg-white p-5">
            <h2 className="font-display text-lg text-ink mb-1">Fee collection trend</h2>
            <p className="text-xs text-slate-light mb-4">
              Amount collected per day, tenant-wide — fees aren't tied to a batch, so the batch filter above doesn't
              apply here.
            </p>
            {!feeTrend || feeTrend.points.length === 0 ? (
              <EmptyState title="No payments recorded yet" description="Record or collect a payment to see the trend here." />
            ) : (
              <LineChart
                data={feeTrend.points.map((p) => ({ label: shortDate(p.date), value: p.amountCollected }))}
                formatValue={(v) => formatCurrency(v)}
                yMax={feeMax}
                lineColor="var(--color-success, var(--color-ink))"
              />
            )}
          </section>

          <section className="border border-line rounded-lg bg-white p-5">
            <h2 className="font-display text-lg text-ink mb-1">Performance by subject</h2>
            <p className="text-xs text-slate-light mb-4">
              Average score across all graded tests in the selected range{batchId ? '' : ', across all batches'}.
            </p>
            {!subjectPerformance || subjectPerformance.points.length === 0 ? (
              <EmptyState title="No tests graded yet" description="Grade a test to see subject averages here." />
            ) : (
              <BarChart
                data={subjectPerformance.points.map((p) => ({ label: p.subject, value: p.averagePercentage }))}
                maxValue={100}
                formatValue={(v) => `${v}%`}
                barColor="var(--color-chalk)"
              />
            )}
          </section>
        </div>
      )}
    </Layout>
  )
}
