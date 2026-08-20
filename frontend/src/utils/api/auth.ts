// -----------------------------------------------------------------------------
// MOCK auth service: every call fakes a response, with the real axios call left
// commented above each mock body. DTO shapes mirror new_docx/types.ts, and a
// single-entry GET/POST returns the bare data object (no _links yet).
//
// Sessions are a bearer JWT (backend-contract §1). Login hands back a token the
// client stores; the token identifies the user, so `/auth/me` re-reads it on
// boot. The mock has no server to hold state, so it encodes the identifier into
// the token and decodes it back in `fetchMe` - the real endpoint reads the row
// keyed by the token's `sub` claim instead.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import { getToken } from '@utils/authToken'
import type { ProblemDetail } from './types'

type AdditionalContactInformation = ContactEntry[]

export interface UserDto {
  id: string
  actorType: 'user'
  createdAt: string
  username: string
  fullName: string
  email: string
  bio: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: AdditionalContactInformation
}

export interface AuthResponse {
  token: string
  user: UserDto
}

export interface LoginPayload {
  identifier: string
  password: string
}

export interface CreateUserPayload {
  username: string
  email: string
  password: string
  fullName: string
  bio: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: AdditionalContactInformation
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (
  status: number,
  detail: string,
  type = 'about:blank',
  title = '',
): ProblemDetail => ({ type, title, status, detail, instance: '/api/auth' })

const rejectProblem = <T>(
  status: number,
  detail: string,
  type?: string,
  title?: string,
): Promise<T> => Promise.reject(makeProblem(status, detail, type, title))

/** Usernames the mock treats as already registered, to exercise the taken flow. */
const TAKEN_USERNAMES = new Set(['admin', 'test', 'spona', 'user'])

/** Log in with this identifier to reach the unverified-account branch. */
const MAGIC_UNVERIFIED = 'neverifikovan'
/** Any password equal to this fails credential checks. */
const MAGIC_WRONG_PASSWORD = 'pogresna'
/** A verification/reset/email-change token equal to this is treated as spent. */
const MAGIC_INVALID_TOKEN = 'nevazeci'

/** The identifier is stashed in the token so `fetchMe` can rebuild the same user. */
const encodeSession = (identifier: string): string => {
  try {
    return `mock.${btoa(identifier)}.${crypto.randomUUID()}`
  } catch {
    return `mock..${crypto.randomUUID()}`
  }
}

const identifierFromToken = (token: string): string | null => {
  const parts = token.split('.')
  if (parts.length < 3 || !parts[1]) return null
  try {
    return atob(parts[1])
  } catch {
    return null
  }
}

const buildUser = (identifier: string): UserDto => ({
  id: `user-${identifier}`,
  actorType: 'user',
  createdAt: new Date().toISOString(),
  username: identifier,
  fullName: identifier,
  email: identifier.includes('@') ? identifier : `${identifier}@example.com`,
  bio: null,
  avatarUrl: null,
  location: null,
  otherContactInfo: [],
})

/**
 * MOCK - would be: const { data } = await api.post('/auth/login', payload)
 * An unverified account is `403` with `/problems/email-not-verified` so the form
 * can offer "resend" instead of "wrong password"; bad credentials are a plain 401.
 */
export const loginRequest = async (payload: LoginPayload): Promise<AuthResponse> => {
  await delay(450)
  const identifier = payload.identifier.trim().toLowerCase()
  if (identifier === MAGIC_UNVERIFIED) {
    return rejectProblem(
      403,
      'Nalog nije potvrđen. Proverite email ili zatražite novi link.',
      '/problems/email-not-verified',
      'Forbidden',
    )
  }
  if (payload.password === MAGIC_WRONG_PASSWORD) {
    return rejectProblem(401, 'Pogrešno korisničko ime ili lozinka.', 'about:blank', 'Unauthorized')
  }
  return { token: encodeSession(payload.identifier), user: buildUser(payload.identifier) }
}

/**
 * MOCK - would be: const { data } = await api.get('/auth/me')
 * Called on boot to restore the session. The real endpoint reads the user off
 * the bearer token; the mock decodes the identifier it stored at login.
 */
export const fetchMe = async (): Promise<UserDto> => {
  await delay(1300)
  const token = getToken()
  const identifier = token ? identifierFromToken(token) : null
  if (!identifier) {
    return rejectProblem(401, 'Sesija je istekla.', 'about:blank', 'Unauthorized')
  }
  return buildUser(identifier)
}

/**
 * MOCK - would be: await api.post('/auth/logout')
 * Logout is client-side: the token is discarded (backend-contract §1). The call
 * exists so the server can audit it, and is best-effort.
 */
export const logoutRequest = async (): Promise<void> => {
  await delay(150)
}

/**
 * MOCK - would be: await api.post('/auth/register', payload)
 * Creates the account with `emailVerifiedAt = NULL` and mails the link. Returns
 * no token and logs no one in - registration ends at "check your inbox".
 */
export const registerUser = async (payload: CreateUserPayload): Promise<void> => {
  await delay(500)
  void payload
}

/**
 * MOCK - would be: await api.post('/auth/verify', { token })
 * Confirms an emailed address for both purposes: a SIGNUP token unlocks the
 * account, a CHANGE token writes the new address and notifies the old one. The
 * token carries its purpose, so one route serves both (backend-contract §2).
 */
export const verifyEmail = async (token: string): Promise<void> => {
  await delay(450)
  if (token.trim().toLowerCase() === MAGIC_INVALID_TOKEN) {
    return rejectProblem(400, 'Link je nevažeći ili je istekao.', 'about:blank', 'Bad Request')
  }
}

/**
 * MOCK - would be: await api.post('/auth/verify/resend', { email })
 * Answers the same whether or not the address is registered, so it never becomes
 * an account-existence oracle (backend-contract §3).
 */
export const resendVerification = async (email: string): Promise<void> => {
  await delay(400)
  void email
}

/** MOCK - would be: const { data } = await api.get('/users', { params: { username } }) */
export const checkUsernameAvailable = async (username: string): Promise<boolean> => {
  await delay(400)
  return !TAKEN_USERNAMES.has(username.trim().toLowerCase())
}

/**
 * MOCK - would be: await api.post('/auth/password-reset', { email })
 * Every syntactically valid address gets the same answer, so the endpoint never
 * reveals which emails are registered; a malformed one is the only visible failure.
 */
export const requestPasswordReset = async (email: string): Promise<void> => {
  await delay(500)
  if (!email.includes('@')) {
    return rejectProblem(400, 'Nevažeća email adresa.', 'about:blank', 'Bad Request')
  }
}

/**
 * MOCK - would be: await api.post('/auth/password-reset/confirm', { token, newPassword })
 * Sets the new digest and invalidates every other live reset token for the user.
 */
export const confirmPasswordReset = async (token: string, newPassword: string): Promise<void> => {
  await delay(500)
  void newPassword
  if (token.trim().toLowerCase() === MAGIC_INVALID_TOKEN) {
    return rejectProblem(400, 'Link je nevažeći ili je istekao.', 'about:blank', 'Bad Request')
  }
}

