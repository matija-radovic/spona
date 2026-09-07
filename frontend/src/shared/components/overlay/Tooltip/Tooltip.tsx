import './Tooltip.scss'
import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react'
import { AnimatePresence, motion } from 'motion/react'
import usePopoverFlip from '@shared/hooks/usePopoverFlip'
import { TooltipAlign } from './tooltipAlign'

const reveal = (above: boolean) =>
  ({
    initial: { opacity: 0, y: above ? 4 : -4 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: above ? 4 : -4 },
    transition: { duration: 0.12, ease: 'easeOut' },
  }) as const

interface TooltipProps {
  /** Wraps at 15rem. */
  label: string
  children: ReactNode
  /** Pins/Aligns to the selected side */
  align?: TooltipAlign
  /** Lands on the anchor: size it, or swap its `display` for an inline host. */
  className?: string
  /** Drives the bubble from outside, e.g. to announce an error the moment it lands. */
  open?: boolean
  /** Required with `open`: the hover, the tap and Escape all report through it. */
  onOpenChange?: (open: boolean) => void
}

/**
 * On Hover, On Touch, On Tap - reveals a bubble beside whatever it wraps.
 * `usePopoverFlip` picks the vertical side, `align` prop the horizontal edge.
 *
 * A `disabled` child works too, it swallows its own pointer events in every engine,
 * so the anchor listens on its behalf. See the SCSS.
 */
const Tooltip = ({
  label,
  children,
  align = TooltipAlign.RIGHT,
  className,
  open: openProp,
  onOpenChange,
}: TooltipProps) => {
  const anchorRef = useRef<HTMLSpanElement>(null)
  const [ownOpen, setOwnOpen] = useState(false)
  const byTouch = useRef(false) // Tap opens it, Tap closes it
  const bubbleId = useId()

  const open = openProp ?? ownOpen
  const setOpen = useCallback(
    (next: boolean) => {
      setOwnOpen(next)
      onOpenChange?.(next)
    },
    [onOpenChange],
  )

  const { above, panelRef } = usePopoverFlip(open, anchorRef)

  useEffect(() => {
    if (!open) return
    const close = () => setOpen(false)
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    const onPointerDown = (event: PointerEvent) => {
      if (!anchorRef.current?.contains(event.target as Node)) close()
    }

    document.addEventListener('keydown', onKeyDown)
    if (byTouch.current) document.addEventListener('pointerdown', onPointerDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('pointerdown', onPointerDown)
    }
  }, [open, setOpen])

  const onEnter = (event: ReactPointerEvent) => {
    if (event.pointerType !== 'mouse') return
    byTouch.current = false
    setOpen(true)
  }

  const onLeave = (event: ReactPointerEvent) => {
    if (event.pointerType === 'mouse') setOpen(false)
  }

  const onTap = (event: ReactPointerEvent) => {
    if (event.pointerType === 'mouse') return
    byTouch.current = true
    setOpen(true)
  }

  return (
    <span
      ref={anchorRef}
      className={['tooltip-anchor', className ?? ''].filter(Boolean).join(' ')}
      onPointerEnter={onEnter}
      onPointerLeave={onLeave}
      onPointerUp={onTap}
      aria-describedby={open ? bubbleId : undefined}
    >
      {children}

      <AnimatePresence>
        {open && (
          <motion.span
            ref={panelRef}
            id={bubbleId}
            role="tooltip"
            className={`tooltip-bubble tooltip-bubble-${align}${above ? ' is-above' : ''}`}
            {...reveal(above)}
          >
            {label}
          </motion.span>
        )}
      </AnimatePresence>
    </span>
  )
}

export default Tooltip
