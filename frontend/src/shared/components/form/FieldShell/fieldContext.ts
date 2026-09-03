import { createContext, use } from 'react'

/** Wiring only - the shell renders the messages, the control renders none. */
export interface FieldContextValue {
  /** `id` for the control the shell's label points at. */
  controlId: string
  /** For `aria-labelledby` when the label is a span rather than a <label>. */
  labelId: string
  /** Hint id, error id, or both. */
  describedBy?: string
  /** Drives `aria-invalid` on the control. */
  invalid: boolean
}

export const FieldContext = createContext<FieldContextValue | null>(null)

/** Null outside a `FieldShell`, which is what lets a control stand alone. */
export const useFieldContext = (): FieldContextValue | null => use(FieldContext)
