import type { ReactNode } from 'react'

export interface DropdownOption {
  value: string
  label: string
  /** Arbitrary node before the label in the panel; the caller builds it. */
  leading?: ReactNode
  /** Extra words the filter matches on, never rendered. */
  searchTokens?: readonly string[]
}

/** What a query matching nothing offers instead of a blank panel. */
export interface DropdownNoMatch {
  option?: DropdownOption
  hint?: string
}
