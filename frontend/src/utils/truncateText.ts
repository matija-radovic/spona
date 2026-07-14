// Shared text-truncation helper. Cuts at the last whitespace before the limit
// so words never get sliced mid-way, and reports whether truncation actually
// happened so callers know whether a "read more" affordance is needed.

export interface TruncatedText {
  text: string
  isTruncated: boolean
}

export const truncateText = (value: string, maxLength: number): TruncatedText => {
  if (value.length <= maxLength) return { text: value, isTruncated: false }

  const cut = value.slice(0, maxLength)
  const lastSpace = cut.lastIndexOf(' ')
  const trimmed = (lastSpace > 0 ? cut.slice(0, lastSpace) : cut).trimEnd()

  return { text: `${trimmed}...`, isTruncated: true }
}
