import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

interface UsePopoverFlipOptions {
  /** Space kept between the panel and the viewport edge. */
  margin?: number
}

interface PopoverFlip {
  /** True when the panel should be pinned to the top of the anchor instead. */
  above: boolean
  /** Attach to the panel element: `<ul ref={panelRef}>`. */
  panelRef: (node: HTMLElement | null) => void
}

/**
 * Decides whether an absolutely-positioned panel opens ABOVE its trigger,
 * because there isn't room below.
 *
 * The measurement hangs off a **callback ref**, not a layout effect: the ref
 * fires during commit, the instant the panel exists and before paint, so the
 * panel never renders in the wrong place first - and it keeps the `setState`
 * out of an effect body (see `useOnScreen` for the same reasoning).
 *
 * ```tsx
 * const { above, panelRef } = usePopoverFlip(open, triggerRef)
 * <ul ref={panelRef} className={`x-panel${above ? ' is-above' : ''}`}>
 * ```
 */
const usePopoverFlip = (
  open: boolean,
  anchorRef: RefObject<HTMLElement | null>,
  { margin = 8 }: UsePopoverFlipOptions = {},
): PopoverFlip => {
  const [above, setAbove] = useState(false)
  const panelNode = useRef<HTMLElement | null>(null)

  const measure = useCallback(() => {
    const anchor = anchorRef.current
    const panel = panelNode.current
    if (!anchor || !panel) return

    const rect = anchor.getBoundingClientRect()
    const spaceBelow = window.innerHeight - rect.bottom - margin
    const spaceAbove = rect.top - margin

    // Only flip when below genuinely doesn't fit AND above is the better of the
    // two - otherwise a panel taller than the whole viewport flips to an equally
    // bad position.
    setAbove(panel.offsetHeight > spaceBelow && spaceAbove > spaceBelow)
  }, [anchorRef, margin])

  const panelRef = useCallback(
    (node: HTMLElement | null) => {
      panelNode.current = node
      if (node) measure()
      else setAbove(false)
    },
    [measure],
  )

  useEffect(() => {
    if (!open) return
    window.addEventListener('resize', measure)
    document.addEventListener('scroll', measure, true)
    return () => {
      window.removeEventListener('resize', measure)
      document.removeEventListener('scroll', measure, true)
    }
  }, [open, measure])

  return { above, panelRef }
}

export default usePopoverFlip
