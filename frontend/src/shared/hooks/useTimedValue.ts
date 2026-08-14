import { useCallback, useEffect, useRef, useState } from 'react'

const DEFAULT_MS = 2500

export interface TimedValue<T> {
  value: T
  /** Hold `active`, then fall back to `base`. Re-calling restarts the countdown. */
  toggle: () => void
  /** Hold an arbitrary value, then fall back to `base`. */
  hold: (next: T) => void
  /** Replace `base`, `active`, or both. A live hold keeps running. */
  configure: (next: { base?: T; active?: T }) => void
}

/** A value that reverts to its base on its own after `ms`. */
const useTimedValue = <T>(base: T, active: T, ms: number = DEFAULT_MS): TimedValue<T> => {
  const [pair, setPair] = useState({ base, active })
  const [held, setHeld] = useState<{ value: T } | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const hold = useCallback(
    (next: T) => {
      if (timerRef.current) clearTimeout(timerRef.current)
      setHeld({ value: next })
      timerRef.current = setTimeout(() => setHeld(null), ms)
    },
    [ms],
  )

  const toggle = useCallback(() => hold(pair.active), [hold, pair.active])

  const configure = useCallback((next: { base?: T; active?: T }) => {
    setPair((current) => ({ ...current, ...next }))
  }, [])

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current)
  }, [])

  return { value: held ? held.value : pair.base, toggle, hold, configure }
}

export default useTimedValue
