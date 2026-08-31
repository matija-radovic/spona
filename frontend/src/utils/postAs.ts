import type { PostAsValue } from '@shared/components/PostAsSelect/postAsValue'

// To store an organization user previously acted as for ease of access

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
