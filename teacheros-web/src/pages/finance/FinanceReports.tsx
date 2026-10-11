import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { DailyCollection, DefaultersReport, HeadCollectionReport, OutstandingReport } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Row, Select, StatBlock } from '@/components/ui'
import { formatCurrency, formatDate, formatDateTime, todayIso } from '@/lib/format'

type Tab = 'daily' | 'outstanding' | 'defaulters' | 'heads'

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)
const BASE = '/api/v1/finance-reports'

export function FinanceReports() {
  const [tab, setTab] = useState<Tab>('daily')
  const tabs: [Tab, string][] = [
    ['daily', 'Daily collection'],
    ['outstanding', 'Outstanding'],
    ['defaulters', 'Defaulters'],
    ['heads', 'By fee head'],
  ]
  return (
    <Layout>
      <PageHeader title="Finance reports" description="Collections and dues, in IST. Every report exports to Excel." />
      <div className="flex flex-wrap gap-2 mb-6">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium border transition-colors ${
              tab === key ? 'bg-ink text-paper border-ink' : 'bg-white text-slate border-line hover:border-ink'
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === 'daily' && <DailyTab />}
      {tab === 'outstanding' && <OutstandingTab />}
      {tab === 'defaulters' && <DefaultersTab />}
      {tab === 'heads' && <HeadsTab />}
    </Layout>
  )
}

function ExportButton({ path, filename, onError }: { path: string; filename: string; onError: (m: string) => void }) {
  const [busy, setBusy] = useState(false)
  return (
    <Button
      variant="secondary"
      disabled={busy}
      onClick={async () => {
        setBusy(true)
        try {
          await api.downloadFile(path, filename)
        } catch (err) {
          onError(msg(err, 'Could not download the Excel file.'))
        } finally {
          setBusy(false)
        }
      }}
    >
      {busy ? 'Preparing…' : 'Export to Excel'}
    </Button>
  )
}

function DailyTab() {
  const [date, setDate] = useState(todayIso())
  const [data, setData] = useState<DailyCollection | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setData(null)
    setError(null)
    api
      .get<DailyCollection>(`${BASE}/daily-collection`, { date })
      .then(setData)
      .catch((e) => setError(msg(e, 'Could not load the report.')))
  }, [date])

  return (
    <>
      <div className="flex flex-wrap items-end gap-3 mb-6">
        <div className="w-44">
          <Input label="Date" type="date" value={date} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
        </div>
        <ExportButton path={`${BASE}/daily-collection/export.xlsx?date=${date}`} filename={`daily-collection-${date}.xlsx`} onError={setError} />
      </div>
      {error && <ErrorBanner message={error} />}
      {!data ? (
        <LedgerSkeleton columns={3} />
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-6">
            <StatBlock label="Collected" value={formatCurrency(data.totalCollected)} />
            <StatBlock label="Payments" value={String(data.paymentCount)} />
          </div>
          {data.payments.length === 0 ? (
            <EmptyState title="No payments recorded" description="Nothing was collected on this day." />
          ) : (
            <>
              <Ledger columns={['Method', 'Payments', 'Amount']}>
                {data.byMethod.map((m) => (
                  <Row key={m.method}>
                    <Cell className="font-medium">{m.method.replace('_', ' ')}</Cell>
                    <Cell>{m.count}</Cell>
                    <Cell>{formatCurrency(m.amount)}</Cell>
                  </Row>
                ))}
              </Ledger>
              <h3 className="font-display text-lg text-ink mt-8 mb-3">Payments</h3>
              <Ledger columns={['Student', 'Time', 'Method', 'Amount']}>
                {data.payments.map((p, i) => (
                  <Row key={i}>
                    <Cell>{p.studentName}</Cell>
                    <Cell>{formatDateTime(p.paidAt)}</Cell>
                    <Cell>{p.method.replace('_', ' ')}</Cell>
                    <Cell className="font-medium">{formatCurrency(p.amount)}</Cell>
                  </Row>
                ))}
              </Ledger>
            </>
          )}
        </>
      )}
    </>
  )
}

