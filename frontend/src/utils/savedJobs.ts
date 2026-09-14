const KEY = 'spona:saved-jobs' // matches the `spona:last-section` convention

export interface SavedJobEntry {
  id: string
  slug: string
  savedAt: string
}

const isEntry = (value: unknown): value is SavedJobEntry => {
  if (typeof value !== 'object' || value === null) return false
  const e = value as Record<string, unknown>
  return typeof e.id === 'string' && typeof e.slug === 'string' && typeof e.savedAt === 'string'
}

export const getSavedJobs = (): SavedJobEntry[] => {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(isEntry) : []
  } catch {
    return [] // malformed JSON or storage unavailable -> treat as empty
  }
}

const write = (entries: SavedJobEntry[]): void => {
  try {
    localStorage.setItem(KEY, JSON.stringify(entries))
  } catch {
    /* storage unavailable - ignore */
  }
}

export const isJobSaved = (id: string): boolean => getSavedJobs().some((e) => e.id === id)

/** Adds when absent, removes when present; returns the new saved state. */
export const toggleSavedJob = (job: { id: string; slug: string }): boolean => {
  const entries = getSavedJobs()
  const index = entries.findIndex((e) => e.id === job.id)
  if (index >= 0) {
    entries.splice(index, 1)
    write(entries)
    return false
  }
  entries.push({ id: job.id, slug: job.slug, savedAt: new Date().toISOString() })
  write(entries)
  return true
}
