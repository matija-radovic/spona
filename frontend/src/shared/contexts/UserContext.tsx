import { createContext, use, useCallback, useMemo, useState, type ReactNode } from 'react'

export interface User {
  id: string
  username: string
  fullName: string
  email: string
  avatarUrl: string
}

interface UserContextValue {
  user: User | null
  isAuthenticated: boolean
  login: (user: User) => void
  logout: () => void
}

const UserContext = createContext<UserContextValue | null>(null)

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

export const useUser = (): UserContextValue => {
  const ctx = use(UserContext)
  if (ctx === null) {
    throw new Error('useUser must be used within a <UserProvider>.')
  }
  return ctx
}
