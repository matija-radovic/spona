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
  /** True while the boot phase restores a stored session; guards wait for it
   *  instead of reading "not yet known" as "not authenticated". */
  isBootstrapping: boolean
  login: (user: User, token: string) => void
  /** Patches the signed-in user in place (e.g. after a settings save), leaving
   *  the session token untouched. */
  updateUser: (user: User) => void
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
