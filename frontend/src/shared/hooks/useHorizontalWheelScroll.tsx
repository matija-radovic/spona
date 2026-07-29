import { useEffect, type RefObject } from 'react'

/** Firefox reports line-based deltas; roughly one text line per unit. */
const LINE_HEIGHT = 16

/**
 * Lets a horizontal scroller consume a plain (vertical) mouse wheel, so a lane
 * scrolls sideways without the user having to hold shift - shift+wheel already
 * arrives as `deltaX` and is left to the browser.
 *
 * The gesture is handed back to the page once the lane sits at the requested
 * edge, so the surrounding scroll never feels trapped.
 */
const useHorizontalWheelScroll = <T extends HTMLElement>(ref: RefObject<T | null>) => {
  useEffect(() => {
    const el = ref.current
    if (!el) return

    const onWheel = (e: WheelEvent) => {
      // Trackpads emit deltaX themselves - only borrow the vertical axis.
      if (!e.deltaY || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return

      const max = el.scrollWidth - el.clientWidth
      if (max <= 0) return

      const delta = e.deltaMode === WheelEvent.DOM_DELTA_LINE ? e.deltaY * LINE_HEIGHT : e.deltaY
      const next = Math.min(Math.max(el.scrollLeft + delta, 0), max)
      if (next === el.scrollLeft) return

      e.preventDefault()
      el.scrollLeft = next
    }

    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  }, [ref])
}

export default useHorizontalWheelScroll
