import { useCallback, useRef, useState } from 'react'

const RADIUS = 10
const HEAD_GAP = 4
const TAIL_GAP = 6
const FADE = 5

/** The last match wins and is chosen as an endpoint*/
const ENDPOINTS = [
  ':scope > .thread-comment > .thread-comment-author-row .actor-avatar',
  ':scope > .comment-composer .content-editor-avatar .actor-avatar',
  ':scope > .thread-more',
  ':scope > .thread-more-error',
  ':scope > .thread-more-loading',
].join(', ')

const px = (value: number) => Math.round(value * 100) / 100

interface ConnectorLine {
  d: string
  /** User-space x, on the same px grid as `d`. */
  fadeFrom: number
  fadeTo: number
}

interface ThreadConnector {
  /** Attach to the responses container: `<div ref={connectorRef}>`. */
  connectorRef: (node: HTMLElement | null) => void
  line: ConnectorLine | null
}

/**
 * The elbow from a comment's avatar to the row that currently ends its replies.
 */
const useThreadConnector = (): ThreadConnector => {
  const [line, setLine] = useState<ConnectorLine | null>(null)
  const nodeRef = useRef<HTMLElement | null>(null)
  const observerRef = useRef<ResizeObserver | null>(null)

  const measure = useCallback(() => {
    const responses = nodeRef.current
    const comment = responses?.closest('.thread-comment')
    const avatar = comment?.querySelector(':scope > .thread-comment-author-row .actor-avatar')
    const rows = responses?.querySelectorAll(ENDPOINTS)
    const end = rows?.[rows.length - 1]

    if (!comment || !avatar || !end) {
      setLine(null)
      return
    }

    const origin = comment.getBoundingClientRect()
    const head = avatar.getBoundingClientRect()
    const tail = end.getBoundingClientRect()

    const x = head.left - origin.left + head.width / 2
    const top = head.bottom - origin.top + HEAD_GAP
    const y = tail.top - origin.top + tail.height / 2
    const right = tail.left - origin.left - TAIL_GAP

    if (y - top < 1 || right - x < 1) {
      setLine(null)
      return
    }

    const r = Math.min(RADIUS, y - top, right - x)
    setLine({
      d: `M${px(x)} ${px(top)}V${px(y - r)}Q${px(x)} ${px(y)} ${px(x + r)} ${px(y)}H${px(right)}`,
      // Clamped to x so a fade reaching back past the corner cannot wash out the
      // vertical run, which is most of the line.
      fadeFrom: px(Math.max(x, right - FADE)),
      fadeTo: px(right),
    })
  }, [])

  const connectorRef = useCallback(
    (node: HTMLElement | null) => {
      observerRef.current?.disconnect()
      observerRef.current = null
      nodeRef.current = node

      if (!node) {
        setLine(null)
        return
      }

      const comment = node.closest('.thread-comment')
      if (comment) {
        const observer = new ResizeObserver(measure)
        observer.observe(comment)
        observerRef.current = observer
      }
      measure()
    },
    [measure],
  )

  return { connectorRef, line }
}

export default useThreadConnector
