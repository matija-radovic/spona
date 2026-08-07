import { createContext, use } from 'react'

export interface User {
  id: string
  username: string
  fullName: string
  email: string
  avatarUrl: string
}

export interface UserContextValue {
  user: User | null
  isAuthenticated: boolean
  login: (user: User) => void
  logout: () => void
}

export const UserContext = createContext<UserContextValue | null>(null)

export const useUser = (): UserContextValue => {
  const ctx = use(UserContext)
  if (ctx === null) {
    throw new Error('useUser must be used within a <UserProvider>.')
  }
  return ctx
}
