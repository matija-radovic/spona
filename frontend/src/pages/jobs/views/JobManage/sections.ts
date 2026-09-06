import { IconFileText, IconInbox } from '@tabler/icons-react'
import type { SectionItem } from '@shared/components'

export const JOB_MANAGE_SECTIONS: readonly SectionItem[] = [
  { path: 'posting', labelKey: 'jobs.manage.nav.posting', icon: IconFileText },
  { path: 'applications', labelKey: 'jobs.manage.nav.applications', icon: IconInbox },
]
