import './ProfileTabs.scss'

export const ProfileTab = Object.freeze({
  ACTIVITY: 'activity',
  CONTACTS: 'contacts',
  JOB_POSTINGS: 'jobPostings',
  SAVED_JOBS: 'savedJobs',
} as const)

export type ProfileTab = (typeof ProfileTab)[keyof typeof ProfileTab]

export interface ProfileTabItem {
  key: ProfileTab
  label: string
}

interface ProfileTabsProps {
  tabs: ProfileTabItem[]
  active: ProfileTab
  onChange: (key: ProfileTab) => void
}

/**
 * A dumb, left-aligned tab strip. The active key lives in Profile (useState) -
 * no router involvement. The active tab is underlined in goldenrod.
 */
const ProfileTabs = ({ tabs, active, onChange }: ProfileTabsProps) => (
  <div className="profile-tabs" role="tablist">
    {tabs.map((tab) => (
      <button
        key={tab.key}
        type="button"
        role="tab"
        aria-selected={tab.key === active}
        className={`profile-tab${tab.key === active ? ' is-active' : ''}`}
        onClick={() => onChange(tab.key)}
      >
        {tab.label}
      </button>
    ))}
  </div>
)

export default ProfileTabs
