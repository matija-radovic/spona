import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react'
import { copyToClipboard } from '@utils/copyToClipboard'

/** How long the "copied" confirmation holds before it reverts. */
const COPIED_RESET_MS = 1800

/**
 * Clipboard write plus a short-lived `copied` flag, so every "copy link"
 * control shares the behaviour and only decides how it *looks*: the quiet pill
 * inside post cards (CopyLinkButton) or a tertiary `Button` in the profile head.
 * Clipboard failure fails quietly - no crash, the control just stays as it was.
 *
 * `copy` stops click propagation so the control can sit inside a clickable card
 * without triggering the card's navigation.
 */
const useCopyLink = (url: string) => {
  const [copied, setCopied] = useState(false)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
  }, [])

  const copy = useCallback(async (event: MouseEvent) => {
    event.stopPropagation()
    event.preventDefault()
    
    if (!(await copyToClipboard(url))) return // clipboard unavailable - keep the icon
    setCopied(true)

    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    timeoutRef.current = setTimeout(() => setCopied(false), COPIED_RESET_MS)
  }, [url])

  return { copied, copy }
}

export default useCopyLink
