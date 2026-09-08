import { createContext, use } from 'react'
import { assertExists } from '@utils/assertExists'

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

/**
 * Thrown when a surface that only renders behind `ProtectedRoute` finds no
 * user. It carries a name so the boundary in `-routing/` can recognise it and
 * let every other render error through.
 */
export class MissingUserError extends Error {
  constructor(surface: string) {
    super(`${surface} rendered without an authenticated user.`)
    this.name = 'MissingUserError'
  }
}

/**
 * `useUser` for the surfaces the route guard has already vouched for. The guard
 * is invisible to the type system, so this is where "cannot be null" is stated
 * once instead of once per page.
 */
export const useAuthenticatedUser = (surface: string): User => {
  const { user } = useUser()
  assertExists(user, () => new MissingUserError(surface))
  return user
}
