import { useOutletContext } from 'react-router-dom'

/** Shared between JobsLayout (owner) and its outlet panels (JobsHint / JobDetail). */
export interface JobsOutletContext {
  isMobile: boolean
  /** Direct/typed/refreshed entry at /jobs/:slug — render the job full-width. */
  detailOnly: boolean
  activeSlug: string | undefined
  /** Mobile: slide back to the (intact) list. */
  onBack: () => void
  /** Phone: the detail pane is still sliding in, so a loaded job waits to render. */
  isSlidingIn: boolean
}

export const useJobsOutletContext = () => useOutletContext<JobsOutletContext>()
