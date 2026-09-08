import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, logoutRequest } from '@utils/api/auth'
import { clearToken, getToken, setToken } from '@utils/authToken'
import { invalidateMyOrganizations } from '@shared/hooks/useMyOrganizations'
import { clearStoredPostAs } from '@utils/postAs'
import { clearPostCache } from '@utils/postCache'
import { clearFeedCache } from '@pages/feed/lib/feedCache'
import { UserContext, type User, type UserContextValue } from './userContext'

interface UserProviderProps {
  children: ReactNode
}

const toUser = (dto: Awaited<ReturnType<typeof fetchMe>>): User => ({
  id: dto.id,
  username: dto.username,
  fullName: dto.fullName,
  email: dto.email,
  avatarUrl: dto.avatarUrl ?? '',
})

export const UserProvider = ({ children }: UserProviderProps) => {
  const [user, setUser] = useState<User | null>(null)
  const [isBootstrapping, setIsBootstrapping] = useState(() => getToken() !== null)

  useEffect(() => {
    if (!getToken()) return
    let cancelled = false
    fetchMe()
      .then((dto) => {
        if (!cancelled) setUser(toUser(dto))
      })
      .catch(() => {
        clearToken()
      })
      .finally(() => {
        if (!cancelled) setIsBootstrapping(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback((next: User, token: string) => {
    setToken(token)
    setUser(next)
  }, [])

  const updateUser = useCallback((next: User) => setUser(next), [])

  const logout = useCallback(() => {
    logoutRequest().catch(() => {})
    clearToken()
    invalidateMyOrganizations()
    clearPostCache()
    clearFeedCache()
    if (user) clearStoredPostAs(user.id)
    setUser(null)
  }, [user])

  const value = useMemo<UserContextValue>(
    () => ({ user, isAuthenticated: user !== null, isBootstrapping, login, updateUser, logout }),
    [user, isBootstrapping, login, updateUser, logout],
  )

  return <UserContext value={value}>{children}</UserContext>
}
