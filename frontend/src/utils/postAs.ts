import type { OrganizationRole } from '@utils/api/organizations'
import type { PostAsSurface, PostAsValue } from '@shared/components/actor/PostAsSelect/postAsValue'

const PUBLISH_AS: Readonly<Record<OrganizationRole, Readonly<Record<PostAsSurface, boolean>>>> = {
  OWNER: { content: true, job: true },
  ADMIN: { content: true, job: true },
  PUBLISHER: { content: true, job: true },
  EDITOR: { content: true, job: false },
  RECRUITER: { content: false, job: true },
}

export const canPublishAs = (role: OrganizationRole, surface: PostAsSurface): boolean =>
  PUBLISH_AS[role][surface]

const keyFor = (userId: string) => `spona:post-as:${userId}`

export const getStoredPostAs = (userId: string): PostAsValue => {
  try {
    return localStorage.getItem(keyFor(userId))
  } catch {
    return null // storage unavailable -> as myself
  }
}

export const setStoredPostAs = (userId: string, value: PostAsValue): void => {
  try {
    if (value === null) localStorage.removeItem(keyFor(userId))
    else localStorage.setItem(keyFor(userId), value)
  } catch {
    /* storage unavailable - the preference simply does not persist */
  }
}

export const clearStoredPostAs = (userId: string): void => {
  try {
    localStorage.removeItem(keyFor(userId))
  } catch {
    /* storage unavailable - nothing to clear */
  }
}
