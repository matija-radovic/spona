import { useEffect, useState, type RefObject } from 'react'

interface UseOnScreenOptions {
  /** Scroll root the target is observed within (defaults to the viewport). */
  root?: RefObject<Element | null>
  rootMargin?: string
  /** Set false to tear the observer down (e.g. once the last page is loaded). */
  enabled?: boolean
}

/**
 * True while `targetRef` intersects `root`. Used as an infinite-scroll sentinel:
 * place an empty <div> at the bottom of a scroll area and observe it.
 */
const useOnScreen = <T extends Element>(
  targetRef: RefObject<T | null>,
  { root, rootMargin = '0px', enabled = true }: UseOnScreenOptions = {},
): boolean => {
  const [onScreen, setOnScreen] = useState(false)

  useEffect(() => {
    const el = targetRef.current
    if (!enabled || !el) return

    // setState lives in the observer callback (an external subscription) - not
    // synchronously in the effect body. When disabled the observer is torn down;
    // any stale `true` is harmless since callers also gate on their own flag.
    const observer = new IntersectionObserver(([entry]) => setOnScreen(entry.isIntersecting), {
      root: root?.current ?? null,
      rootMargin,
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [targetRef, root, rootMargin, enabled])

  return onScreen
}

export default useOnScreen
