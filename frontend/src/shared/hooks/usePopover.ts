import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type RefObject,
  type SetStateAction,
} from 'react'
import usePopoverFlip, { PopoverAlign } from './usePopoverFlip'

interface UsePopoverOptions {
  /** Preferred horizontal side, handed straight to `usePopoverFlip`. */
  align?: PopoverAlign
  /** Space kept between the panel and the viewport edge. */
  margin?: number
  /** False when the panel owns Escape itself, e.g. to restore focus first. */
  closeOnEscape?: boolean
}

interface Popover {
  open: boolean
  setOpen: Dispatch<SetStateAction<boolean>>
  toggle: () => void
  /** Outside-click is measured against this: wrap the trigger and the panel. */
  rootRef: RefObject<HTMLDivElement | null>
  /** The element the panel is positioned from. */
  triggerRef: RefObject<HTMLButtonElement | null>
  /** Attach to the panel element to opt into edge avoidance. */
  panelRef: (node: HTMLElement | null) => void
  above: boolean
  align: PopoverAlign
}

/**
 * Open state plus the dismiss discipline every popover in the app shares:
 * outside-click and Escape, listening only while open. Position is delegated to
 * `usePopoverFlip` - a panel that does not attach `panelRef` simply never flips.
 *
 * ```tsx
 * const { open, toggle, rootRef, triggerRef, panelRef, above } = usePopover()
 * <div ref={rootRef}><button ref={triggerRef} onClick={toggle} /> ...</div>
 * ```
 */
const usePopover = ({ align, margin, closeOnEscape = true }: UsePopoverOptions = {}): Popover => {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  const { above, align: openAlign, panelRef } = usePopoverFlip(open, triggerRef, { align, margin })

  // setState lives in the subscription callbacks, never synchronously in the
  // effect body: this eslint config errors on that.
  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    if (closeOnEscape) document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open, closeOnEscape])

  const toggle = useCallback(() => setOpen((current) => !current), [])

  return { open, setOpen, toggle, rootRef, triggerRef, panelRef, above, align: openAlign }
}

export default usePopover
