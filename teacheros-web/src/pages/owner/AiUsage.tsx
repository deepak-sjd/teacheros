import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { Cell, EmptyState, ErrorBanner, Ledger, LedgerSkeleton, Row, Select, StatBlock, StatBlockSkeleton } from '@/components/ui'
import { LineChart } from '@/components/Charts'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { AiUsageDashboardResponse} from '@/lib/types'

const RANGES = [
  { label: 'Last 7 days', days: 7 },
  { label: 'Last 30 days', days: 30 },
  { label: 'Last 90 days', days: 90 },
]

function shortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
}

export function AiUsagePage() {
  const { role } = useAuth()
  const [days, setDays] = useState(30)
  const [data, setData] = useState<AiUsageDashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (role !== 'OWNER') return
    setLoading(true)
    setError(null)
    api
      .get<AiUsageDashboardResponse>('/api/v1/owner/ai-usage', { days })
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load AI usage.'))
      .finally(() => setLoading(false))
  }, [days, role])

  if (role !== 'OWNER') {
    return (
      <Layout>
        <PageHeader title="AI usage" />
        <EmptyState title="Owner only" description="Only the workspace owner can see AI usage and cost." />
      </Layout>
    )
  }

  const perDay = new Map<string, number>()
  for (const r of data?.daily ?? []) perDay.set(r.date, (perDay.get(r.date) ?? 0) + r.calls)
  const points = [...perDay.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, calls]) => ({ label: shortDate(date), value: calls }))

  return (
    <Layout>
      <PageHeader title="AI usage" description="How often AI features are used, how often they fail, and what they cost." />

      <div className="w-44 mb-6">
        <Select label="Range" value={days} onChange={(e) => setDays(Number(e.target.value))}>
          {RANGES.map((r) => (
            <option key={r.days} value={r.days}>
              {r.label}
            </option>
          ))}
        </Select>
      </div>

      {error && <ErrorBanner message={error} />}

      {loading || !data ? (
        <div className="space-y-8">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatBlockSkeleton />
            <StatBlockSkeleton />
            <StatBlockSkeleton />
          </div>
          <LedgerSkeleton columns={7} />
        </div>
      ) : (
        <div className="space-y-8">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatBlock label="AI calls" value={String(data.totalCalls)} />
            <StatBlock label="Failure rate" value={`${data.failureRate}%`} sub={`${data.totalFailed} failed`} />
            <StatBlock
              label="Estimated cost"
              value={`${data.costCurrency} ${data.estimatedCost.toFixed(2)}`}
              sub="From provider token counts"
            />
          </div>

          <section className="border border-line rounded-lg bg-white p-5">
            <h2 className="font-display text-lg text-ink mb-1">Calls per day</h2>
            <p className="text-xs text-slate-light mb-4">All AI features combined, by IST calendar day.</p>
            {points.length === 0 ? (
              <EmptyState title="No AI calls in this range" />
            ) : (
              <LineChart data={points} yMax={Math.max(...points.map((p) => p.value), 1)} />
            )}
          </section>

          <section>
            <h2 className="font-display text-xl text-ink mb-3">By feature</h2>
            <Ledger columns={['Feature', 'Calls', 'Failed', 'Failure %', 'Tokens in / out', 'Est. cost', 'Today']}>
              {data.features.map((f) => (
                <Row key={f.feature}>
                  <Cell className="font-medium">{f.label}</Cell>
                  <Cell>{f.calls}</Cell>
                  <Cell>{f.failed}</Cell>
                  <Cell>{f.failureRate}%</Cell>
                  <Cell>
                    {f.inputTokens.toLocaleString('en-IN')} / {f.outputTokens.toLocaleString('en-IN')}
                  </Cell>
                  <Cell>
                    {data.costCurrency} {f.estimatedCost.toFixed(2)}
                  </Cell>
                  <Cell>
                    {f.usedToday} / {f.dailyLimit}
                  </Cell>
                </Row>
              ))}
            </Ledger>
            <p className="text-xs text-slate-light mt-3">
              Cost is estimated from token counts the provider reports, so calls made before token reporting was
              switched on show no tokens and no cost. Self-hosted (Ollama) calls are not costed.
            </p>
          </section>
        </div>
      )}
    </Layout>
  )
}
