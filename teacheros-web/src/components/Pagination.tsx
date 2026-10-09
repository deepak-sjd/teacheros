import { Button } from '@/components/ui'

// Matches the backend's @PageableDefault(size = 20) on list endpoints.
export const PAGE_SIZE = 20

/**
 * Prev/Next footer for a Spring `Page<T>`. `page` is 0-based (Page.number).
 * Renders nothing when everything fits on one page.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements: number
  size: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  const from = page * size + 1
  const to = Math.min((page + 1) * size, totalElements)

  return (
    <div className="flex items-center justify-between mt-4">
      <p className="text-xs text-slate">
        Showing {from}–{to} of {totalElements}
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  )
}
