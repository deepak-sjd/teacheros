import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Ledger, Row, Select, StatBlock } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { Batch, BatchRanking, Page, ReportSummary } from '@/lib/types'
import { formatCurrency, formatDate, todayIso } from '@/lib/format'

type Period = 'today' | 'week' | 'month' | 'custom'

/**
 * Daily/weekly/monthly is purely a UI convenience for picking a from/to
 * range - the backend only ever sees plain dates (GET /api/v1/reports/
 * summary?from=&to=&batchId=). See ReportService: everything is computed
 * on demand from existing attendance/fee/mark records, nothing is stored
 * or cached, so there's no separate "daily report" entity to manage.
 */
function rangeFor(period: Period): { from: string; to: string } {
  const today = new Date()
  const to = todayIso()

  if (period === 'today') return { from: to, to }

  if (period === 'week') {
    const day = today.getDay() === 0 ? 7 : today.getDay() // Mon=1..Sun=7
    const monday = new Date(today)
    monday.setDate(today.getDate() - (day - 1))
    return { from: monday.toISOString().slice(0, 10), to }
  }

  // month
  const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1)
  return { from: firstOfMonth.toISOString().slice(0, 10), to }
}

export function ReportsPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [period, setPeriod] = useState<Period>('month')
  const [from, setFrom] = useState(rangeFor('month').from)
  const [to, setTo] = useState(rangeFor('month').to)
  const [report, setReport] = useState<ReportSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [ranking, setRanking] = useState<BatchRanking | null>(null)
  const [rankingLoading, setRankingLoading] = useState(false)

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => setBatches(res.content))
  }, [])

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Class ranking is per-batch (a leaderboard needs one roster, not a mix
  // of several), so it only loads once a specific batch is selected -
  // "All batches" clears it rather than showing something misleading.
  useEffect(() => {
    if (!batchId) {
      setRanking(null)
      return
    }
    setRankingLoading(true)
    api
      .get<BatchRanking>(`/api/v1/tests/batches/${batchId}/ranking`)
      .then(setRanking)
      .catch(() => setRanking(null))
      .finally(() => setRankingLoading(false))
  }, [batchId])

  function handlePeriodChange(next: Period) {
    setPeriod(next)
    if (next !== 'custom') {
      const r = rangeFor(next)
      setFrom(r.from)
      setTo(r.to)
    }
  }

  function load() {
    setError(null)
    setLoading(true)
    api
      .get<ReportSummary>('/api/v1/reports/summary', { from, to, batchId: batchId || undefined })
      .then(setReport)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not generate the report.'))
      .finally(() => setLoading(false))
  }

  async function handleDownloadPdf() {
    setDownloading(true)
    try {
      await api.downloadFile(
        `/api/v1/reports/summary.pdf?from=${from}&to=${to}${batchId ? `&batchId=${batchId}` : ''}`,
        `report-${from}-to-${to}.pdf`,
      )
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not download the report.')
    } finally {
      setDownloading(false)
    }
  }

  return (
    <Layout>
      <PageHeader
        title="Reports"
        description="Attendance, fees, and marks for a period."
        action={
          report && (
            <Button variant="secondary" onClick={handleDownloadPdf} disabled={downloading}>
              {downloading ? 'Preparing PDF…' : 'Download PDF'}
            </Button>
          )
        }
      />

      <div className="flex flex-wrap items-end gap-3 mb-6 print:hidden">
        <div className="w-40">
          <Select label="Period" value={period} onChange={(e) => handlePeriodChange(e.target.value as Period)}>
            <option value="today">Today</option>
            <option value="week">This week</option>
            <option value="month">This month</option>
            <option value="custom">Custom range</option>
          </Select>
        </div>
        {period === 'custom' && (
          <>
            <div className="w-40">
              <label className="block">
                <span className="block text-sm font-medium text-ink mb-1.5">From</span>
                <input
                  type="date"
                  value={from}
                  max={to}
                  onChange={(e) => setFrom(e.target.value)}
                  className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink focus:border-ink focus:ring-1 focus:ring-ink outline-none"
                />
              </label>
            </div>
            <div className="w-40">
              <label className="block">
                <span className="block text-sm font-medium text-ink mb-1.5">To</span>
                <input
                  type="date"
                  value={to}
                  min={from}
                  max={todayIso()}
                  onChange={(e) => setTo(e.target.value)}
                  className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink focus:border-ink focus:ring-1 focus:ring-ink outline-none"
                />
              </label>
            </div>
          </>
        )}
        <div className="w-56">
          <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
            <option value="">All batches</option>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </Select>
        </div>
        <Button onClick={load} disabled={loading}>
          {loading ? 'Generating…' : 'Generate'}
        </Button>
      </div>

      {error && <ErrorBanner message={error} />}

      {!report ? (
        loading ? (
          <p className="text-slate text-sm">Generating…</p>
        ) : (
          <EmptyState title="No report yet" description="Pick a period and hit Generate." />
        )
      ) : (
        <div className="space-y-8">
          <p className="text-sm text-slate">
            {formatDate(report.from)} – {formatDate(report.to)}
            {batchId && ` · ${batches.find((b) => b.id === batchId)?.name ?? ''}`}
          </p>

          <section>
            <h3 className="font-display text-xl text-ink mb-3">Attendance</h3>
            <div className="grid grid-cols-3 gap-4 mb-4">
              <StatBlock label="Sessions held" value={String(report.attendance.totalSessions)} />
              <StatBlock label="Present records" value={String(report.attendance.presentCount)} />
              <StatBlock label="Attendance %" value={`${report.attendance.attendancePercentage}%`} />
            </div>
            {report.attendance.byBatch.length === 0 ? (
              <EmptyState title="No sessions in this period" />
            ) : (
              <Ledger columns={['Batch', 'Sessions', 'Present', 'Attendance %']}>
                {report.attendance.byBatch.map((row) => (
                  <Row key={row.batchId}>
                    <Cell>{row.batchName}</Cell>
                    <Cell>{row.sessions}</Cell>
                    <Cell>{row.presentCount}</Cell>
                    <Cell>{row.attendancePercentage}%</Cell>
                  </Row>
                ))}
              </Ledger>
            )}
          </section>

          <section>
            <h3 className="font-display text-xl text-ink mb-3">Fees</h3>
            <p className="text-xs text-slate-light mb-3">
              Fees aren't tied to a batch, so these figures are tenant-wide even when a batch is selected above.
            </p>
            <div className="grid grid-cols-4 gap-4">
              <StatBlock label="Collected this period" value={formatCurrency(report.fees.collectedInPeriod)} />
              <StatBlock label="Outstanding" value={formatCurrency(report.fees.totalOutstanding)} />
              <StatBlock label="Pending fees" value={String(report.fees.pendingCount)} />
              <StatBlock label="Overdue fees" value={String(report.fees.overdueCount)} />
            </div>
          </section>

          <section>
            <h3 className="font-display text-xl text-ink mb-3">Marks</h3>
            <div className="grid grid-cols-3 gap-4 mb-4">
              <StatBlock label="Tests conducted" value={String(report.marks.testsConducted)} />
              <StatBlock label="Graded entries" value={String(report.marks.gradedEntries)} />
              <StatBlock label="Average score" value={`${report.marks.averageScorePercentage}%`} />
            </div>
            {report.marks.byBatch.length === 0 ? (
              <EmptyState title="No tests in this period" />
            ) : (
              <Ledger columns={['Batch', 'Tests', 'Graded', 'Average %']}>
                {report.marks.byBatch.map((row) => (
                  <Row key={row.batchId}>
                    <Cell>{row.batchName}</Cell>
                    <Cell>{row.testsConducted}</Cell>
                    <Cell>{row.gradedEntries}</Cell>
                    <Cell>{row.averageScorePercentage}%</Cell>
                  </Row>
                ))}
              </Ledger>
            )}
          </section>

          {batchId && (
            <section>
              <h3 className="font-display text-xl text-ink mb-3">Class ranking</h3>
              {rankingLoading ? (
                <p className="text-slate text-sm">Loading…</p>
              ) : !ranking || ranking.entries.length === 0 ? (
                <EmptyState title="No students in this batch" />
              ) : (
                <>
                  <p className="text-xs text-slate-light mb-3">
                    Ranked by total marks scored across every graded test in this batch, all-time — not limited to
                    the period selected above. {ranking.entries.filter((e) => e.rank !== null).length} of{' '}
                    {ranking.totalStudents} student{ranking.totalStudents === 1 ? '' : 's'} have at least one graded
                    test.
                  </p>
                  <Ledger columns={['Rank', 'Student', 'Score', 'Average %']}>
                    {ranking.entries.map((e) => (
                      <Row key={e.studentId}>
                        <Cell className="font-medium">{e.rank ? `#${e.rank}` : '\u2014'}</Cell>
                        <Cell>{e.studentName}</Cell>
                        <Cell>{e.rank ? `${e.totalMarksObtained} / ${e.totalMaxMarks}` : '\u2014'}</Cell>
                        <Cell>{e.percentage !== null ? `${e.percentage}%` : '\u2014'}</Cell>
                      </Row>
                    ))}
                  </Ledger>
                </>
              )}
            </section>
          )}
        </div>
      )}
    </Layout>
  )
}
