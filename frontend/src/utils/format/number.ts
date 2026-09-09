const cache = new Map<string, Intl.NumberFormat>()

const formatterFor = (locale: string): Intl.NumberFormat => {
  const hit = cache.get(locale)
  if (hit) return hit

  const made = new Intl.NumberFormat(locale)
  cache.set(locale, made)
  return made
}

/** Groups thousands per `locale`: `1.234.567` in sr, `1,234,567` in en. */
export const formatNumber = (value: number, locale: string): string =>
  formatterFor(locale).format(value)

const COMPACT_FROM = 1000
const COMPACT_MAX_THOUSANDS = 99

export const formatCompactCount = (value: number): string =>
  value < COMPACT_FROM
    ? String(value)
    : `${Math.min(COMPACT_MAX_THOUSANDS, Math.round(value / COMPACT_FROM))}k`
