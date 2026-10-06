import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import type { AttendanceTrendResponse, Batch, Fee, Page, Student, SubjectPerformanceResponse } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { StatBlock, StatBlockSkeleton, Button, Ledger, Row, Cell, EmptyState, Skeleton, LedgerSkeleton } from '@/components/ui'
import { BarChart, LineChart } from '@/components/Charts'
import { formatCurrency } from '@/lib/format'

export function Overview() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [students, setStudents] = useState<Page<Student> | null>(null)
  const [pendingFees, setPendingFees] = useState<Page<Fee> | null>(null)
  const [attendanceTrend, setAttendanceTrend] = useState<AttendanceTrendResponse | null>(null)
  const [subjectPerformance, setSubjectPerformance] = useState<SubjectPerformanceResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      api.get<Page<Batch>>('/api/v1/batches', { size: 50 }),
      api.get<Page<Student>>('/api/v1/students', { size: 50 }),
      api.get<Page<Fee>>('/api/v1/fees/pending', { size: 10 }),
      api.get<AttendanceTrendResponse>('/api/v1/analytics/attendance-trend', { days: 14 }),
      api.get<SubjectPerformanceResponse>('/api/v1/analytics/subject-performance', { days: 30 }),
    ])
      .then(([b, s, f, trend, subjects]) => {
        setBatches(b.content)
        setStudents(s)
        setPendingFees(f)
        setAttendanceTrend(trend)
        setSubjectPerformance(subjects)
      })
      .finally(() => setLoading(false))
  }, [])

  const totalDue = (pendingFees?.content ?? []).reduce((sum, f) => sum + (f.amountDue - f.amountPaid), 0)

  return (
    <Layout>
      <PageHeader title="Overview" description="Today's snapshot across your batches, students, and fees." />

      {loading ? (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-10">
            <StatBlockSkeleton />
            <StatBlockSkeleton />
            <StatBlockSkeleton />
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-10">
            <div className="border border-line rounded-lg bg-white p-5">
              <Skeleton className="h-4 w-32 mb-4" />
              <Skeleton className="h-40 w-full" />
            </div>
            <div className="border border-line rounded-lg bg-white p-5">
              <Skeleton className="h-4 w-40 mb-4" />
              <Skeleton className="h-40 w-full" />
            </div>
          </div>
          <LedgerSkeleton columns={4} />
        </>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-10">
            <StatBlock label="Active batches" value={String(batches.length)} />
            <StatBlock label="Students enrolled" value={String(students?.totalElements ?? 0)} />
            <StatBlock
              label="Fees pending"
              value={formatCurrency(totalDue)}
              sub={`${pendingFees?.totalElements ?? 0} student${(pendingFees?.totalElements ?? 0) === 1 ? '' : 's'}`}
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-10">
            <div className="border border-line rounded-lg bg-white p-5">
              <h2 className="font-display text-lg text-ink mb-1">Attendance trend</h2>
              <p className="text-xs text-slate-light mb-4">Last 14 days with a class held, across all batches.</p>
              {!attendanceTrend || attendanceTrend.points.length === 0 ? (
                <EmptyState title="No attendance recorded yet" description="Mark attendance to see the trend here." />
              ) : (
                <LineChart
                  data={attendanceTrend.points.map((p) => ({
                    label: new Date(p.date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }),
                    value: p.attendancePercentage,
                  }))}
                  formatValue={(v) => `${v}%`}
                  yMax={100}
                />
              )}
            </div>

            <div className="border border-line rounded-lg bg-white p-5">
              <h2 className="font-display text-lg text-ink mb-1">Performance by subject</h2>
              <p className="text-xs text-slate-light mb-4">Average score across all tests in the last 30 days.</p>
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
            </div>
          </div>

          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display text-xl text-ink">Fees due soon</h2>
            <Link to="/app/fees" className="text-sm text-slate hover:text-ink underline underline-offset-2">
              View all
            </Link>
          </div>

          {pendingFees && pendingFees.content.length > 0 ? (
            <Ledger columns={['Student', 'Due date', 'Amount due', 'Balance']}>
              {pendingFees.content.slice(0, 6).map((f) => (
                <Row key={f.id}>
                  <Cell>{students?.content.find((s) => s.id === f.studentId)?.fullName ?? f.studentId.slice(0, 8)}</Cell>
                  <Cell>{f.dueDate}</Cell>
                  <Cell>{formatCurrency(f.amountDue)}</Cell>
                  <Cell className="font-medium">{formatCurrency(f.amountDue - f.amountPaid)}</Cell>
                </Row>
              ))}
            </Ledger>
          ) : (
            <EmptyState
              title="No pending fees"
              description="Every fee on record is fully paid."
              action={
                <Link to="/app/fees">
                  <Button variant="secondary">Go to fees</Button>
                </Link>
              }
            />
          )}
        </>
      )}
    </Layout>
  )
}
