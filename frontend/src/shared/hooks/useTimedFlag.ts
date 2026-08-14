import useTimedValue from './useTimedValue'

export interface TimedFlag {
  active: boolean
  /** Raise the flag. Re-calling restarts the countdown. */
  raise: () => void
}

/** A boolean that goes true on `raise()` and clears itself after `ms`. */
const useTimedFlag = (ms?: number): TimedFlag => {
  const { value, toggle } = useTimedValue(false, true, ms)
  return { active: value, raise: toggle }
}

export default useTimedFlag
