/** Field skeletons; `Intl` supplies each locale's order, separators and names. */
const PRESETS = {
  /** `13. avgust 2026.` */
  long: { day: 'numeric', month: 'long', year: 'numeric' },
  /** `13. 8. 2026.` */
  short: { day: 'numeric', month: 'numeric', year: 'numeric' },
  /** `13. 8. 2026. 12:51` */
  dateTime: {
    day: 'numeric',
    month: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  },
} as const satisfies Record<string, Intl.DateTimeFormatOptions>

export type DatePreset = keyof typeof PRESETS

/** Construction is the costly part of `Intl.DateTimeFormat`; reuse instances. */
const cache = new Map<string, Intl.DateTimeFormat>()

const formatterFor = (locale: string, preset: DatePreset): Intl.DateTimeFormat => {
  const key = `${locale}|${preset}`
  const hit = cache.get(key)
  if (hit) return hit

  const made = new Intl.DateTimeFormat(locale, PRESETS[preset])
  cache.set(key, made)
  return made
}

/** Missing or unparseable input yields an empty string rather than `Invalid Date`. */
export const formatDate = (
  iso: string | null | undefined,
  locale: string,
  preset: DatePreset = 'long',
): string => {
  if (!iso) return ''

  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''

  return formatterFor(locale, preset).format(date)
}
