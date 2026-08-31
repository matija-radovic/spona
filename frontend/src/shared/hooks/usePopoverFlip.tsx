import { useCallback, useEffect, useRef, useState, type RefObject } from 'react'

/** Which trigger edge the panel's matching edge is pinned to. */
export const PopoverAlign = Object.freeze({
  START: 'start',
  END: 'end',
} as const)

export type PopoverAlign = (typeof PopoverAlign)[keyof typeof PopoverAlign]

interface UsePopoverFlipOptions {
  /** Space kept between the panel and the viewport edge. */
  margin?: number
  /** Preferred horizontal side; flipped only when that side would overflow. */
  align?: PopoverAlign
}

interface PopoverFlip {
  /** True when the panel should be pinned to the top of the anchor instead. */
  above: boolean
  /** The side to actually pin to, after edge avoidance. */
  align: PopoverAlign
  /** Attach to the panel element: `<ul ref={panelRef}>`. */
  panelRef: (node: HTMLElement | null) => void
}

/**
 * Keeps an absolutely-positioned panel inside the viewport on BOTH axes.
 * Prefered alignment edges can be chosen along side margin between anchor and the popover.
 * 
 * ```tsx
 * const { above, align, panelRef } = usePopoverFlip(open, triggerRef, { align: MenuAlign.END })
 * <ul ref={panelRef} className={`x-panel${above ? ' is-above' : ''} is-${align}`}>
 * ```
 */
const usePopoverFlip = (
  open: boolean,
  anchorRef: RefObject<HTMLElement | null>,
  { margin = 8, align: preferred = PopoverAlign.START }: UsePopoverFlipOptions = {},
): PopoverFlip => {
  const [above, setAbove] = useState(false)
  const [align, setAlign] = useState<PopoverAlign>(preferred)
  const panelNode = useRef<HTMLElement | null>(null)

  const measure = useCallback(() => {
    const anchor = anchorRef.current
    const panel = panelNode.current
    if (!anchor || !panel) return

    const rect = anchor.getBoundingClientRect()

    const spaceBelow = window.innerHeight - rect.bottom - margin
    const spaceAbove = rect.top - margin

    setAbove(panel.offsetHeight > spaceBelow && spaceAbove > spaceBelow)

    const width = panel.offsetWidth
    const fitsStart = rect.left + width <= window.innerWidth - margin
    const fitsEnd = rect.right - width >= margin
    if (preferred === PopoverAlign.START) setAlign(fitsStart || !fitsEnd ? PopoverAlign.START : PopoverAlign.END)
    else setAlign(fitsEnd || !fitsStart ? PopoverAlign.END : PopoverAlign.START)
  }, [anchorRef, margin, preferred])

  const panelRef = useCallback(
    (node: HTMLElement | null) => {
      panelNode.current = node
      if (node) measure()
      else {
        setAbove(false)
        setAlign(preferred)
      }
    },
    [measure, preferred],
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

  return { above, align, panelRef }
}

export default usePopoverFlip
