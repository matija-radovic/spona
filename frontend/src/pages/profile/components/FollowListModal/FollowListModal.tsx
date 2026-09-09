import './FollowListModal.scss'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconSearch } from '@tabler/icons-react'
import { Input, InputShape, Modal } from '@shared/components'
import useDebounce from '@shared/hooks/useDebounce'
import { actorPath } from '@utils/actorPath'
import type { ActorRefDto, FollowDirection } from '@utils/api/profiles'
import FollowList from './FollowList'

interface FollowTab {
  key: FollowDirection
  label: string
}

interface FollowListModalProps {
  /** Null closes it; the last non-null value is what the fade out draws. */
  direction: FollowDirection | null
  actor: ActorRefDto
  followers: number
  /** Absent on an organization, which has no following list at all. */
  following?: number
  onClose: () => void
  onViewerFollowChange?: (delta: number) => void
}

const FollowListModal = ({
  direction,
  actor,
  followers,
  following,
  onClose,
  onViewerFollowChange,
}: FollowListModalProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [query, setQuery] = useState('')
  const debouncedQuery = useDebounce(query.trim(), 350)

  const isOpen = direction !== null

  const [session, setSession] = useState({ direction, opens: 0, wasOpen: isOpen })
  if (isOpen !== session.wasOpen || (direction !== null && direction !== session.direction)) {
    setSession({
      direction: direction ?? session.direction,
      opens: session.opens + (isOpen && !session.wasOpen ? 1 : 0),
      wasOpen: isOpen,
    })
  }
  const shown = session.direction

  const tabs: FollowTab[] = [
    { key: 'followers', label: t('profile.follows.followersTab', { count: followers }) },
  ]
  if (following !== undefined) {
    tabs.push({ key: 'following', label: t('profile.follows.followingTab', { count: following }) })
  }

  const switchTo = (next: FollowDirection) =>
    navigate(`${actorPath(actor)}/${next}`, { replace: true })

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={actor.name}
      className="follow-list-modal"
    >
      <div className="follow-list-tabs" role="tablist">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={tab.key === shown}
            className={`follow-tab${tab.key === shown ? ' is-active' : ''}`}
            onClick={() => switchTo(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="follow-list-search">
        <Input
          shape={InputShape.PILL}
          leading={<IconSearch aria-hidden />}
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t('actors.search.placeholder')}
          aria-label={t('actors.search.placeholder')}
          enterKeyHint="search"
        />
      </div>

      {shown && (
        <FollowList
          key={`${session.opens}::${shown}::${debouncedQuery}`}
          actorId={actor.id}
          direction={shown}
          query={debouncedQuery}
          onFollowChange={
            onViewerFollowChange ? (next) => onViewerFollowChange(next ? 1 : -1) : undefined
          }
        />
      )}
    </Modal>
  )
}

export default FollowListModal
