import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import type { TermResult } from '@/lib/types'
import { Cell, Ledger, Row } from '@/components/ui'
import { formatDate } from '@/lib/format'

/**
 * Term-wise grades for the student/parent "Tests & marks" page. Renders
 * nothing when the tenant has no terms (or nothing graded in any of them),
 * so tenants that haven't set terms up see the page exactly as before.
 */
export function TermResults({ studentId }: { studentId: string }) {
  const [results, setResults] = useState<TermResult[] | null>(null)

  useEffect(() => {
    setResults(null)
    api
      .get<TermResult[]>(`/api/v1/report-cards/students/${studentId}`)
      .then(setResults)
      .catch(() => setResults([]))
  }, [studentId])

  const shown = (results ?? []).filter((r) => r.subjects.length > 0 || r.teacherRemark)
  if (shown.length === 0) return null

  return (
    <div className="mb-8">
      <h3 className="font-display text-xl text-ink mb-3">Term results</h3>
      <div className="space-y-6">
        {shown.map((t) => (
          <div key={t.termId}>
            <p className="text-sm font-medium text-ink mb-2">
              {t.termName}{' '}
              <span className="text-xs text-slate font-normal">
                {formatDate(t.startDate)} – {formatDate(t.endDate)}
              </span>
            </p>
            {t.subjects.length > 0 && (
              <Ledger columns={['Subject', 'Average', 'Grade', 'Remark']}>
                {t.subjects.map((s) => (
                  <Row key={s.subject}>
                    <Cell>{s.subject}</Cell>
                    <Cell>{s.averagePercentage}%</Cell>
                    <Cell className="font-medium">{s.grade ?? '\u2014'}</Cell>
                    <Cell>{s.remark ?? '\u2014'}</Cell>
                  </Row>
                ))}
                <Row>
                  <Cell className="font-medium">Overall</Cell>
                  <Cell className="font-medium">{t.overallPercentage}%</Cell>
                  <Cell className="font-medium">{t.overallGrade ?? '\u2014'}</Cell>
                  <Cell>{t.overallRemark ?? '\u2014'}</Cell>
                </Row>
              </Ledger>
            )}
            {t.teacherRemark && (
              <p className="text-sm text-slate mt-2">
                <span className="text-ink font-medium">Teacher's remark:</span> {t.teacherRemark}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
