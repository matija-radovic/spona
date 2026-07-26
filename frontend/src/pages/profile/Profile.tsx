import './Profile.scss'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { parseProblem, type ProblemDetail } from '@utils/api/types'
import { fetchProfile, type ActorRefDto, type ProfileDto } from '@utils/api/profiles'
import { fetchActorJobPostings, type JobListItemDto } from '@utils/api/jobs'
import { ProfileHeader } from './components/ProfileHeader'
import { ProfileTabs, ProfileTab, type ProfileTabItem } from './components/ProfileTabs'
import { ActivityPane } from './panes/ActivityPane'
import { ContactPane } from './panes/ContactPane'
import { JobPostingsPane } from './panes/JobPostingsPane'
import { SavedJobsPane } from './panes/SavedJobsPane'

interface ProfileBodyProps {
  profile: ProfileDto
  postings: JobListItemDto[]
}

/**
 * The ready-state view. Keyed by handle in Profile so navigating between profiles
 * remounts it - which resets the active tab and lets the panes start fresh. The
 * tab strip is useState-driven (no routes); inactive panes unmount but their data
 * survives via props (postings) or module caches (activity, saved jobs).
 */
const ProfileBody = ({ profile, postings }: ProfileBodyProps) => {
  const { t } = useTranslation()
  const [active, setActive] = useState<ProfileTab>(ProfileTab.ACTIVITY)

  const owner = useMemo<ActorRefDto>(() => {
    const name = profile.actorType === 'user' ? profile.fullName : profile.displayName
    const handle = profile.actorType === 'user' ? profile.username : profile.slug
    return { actorType: profile.actorType, name, handle, avatarUrl: profile.avatarUrl }
  }, [profile])

  const tabs: ProfileTabItem[] = [{ key: ProfileTab.ACTIVITY, label: t('profile.tabs.activity') }]
  if (profile.otherContactInfo.length > 0) {
    tabs.push({ key: ProfileTab.CONTACTS, label: t('profile.tabs.contacts') })
  }
  if (postings.length > 0) {
    tabs.push({ key: ProfileTab.JOB_POSTINGS, label: t('profile.tabs.jobPostings') })
  }
  if (profile.actorType === 'user' && profile.viewerCanEdit) {
    tabs.push({ key: ProfileTab.SAVED_JOBS, label: t('profile.tabs.savedJobs') })
  }

  return (
    <div className="profile container">
      <ProfileHeader profile={profile} />

      {/* TODO(org-members): specialized member action row goes here — see
          random/frontend/profile-page.r1.md §17.1 */}

      <ProfileTabs tabs={tabs} active={active} onChange={setActive} />

      <section className="profile-pane">
        {active === ProfileTab.ACTIVITY && <ActivityPane actorId={profile.id} owner={owner} />}
        {active === ProfileTab.CONTACTS && <ContactPane entries={profile.otherContactInfo} />}
        {active === ProfileTab.JOB_POSTINGS && <JobPostingsPane postings={postings} />}
        {active === ProfileTab.SAVED_JOBS && <SavedJobsPane />}
      </section>
    </div>
  )
}

interface ProfileProps {
  actorType: 'user' | 'org'
}

/**
 * Public profile showcase loader for a user or organization. Loads the profile
 * and the actor's public job postings in parallel; loading is derived from
 * `loadedHandle` so the effect only setStates in async callbacks (the JobDetail
 * discipline). A postings failure degrades to [] so the page never breaks because
 * a secondary list errored (§6, §16.14).
 */
const Profile = ({ actorType }: ProfileProps) => {
  const { t } = useTranslation()
  const params = useParams()
  const handle = actorType === 'user' ? params.username : params.slug

  const [profile, setProfile] = useState<ProfileDto | null>(null)
  const [postings, setPostings] = useState<JobListItemDto[]>([])
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [loadedHandle, setLoadedHandle] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (!handle) return
    let alive = true
    // The postings query is keyed by the URL handle (the backend resolves the
    // actor); both requests run concurrently.
    Promise.allSettled([fetchProfile(actorType, handle), fetchActorJobPostings(handle)]).then(
      ([profileRes, postingsRes]) => {
        if (!alive) return
        if (profileRes.status === 'fulfilled') {
          setProfile(profileRes.value)
          setPostings(postingsRes.status === 'fulfilled' ? postingsRes.value : [])
          setProblem(null)
        } else {
          setProfile(null)
          setProblem(parseProblem(profileRes.reason))
        }
        setLoadedHandle(handle)
      },
    )
    return () => {
      alive = false
    }
  }, [actorType, handle])

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
