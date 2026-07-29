/**
 * This is the single source of truth for the profile tab keys and one tab entry.
 * Kept out of the component file so the module exports the component alone
 * (react-refresh).
 */
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
