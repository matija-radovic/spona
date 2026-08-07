import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { UserContext, type User, type UserContextValue } from './userContext'

interface UserProviderProps {
  children: ReactNode
}

export const UserProvider = ({ children }: UserProviderProps) => {
  const [user, setUser] = useState<User | null>(null)

  const login = useCallback((next: User) => setUser(next), [])
  const logout = useCallback(() => setUser(null), [])

  const value = useMemo<UserContextValue>(
    () => ({ user, isAuthenticated: user !== null, login, logout }),
    [user, login, logout],
  )

  return <UserContext value={value}>{children}</UserContext>
}
