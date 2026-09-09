import './ReactionBreakdownModal.scss'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize } from '@shared/components/actor/ActorAvatar'
import { Modal } from '@shared/components/overlay/Modal'
import { actorDisplayName } from '@utils/actorDisplayName'
import { actorPath } from '@utils/actorPath'
import { parseProblem } from '@utils/api/types'
import {
  fetchReactionBreakdown,
  type ReactionBreakdownDto,
  type ReactionTargetType,
} from '@utils/api/reactions'

type BreakdownState =
  | { status: 'loading' }
  | { status: 'error'; detail: string }
  | { status: 'ready'; data: ReactionBreakdownDto }

const LOADING: BreakdownState = { status: 'loading' }

interface ReactionBreakdownModalProps {
  isOpen: boolean
  onClose: () => void
  targetType: ReactionTargetType
  targetId: string
}

const ReactionBreakdownModal = ({
  isOpen,
  onClose,
  targetType,
  targetId,
}: ReactionBreakdownModalProps) => {
  const { t } = useTranslation()
  const baseId = useId()
  const tabsRef = useRef<(HTMLButtonElement | null)[]>([])

  const [state, setState] = useState<BreakdownState>(LOADING)
  const [selected, setSelected] = useState<string | null>(null)
  const [wasOpen, setWasOpen] = useState(isOpen)

  if (isOpen !== wasOpen) {
    setWasOpen(isOpen)
    if (!isOpen) {
      setState(LOADING)
      setSelected(null)
    }
  }

  useEffect(() => {
    if (!isOpen) return
    let alive = true

    fetchReactionBreakdown(targetType, targetId)
      .then((data) => {
        if (!alive) return
        setState({ status: 'ready', data })
        setSelected(data.entries[0]?.emoji ?? null)
      })
      .catch((caught) => {
        if (alive) setState({ status: 'error', detail: parseProblem(caught).detail })
      })

    return () => {
      alive = false
    }
  }, [isOpen, targetType, targetId])

  const entries = state.status === 'ready' ? state.data.entries : []
  const activeIndex = entries.findIndex((entry) => entry.emoji === selected)
  const reactors = activeIndex < 0 ? [] : entries[activeIndex].reactors
  const panelId = `${baseId}-panel`

  const onLaneKey = (event: KeyboardEvent<HTMLDivElement>) => {
    const last = entries.length - 1
    if (last < 0) return

    const next =
      event.key === 'ArrowDown'
        ? Math.min(activeIndex + 1, last)
        : event.key === 'ArrowUp'
          ? Math.max(activeIndex - 1, 0)
          : event.key === 'Home'
            ? 0
            : event.key === 'End'
              ? last
              : -1

    if (next < 0) return
    event.preventDefault()
    setSelected(entries[next].emoji)
    tabsRef.current[next]?.focus()
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('post.reactions.title')} className="reaction-breakdown">
      {state.status === 'loading' && (
        <p className="reaction-breakdown-state">{t('post.reactions.loading')}</p>
      )}

      {state.status === 'error' && (
        <p className="reaction-breakdown-state">{state.detail || t('errors.serverError')}</p>
      )}

      {state.status === 'ready' && entries.length === 0 && (
        <p className="reaction-breakdown-state">{t('post.reactions.empty')}</p>
      )}

      {state.status === 'ready' && entries.length > 0 && (
        <div className="reaction-breakdown-body">
          <div
            className="reaction-breakdown-lane scroll-container"
            role="tablist"
            aria-orientation="vertical"
            aria-label={t('post.reactions.title')}
            onKeyDown={onLaneKey}
          >
            {entries.map((entry, index) => (
              <button
                key={entry.emoji}
                type="button"
                role="tab"
                id={`${baseId}-tab-${index}`}
                ref={(node) => {
                  tabsRef.current[index] = node
                }}
                className={`reaction-breakdown-tab${index === activeIndex ? ' is-active' : ''}`}
                aria-selected={index === activeIndex}
                aria-controls={panelId}
                aria-label={t('post.reactions.tab', { count: entry.count, emoji: entry.emoji })}
                tabIndex={index === activeIndex ? 0 : -1}
                onClick={() => setSelected(entry.emoji)}
              >
                <span className="reaction-breakdown-emoji" aria-hidden>{entry.emoji}</span>
                <span className="reaction-breakdown-count">{entry.count}</span>
              </button>
            ))}
          </div>

          <div
            className="reaction-breakdown-people scroll-container"
            role="tabpanel"
            id={panelId}
            aria-labelledby={activeIndex < 0 ? undefined : `${baseId}-tab-${activeIndex}`}
          >
            {reactors.map((actor) => (
              <Link key={actor.id} to={actorPath(actor)} className="reaction-breakdown-person">
                <ActorAvatar
                  avatarUrl={actor.avatarUrl}
                  actorType={actor.actorType}
                  size={ActorAvatarSize.SM}
                />
                <span className="reaction-breakdown-name">
                  {actorDisplayName(actor.name, actor.handle)}
                </span>
              </Link>
            ))}
          </div>
        </div>
      )}
    </Modal>
  )
}

export default ReactionBreakdownModal
