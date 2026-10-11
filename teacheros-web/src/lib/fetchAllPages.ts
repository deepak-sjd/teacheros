import { api } from './api'
import type { Page } from './types'

/**
 * Fetches every page of a paged list endpoint and returns the combined
 * items. Sorted by id on the server so page boundaries are stable (a
 * name sort can reorder ties between requests and drop or duplicate a
 * row at a boundary) - callers sort for display themselves.
 */
export async function fetchAllPages<T>(
  path: string,
  params: Record<string, string | number | undefined> = {},
  pageSize = 200,
  maxPages = 50,
): Promise<T[]> {
  const items: T[] = []
  for (let page = 0; page < maxPages; page++) {
    const res = await api.get<Page<T>>(path, { sort: 'id,asc', ...params, page, size: pageSize })
    items.push(...res.content)
    if (page + 1 >= res.totalPages) return items
  }
  throw new Error(`Too many records to load (more than ${maxPages * pageSize}).`)
}
