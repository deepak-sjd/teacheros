import { useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { Button, ErrorBanner } from '@/components/ui'
import { formatDateTime } from '@/lib/format'
import type { StudyPlanResponse } from '@/lib/types'

/**
 * Weak Topic Detection + Study Plan. `initial` comes from the parent's
 * own data-loading Promise.all (GET /study-plan/students/{id}, which
 * never calls the AI provider - see StudyPlanService#get) so this card
 * never fires a request on mount; it only calls the AI-backed regenerate
 * endpoint when the person explicitly clicks the button.
 */
export function StudyPlanCard({ studentId, initial }: { studentId: string; initial: StudyPlanResponse }) {
  const [plan, setPlan] = useState(initial)
  const [regenerating, setRegenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (plan.subjects.length === 0) {
    // Nothing graded yet for this student - nothing to analyze, so the
    // card would just be an empty shell. Overview already has an empty
    // state for "no marks yet" elsewhere; no need to repeat it here.
    return null
  }

  const weakSubjects = plan.subjects.filter((s) => s.weak)

  async function handleRegenerate() {
    setError(null)
    setRegenerating(true)
    try {
      const res = await api.post<StudyPlanResponse>(`/api/v1/study-plan/students/${studentId}/regenerate`)
      setPlan(res)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not generate a study plan right now.')
    } finally {
      setRegenerating(false)
    }
  }

  return (
    <div className="border border-line rounded-lg bg-white p-5 mb-8">
      <div className="flex items-start justify-between gap-4 mb-1">
        <h3 className="font-display text-lg text-ink">Weak topics &amp; study plan</h3>
        {weakSubjects.length > 0 && (
          <Button variant="secondary" onClick={handleRegenerate} disabled={regenerating}>
            {regenerating ? 'Generating…' : plan.planText ? 'Regenerate' : 'Generate plan'}
          </Button>
        )}
      </div>
      <p className="text-xs text-slate-light mb-4">Based on your graded test marks, by subject.</p>

      {error && <ErrorBanner message={error} />}

      {weakSubjects.length === 0 ? (
        <p className="text-sm text-slate">No weak subjects right now \u2014 nice work across the board.</p>
      ) : (
        <div className="flex flex-wrap gap-2 mb-4">
          {weakSubjects.map((s) => (
            <span
              key={s.subject}
              className="inline-flex items-center rounded-full px-3 py-1 text-xs font-medium bg-danger/10 text-danger"
            >
              {s.subject} {'\u00b7'} {Math.round(s.averagePercentage)}%
            </span>
          ))}
        </div>
      )}

      {plan.planText ? (
        <div className="text-sm text-ink whitespace-pre-wrap leading-relaxed border-t border-line pt-4">
          {plan.planText}
          {plan.generatedAt && (
            <p className="text-xs text-slate-light mt-3">Generated {formatDateTime(plan.generatedAt)}</p>
          )}
        </div>
      ) : weakSubjects.length > 0 ? (
        <p className="text-sm text-slate-light">No plan generated yet \u2014 click "Generate plan" above.</p>
      ) : null}
    </div>
  )
}
