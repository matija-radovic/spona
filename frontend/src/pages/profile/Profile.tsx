import './Profile.scss'
import { useEffect, useMemo, useState } from 'react'
import { useLocation, useMatch, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useUser } from '@shared/contexts'
import { loadMyOrganizations } from '@shared/hooks/useMyOrganizations'
import { parseProblem, type PagedResponse, type ProblemDetail } from '@utils/api/types'
import {
  fetchProfile,
  type ActorRefDto,
  type FollowDirection,
  type ProfileDto,
} from '@utils/api/profiles'
import { actorPath } from '@utils/actorPath'
import { actorDisplayName } from '@utils/actorDisplayName'
import { fetchActorJobPostings, type SavedJobItemDto } from '@utils/api/jobs'
import { FollowListModal } from './components/FollowListModal'
import { ProfileHeader } from './components/ProfileHeader'
import { ProfileTabs, ProfileTab, type ProfileTabItem } from './components/ProfileTabs'
import { ActivityPane } from './panes/ActivityPane'
import { ContactPane } from './panes/ContactPane'
import { JobPostingsPane } from './panes/JobPostingsPane'
import { SavedJobsPane } from './panes/SavedJobsPane'

interface ProfileBodyProps {
  profile: ProfileDto
  /** Null when the postings request failed; the tab is then simply absent. */
  postings: PagedResponse<SavedJobItemDto> | null
}

const ProfileBody = ({ profile, postings }: ProfileBodyProps) => {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useUser()

  const base = profile.actorType === 'user' ? '/users/:username' : '/companies/:slug'
  const followersMatch = useMatch(`${base}/followers`)
  const followingMatch = useMatch(`${base}/following`)
  const direction: FollowDirection | null = followersMatch
    ? 'followers'
    : followingMatch
      ? 'following'
      : null

  const [followers, setFollowers] = useState(profile.followers)
  const [following, setFollowing] = useState(
    profile.actorType === 'user' ? profile.following : undefined,
  )
  const [isFollowing, setIsFollowing] = useState(profile.isFollowedByViewer ?? false)

  const onFollowChange = (next: boolean) => {
    setIsFollowing(next)
    setFollowers((count) => count + (next ? 1 : -1))
  }

  const onViewerFollowChange = (delta: number) =>
    setFollowing((count) => (count === undefined ? count : count + delta))

  const hasPostings = !!postings && postings.content.length > 0
  const requested = (location.state as { tab?: ProfileTab } | null)?.tab
  const [active, setActive] = useState<ProfileTab>(
    requested === ProfileTab.JOB_POSTINGS && hasPostings
      ? ProfileTab.JOB_POSTINGS
      : ProfileTab.ACTIVITY,
  )

  const owner = useMemo<ActorRefDto>(() => {
    const handle = profile.actorType === 'user' ? profile.username : profile.slug
    const name = actorDisplayName(
      profile.actorType === 'user' ? profile.fullName : profile.displayName,
      handle,
    )
    return { actorType: profile.actorType, id: profile.id, name, handle, avatarUrl: profile.avatarUrl }
  }, [profile])

  const tabs: ProfileTabItem[] = [{ key: ProfileTab.ACTIVITY, label: t('profile.tabs.activity') }]
  if (profile.otherContactInfo.length > 0) {
    tabs.push({ key: ProfileTab.CONTACTS, label: t('profile.tabs.contacts') })
  }
  if (hasPostings) {
    tabs.push({ key: ProfileTab.JOB_POSTINGS, label: t('profile.tabs.jobPostings') })
  }
  if (profile.actorType === 'user' && profile.viewerCanEdit) {
    tabs.push({ key: ProfileTab.SAVED_JOBS, label: t('profile.tabs.savedJobs') })
  }

  const closeFollowList = () => navigate(actorPath(owner), { replace: true })

  return (
    <div className="profile container">
      <ProfileHeader
        profile={profile}
        followers={followers}
        following={following}
        isFollowing={isFollowing}
        onFollowChange={onFollowChange}
      />

      <FollowListModal
        direction={direction}
        actor={owner}
        followers={followers}
        following={following}
        onClose={closeFollowList}
        onViewerFollowChange={profile.id === user?.id ? onViewerFollowChange : undefined}
      />

      {/* TODO(org-members): specialized member action row goes here.*/}

      <ProfileTabs tabs={tabs} active={active} onChange={setActive} />

      <section className="profile-pane">
        {active === ProfileTab.ACTIVITY && <ActivityPane actorId={profile.id} />}
        {active === ProfileTab.CONTACTS && <ContactPane entries={profile.otherContactInfo} />}
        {active === ProfileTab.JOB_POSTINGS && postings && (
          <JobPostingsPane
            actorId={profile.id}
            canManage={profile.viewerCanManageJobPostings ?? false}
            initialPage={postings}
          />
        )}
        {active === ProfileTab.SAVED_JOBS && <SavedJobsPane />}
      </section>
    </div>
  )
}

interface ProfileProps {
  actorType: 'user' | 'org'
}

const Profile = ({ actorType }: ProfileProps) => {
  const { t } = useTranslation()
  const params = useParams()
  const { user } = useUser()
  const handle = actorType === 'user' ? params.username : params.slug

  const [profile, setProfile] = useState<ProfileDto | null>(null)
  const [postings, setPostings] = useState<PagedResponse<SavedJobItemDto> | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [loadedHandle, setLoadedHandle] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (!handle) return
    let alive = true
    const profileRequest = fetchProfile(actorType, handle)
    const postingsRequest = profileRequest.then((res) => fetchActorJobPostings(res.id))
    const organizationsRequest = user
      ? loadMyOrganizations(user.id, user.username)
      : Promise.resolve([])

    Promise.allSettled([
      profileRequest,
      postingsRequest,
      organizationsRequest,
    ]).then(([profileRes, postingsRes]) => {
      if (!alive) return
      if (profileRes.status === 'fulfilled') {
        setProfile(profileRes.value)
        setPostings(postingsRes.status === 'fulfilled' ? postingsRes.value : null)
        setProblem(null)
      } else {
        setProfile(null)
        setPostings(null)
        setProblem(parseProblem(profileRes.reason))
      }
      setLoadedHandle(handle)
    })
    return () => {
      alive = false
    }
  }, [actorType, handle, user])

  const loading = loadedHandle !== handle

  if (loading) {
    return (
      <div className="profile container">
        <p className="profile-status">{t('common.loading')}</p>
      </div>
    )
  }

  if (problem || !profile) {
    const isNotFound = problem?.status === 404
    return (
      <div className="profile container">
        <p className="profile-status">
          {isNotFound ? t('profile.notFound') : problem?.detail || t('errors.serverError')}
        </p>
      </div>
    )
  }

  return <ProfileBody key={handle} profile={profile} postings={postings} />
}

export default Profile
