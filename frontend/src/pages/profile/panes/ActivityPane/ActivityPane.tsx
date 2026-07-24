import './ActivityPane.scss'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FilterCapsules, type CapsuleOption } from '@pages/profile/components/FilterCapsules'
import type { ActivityKind } from '@utils/api/activity'
import type { ActorRefDto } from '@utils/api/profiles'
import ActivityList from './ActivityList'

interface ActivityPaneProps {
  actorId: string
  /** The profile owner, stamped as each item's author. */
  owner: ActorRefDto
}

/**
 * Activity pane: single-select filter capsules over the list. Each kind is its
 * own cached list, so switching kind remounts the list (keyed by actorId::kind)
 * which lazy-inits from the module cache instead of refetching.
 */
const ActivityPane = ({ actorId, owner }: ActivityPaneProps) => {
  const { t } = useTranslation()
  const [kind, setKind] = useState<ActivityKind>('all')

  const capsules: CapsuleOption[] = [
    { key: 'all', label: t('profile.activity.filter.all') },
    { key: 'posts', label: t('profile.activity.filter.posts') },
    { key: 'comments', label: t('profile.activity.filter.comments') },
  ]

  return (
    <div className="activity-pane">
      <FilterCapsules
        options={capsules}
        active={kind}
        onChange={(key) => setKind(key as ActivityKind)}
        ariaLabel={t('profile.tabs.activity')}
      />

      <ActivityList key={`${actorId}::${kind}`} actorId={actorId} owner={owner} kind={kind} />
    </div>
  )
}

export default ActivityPane