function OutstandingTab() {
  const [groupBy, setGroupBy] = useState('batch')
  const [data, setData] = useState<OutstandingReport | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setData(null)
    setError(null)
    api
      .get<OutstandingReport>(`${BASE}/outstanding`, { groupBy })
      .then(setData)
      .catch((e) => setError(msg(e, 'Could not load the report.')))
  }, [groupBy])

  return (
    <>
      <div className="flex flex-wrap items-end gap-3 mb-6">
        <div className="w-44">
          <Select label="Group by" value={groupBy} onChange={(e) => setGroupBy(e.target.value)}>
            <option value="batch">Batch</option>
            <option value="grade">Grade</option>
          </Select>
        </div>
        <ExportButton path={`${BASE}/outstanding/export.xlsx?groupBy=${groupBy}`} filename={`outstanding-by-${groupBy}.xlsx`} onError={setError} />
      </div>
      {error && <ErrorBanner message={error} />}
      {!data ? (
        <LedgerSkeleton columns={6} />
      ) : data.rows.length === 0 ? (
        <EmptyState title="Nothing outstanding" description="Every fee is paid." />
      ) : (
        <>
          <div className="mb-6 max-w-xs">
            <StatBlock label="Total outstanding" value={formatCurrency(data.totalOutstanding)} sub={`As of ${formatDate(data.asOf)}`} />
          </div>
          <Ledger columns={[groupBy === 'grade' ? 'Grade' : 'Batch', 'Students', 'Fees', 'Due', 'Paid', 'Outstanding']}>
            {data.rows.map((r) => (
              <Row key={r.group}>
                <Cell className="font-medium">{r.group}</Cell>
                <Cell>{r.studentCount}</Cell>
                <Cell>{r.feeCount}</Cell>
                <Cell>{formatCurrency(r.totalDue)}</Cell>
                <Cell>{formatCurrency(r.totalPaid)}</Cell>
                <Cell className="font-medium">{formatCurrency(r.outstanding)}</Cell>
              </Row>
            ))}
          </Ledger>
        </>
      )}
    </>
  )
}

function DefaultersTab() {
  const [minDays, setMinDays] = useState('0')
  const [data, setData] = useState<DefaultersReport | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setData(null)
    setError(null)
    api
      .get<DefaultersReport>(`${BASE}/defaulters`, { minDaysOverdue: Number(minDays) || 0 })
      .then(setData)
      .catch((e) => setError(msg(e, 'Could not load the report.')))
  }, [minDays])

  return (
    <>
      <div className="flex flex-wrap items-end gap-3 mb-6">
        <div className="w-52">
          <Input label="Overdue at least (days)" type="number" min="0" value={minDays} onChange={(e) => setMinDays(e.target.value)} />
        </div>
        <ExportButton path={`${BASE}/defaulters/export.xlsx?minDaysOverdue=${Number(minDays) || 0}`} filename="defaulters.xlsx" onError={setError} />
      </div>
      {error && <ErrorBanner message={error} />}
      {!data ? (
        <LedgerSkeleton columns={7} />
      ) : data.rows.length === 0 ? (
        <EmptyState title="No defaulters" description="No overdue fees match this filter." />
      ) : (
        <>
          <div className="mb-6 max-w-xs">
            <StatBlock label="Total overdue" value={formatCurrency(data.totalOverdue)} sub={`${data.rows.length} students`} />
          </div>
          <Ledger columns={['Student', 'Batch', 'Guardian phone', 'Overdue fees', 'Overdue', 'Oldest due', 'Days']}>
            {data.rows.map((r) => (
              <Row key={r.studentId}>
                <Cell className="font-medium">{r.studentName}</Cell>
                <Cell>{r.batchName}</Cell>
                <Cell>{r.guardianPhone}</Cell>
                <Cell>{r.overdueFeeCount}</Cell>
                <Cell className="font-medium">{formatCurrency(r.overdueAmount)}</Cell>
                <Cell>{formatDate(r.oldestDueDate)}</Cell>
                <Cell>{r.maxDaysOverdue}</Cell>
              </Row>
            ))}
          </Ledger>
        </>
      )}
    </>
  )
}

function HeadsTab() {
  const monthStart = todayIso().slice(0, 8) + '01'
  const [from, setFrom] = useState(monthStart)
  const [to, setTo] = useState(todayIso())
  const [data, setData] = useState<HeadCollectionReport | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setData(null)
    setError(null)
    api
      .get<HeadCollectionReport>(`${BASE}/collection-by-head`, { from, to })
      .then(setData)
      .catch((e) => setError(msg(e, 'Could not load the report.')))
  }, [from, to])

  return (
    <>
      <div className="flex flex-wrap items-end gap-3 mb-6">
        <div className="w-44">
          <Input label="From" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="w-44">
          <Input label="To" type="date" value={to} min={from} max={todayIso()} onChange={(e) => setTo(e.target.value)} />
        </div>
        <ExportButton path={`${BASE}/collection-by-head/export.xlsx?from=${from}&to=${to}`} filename={`collection-by-head-${from}-to-${to}.xlsx`} onError={setError} />
      </div>
      {error && <ErrorBanner message={error} />}
      {!data ? (
        <LedgerSkeleton columns={2} />
      ) : data.rows.length === 0 ? (
        <EmptyState title="No payments in this range" />
      ) : (
        <>
          <div className="mb-6 max-w-xs">
            <StatBlock label="Collected" value={formatCurrency(data.totalCollected)} />
          </div>
          <Ledger columns={['Fee head', 'Collected']}>
            {data.rows.map((r) => (
              <Row key={r.head}>
                <Cell className="font-medium">{r.head}</Cell>
                <Cell>{formatCurrency(r.amount)}</Cell>
              </Row>
            ))}
          </Ledger>
        </>
      )}
    </>
  )
}
