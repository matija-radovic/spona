import './ReactionPicker.scss'
import { type MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconMoodSmile } from '@tabler/icons-react'
import usePopover from '@shared/hooks/usePopover'
import { REACTION_EMOJIS } from '@utils/constants/reactions'

interface ReactionPickerProps {
  /** Emojis the viewer has already used on this target - rendered pressed. */
  viewerReactions: string[]
  /** Toggle a reaction: `next` is true to add, false to remove. */
  onToggle: (emoji: string, next: boolean) => void
  className?: string
}

/**
 * The emoji trigger button plus its popover: a 5x4 grid of the 20 reaction
 * emojis (👍 then ❤️ first). Closes on outside-click, Esc, or a pick. A cell the
 * viewer already used renders pressed and clicking it removes the reaction.
 */
const ReactionPicker = ({ viewerReactions, onToggle, className }: ReactionPickerProps) => {
  const { t } = useTranslation()
  const { open, setOpen, toggle, rootRef } = usePopover()

  const onTrigger = (event: MouseEvent) => {
    event.stopPropagation()
    toggle()
  }

  const pick = (event: MouseEvent, emoji: string, active: boolean) => {
    event.stopPropagation()
    onToggle(emoji, !active)
    setOpen(false)
  }

  const rootClass = ['reaction-picker', open ? 'is-open' : '', className ?? ''].filter(Boolean).join(' ')

  return (
    <div className={rootClass} ref={rootRef}>
      <button
        type="button"
        className="reaction-picker-trigger"
        onClick={onTrigger}
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={t('post.react')}
      >
        <IconMoodSmile aria-hidden />
      </button>

      {open && (
        <div className="reaction-picker-popover" role="menu">
          {REACTION_EMOJIS.map((emoji) => {
            const active = viewerReactions.includes(emoji)
            return (
              <button
                key={emoji}
                type="button"
                role="menuitemcheckbox"
                aria-checked={active}
                className={`reaction-picker-cell${active ? ' is-active' : ''}`}
                onClick={(e) => pick(e, emoji, active)}
              >
                {emoji}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default ReactionPicker
