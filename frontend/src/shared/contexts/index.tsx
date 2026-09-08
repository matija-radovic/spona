/* eslint-disable react-refresh/only-export-components -- Re-export barrel: it defines no component of its own, so it is never a fast-refresh boundary. */
export { UserProvider } from './UserProvider'
export { useUser, useAuthenticatedUser, MissingUserError } from './userContext'
export type { User } from './userContext'
