import './ContentEditor.scss'
import { useCallback, useEffect, useId, useLayoutEffect, useRef, type ReactNode } from 'react'
import { Button, ButtonSize, ButtonVariant } from '@shared/components/form/Button'

const COUNTER_THRESHOLD = 0.9

const grow = (node: HTMLTextAreaElement | null) => {
  if (!node) return
  const dragged = node.style.height
  node.style.height = 'auto'
  node.style.setProperty('--field-natural-height', '0px')
  const natural = node.scrollHeight + node.offsetHeight - node.clientHeight
  node.style.height = dragged
  node.style.setProperty('--field-natural-height', `${natural}px`)
}

interface ContentEditorProps {
  value: string
  onChange: (next: string) => void
  onSubmit: () => void
  onCancel: () => void
  submitLabel: string
  cancelLabel: string
  placeholder?: string
  busy?: boolean
  error?: string | null
  identity?: ReactNode
  /** Rendered beside the field, the author's avatar. */
  leading?: ReactNode
  maxLength?: number
  autoFocus?: boolean
  /** EDIT mode carries no placeholder to fall back on. */
  ariaLabel?: string
  /** Form-control name. Defaults to the generated id; pass one only where the
   *  surrounding form needs to address the field by a name of its own. */
  name?: string
  className?: string
}

/** The one field that creates or edits post-like content - an auto-growing textarea. */
const ContentEditor = ({
  value,
  onChange,
  onSubmit,
  onCancel,
  submitLabel,
  cancelLabel,
  placeholder,
  busy = false,
  error,
  identity,
  leading,
  maxLength,
  autoFocus = false,
  ariaLabel,
  name,
  className,
}: ContentEditorProps) => {
  const fieldRef = useRef<HTMLTextAreaElement | null>(null)
  const fieldId = useId()

  const setField = useCallback(
    (node: HTMLTextAreaElement | null) => {
      fieldRef.current = node
      if (autoFocus && node) {
        node.focus()
        node.setSelectionRange(node.value.length, node.value.length)
      }
    },
    [autoFocus],
  )

  useLayoutEffect(() => {
    grow(fieldRef.current)
  }, [value])

  // Snap phone rows to 3 when moving from desktop when desktop rows are > 3
  useEffect(() => {
    const node = fieldRef.current
    if (!node) return

    let width = node.clientWidth
    const observer = new ResizeObserver(() => {
      if (node.clientWidth === width) return
      width = node.clientWidth
      grow(node)
    })
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  const empty = value.trim().length === 0
  const overCounterThreshold = maxLength !== undefined && value.length >= maxLength * COUNTER_THRESHOLD

  return (
    <div className={['content-editor', className].filter(Boolean).join(' ')}>
      {leading && <div className="content-editor-avatar">{leading}</div>}

      <div className="content-editor-body">
        <textarea
          ref={setField}
          id={fieldId}
          name={name ?? fieldId}
          className="content-editor-field"
          value={value}
          placeholder={placeholder}
          aria-label={ariaLabel ?? placeholder}
          maxLength={maxLength}
          rows={1}
          disabled={busy}
          autoComplete="off"
          onChange={(event) => onChange(event.target.value)}
        />

        <div className="content-editor-row">
          {identity}

          <div className="content-editor-buttons">
            <Button
              variant={ButtonVariant.GHOST}
              size={ButtonSize.SMALL}
              onClick={onCancel}
              disabled={busy}
            >
              {cancelLabel}
            </Button>
            <Button
              variant={ButtonVariant.PRIMARY}
              size={ButtonSize.SMALL}
              onClick={onSubmit}
              disabled={busy || empty}
            >
              {submitLabel}
            </Button>
          </div>
        </div>

        {(overCounterThreshold || error) && (
          <div className="content-editor-meta">
            {error && <span className="content-editor-error">{error}</span>}
            {overCounterThreshold && (
              <span className="content-editor-counter">
                {value.length} / {maxLength}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default ContentEditor
