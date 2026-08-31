import { useCallback, useEffect, useState } from 'react'
import { useUser } from '@shared/contexts'
import { fetchMyOrganizations, type MyOrganizationDto } from '@utils/api/memberships'

// Module-level cache of "the organizations I belong to", keyed by user id
const cache = new Map<string, Promise<MyOrganizationDto[]>>()

export const loadMyOrganizations = (
  userId: string,
  username: string,
): Promise<MyOrganizationDto[]> => {
  const hit = cache.get(userId)
  if (hit) return hit

  const pending = fetchMyOrganizations(username).catch((error: unknown) => {
    cache.delete(userId)
    throw error
  })
  cache.set(userId, pending)
  return pending
}

/** on logout and 403 */
export const invalidateMyOrganizations = (userId?: string): void => {
  if (userId) cache.delete(userId)
  else cache.clear()
}

interface MyOrganizations {
  organizations: MyOrganizationDto[]
  loading: boolean
  /** Invalidate and refetch */
  refresh: () => void
}

/**
 * The signed-in user's memberships, for composers that offer publishing as an
 * organization. Guests resolve to an empty list without a request.
 *
 * A page that hosts a composer should also call `loadMyOrganizations` alongside
 * its own fetches, so the list is already in flight by the time the reader has
 * scrolled to the composer - otherwise the identity picker appears late on a
 * page that had every chance to ask early.
 */
const useMyOrganizations = (): MyOrganizations => {
  const { user } = useUser()
  const [organizations, setOrganizations] = useState<MyOrganizationDto[]>([])
  const [loading, setLoading] = useState(user !== null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!user) return
    let alive = true
    loadMyOrganizations(user.id, user.username)
      .then((rows) => {
        if (!alive) return
        setOrganizations(rows)
        setLoading(false)
      })
      .catch(() => {
        if (!alive) return
        setOrganizations([])
        setLoading(false)
      })
    return () => {
      alive = false
    }
  }, [user, reloadKey])

  const refresh = useCallback(() => {
    if (!user) return
    invalidateMyOrganizations(user.id)
    setLoading(true)
    setReloadKey((key) => key + 1)
  }, [user])

  return { organizations, loading, refresh }
}

export default useMyOrganizations
