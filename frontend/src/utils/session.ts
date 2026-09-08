import { clearToken } from '@utils/authToken'
import { invalidateMyOrganizations } from '@shared/hooks/useMyOrganizations'
import { clearStoredPostAs } from '@utils/postAs'
import { clearPostCache } from '@utils/postCache'
import { clearFeedCache } from '@pages/feed/lib/feedCache'

/**
 * Everything keyed to the signed-in identity, cleared in one place. Both the
 * deliberate logout and `ProtectedRoute`'s recovery from a missing user call
 * this, so a cache added later cannot be cleared by only one of them.
 *
 * `userId` is null when the departing identity is no longer known; the stored
 * publishing preference is keyed by id and is the one entry that then stays.
 */
export const clearSessionData = (userId: string | null): void => {
  clearToken()
  invalidateMyOrganizations()
  clearPostCache()
  clearFeedCache()
  if (userId) clearStoredPostAs(userId)
}
