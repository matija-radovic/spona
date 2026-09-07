// -----------------------------------------------------------------------------
// MOCK "my account" settings service (backend spec §3.4); the real axios call is
// left commented above each mock body. Rejections are `ProblemDetail` shapes, and
// these magic inputs reach every failure path:
//   - fetchUserSettings : username === "nepostojeci"     -> 404 Not Found
//   - changeUsername    : username === "zauzeto"         -> 409 Conflict
//   - changeEmail       : currentPassword === "pogresna" -> 403 Forbidden
//                         email === "zauzeto@primer.rs"  -> 409 Conflict
//   - changePassword    : currentPassword === "pogresna" -> 403 Forbidden
//   - deleteAccount     : password === "pogresna"        -> 403 Forbidden
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { ProblemDetail } from './types'
import { VIEWER, findPerson, userIdFor } from './mockActors'

// ── DTOs ──────────────────────────────────────────────────────────────────────

/** Boolean-only and additive. There is no public/private-account flag. */
export interface PrivacyDto {
  showJobPostings: boolean
}

export interface UserSettingsDto {
  id: string
  username: string
  /** Private: never in a public profile DTO. A *display* email is an otherContactInfo entry. */
  email: string
  fullName: string
  bio: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
  privacy: PrivacyDto
}

export interface UpdateProfilePayload {
  fullName: string
  bio: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
}

export interface ChangeUsernamePayload {
  username: string
}

export interface ChangeEmailPayload {
  email: string
  currentPassword: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

// ── Mock plumbing ─────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/users',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const MAGIC_NOT_FOUND = 'nepostojeci'
const MAGIC_TAKEN = 'zauzeto'
const MAGIC_TAKEN_EMAIL = 'zauzeto@primer.rs'
const MAGIC_WRONG_PASSWORD = 'pogresna'

const CONTACTS: ContactEntry[] = [
  { type: 'email', description: 'Email za saradnju', value: 'kontakt@primer.rs' },
  { type: 'linkedin', description: 'Poslovni profil', value: 'linkedin.com/in/primer' },
]

/** Mutated in place by the update calls, so a save is visible once the bootstrap refetches. */
let account: UserSettingsDto | null = null

const seedAccount = (username: string): UserSettingsDto => {
  if (!account) {
    account = {
      id: userIdFor(username),
      username,
      email: `${username}@primer.rs`,
      fullName: findPerson(username)?.fullName ?? VIEWER.fullName,
      bio: findPerson(username)?.bio ?? VIEWER.bio,
      avatarUrl: null,
      location: 'Beograd',
      otherContactInfo: CONTACTS,
      privacy: { showJobPostings: true },
    }
  }
  return account
}

// ── Calls ─────────────────────────────────────────────────────────────────────

/** MOCK - would be: const { data } = await api.get(`/users/${username}/settings`) */
export const fetchUserSettings = async (username: string): Promise<UserSettingsDto> => {
  await delay(420)
  if (username === MAGIC_NOT_FOUND) {
    return rejectProblem(404, 'Traženi nalog ne postoji.', 'Not Found')
  }
  return { ...seedAccount(username) }
}

/** MOCK - would be: const { data } = await api.put(`/users/${username}`, payload) */
export const updateProfile = async (payload: UpdateProfilePayload): Promise<UserSettingsDto> => {
  await delay(500)
  const current = seedAccount('my-account')
  account = { ...current, ...payload }
  return { ...account }
}

/**
 * MOCK - would be: const { data } = await api.put(`/users/${username}/username`, payload)
 * The URL carries the username, so the caller must move the address bar with it.
 */
export const changeUsername = async (payload: ChangeUsernamePayload): Promise<UserSettingsDto> => {
  await delay(450)
  if (payload.username.trim().toLowerCase() === MAGIC_TAKEN) {
    return rejectProblem(409, 'Korisničko ime je već zauzeto.', 'Conflict')
  }
  const current = seedAccount(payload.username)
  account = { ...current, username: payload.username }
  return { ...account }
}

/**
 * MOCK - would be: await api.post(`/users/${username}/email`, payload)
 * A REQUEST, not a write: `204`, and the address is mailed a confirmation link. It
 * reaches `User.email` only when `/auth/verify` consumes that link (a CHANGE-purpose
 * token), which is also when the OLD address is notified - the tripwire that makes a
 * session-theft takeover visible (backend contract §2).
 */
export const changeEmail = async (payload: ChangeEmailPayload): Promise<void> => {
  await delay(500)
  if (payload.currentPassword === MAGIC_WRONG_PASSWORD) {
    return rejectProblem(403, 'Pogrešna trenutna lozinka.', 'Forbidden')
  }
  if (payload.email.trim().toLowerCase() === MAGIC_TAKEN_EMAIL) {
    return rejectProblem(409, 'Email adresa je već u upotrebi.', 'Conflict')
  }
}

/** MOCK - would be: await api.put(`/users/${username}/password`, payload) */
export const changePassword = async (payload: ChangePasswordPayload): Promise<void> => {
  await delay(500)
  if (payload.currentPassword === MAGIC_WRONG_PASSWORD) {
    return rejectProblem(403, 'Pogrešna trenutna lozinka.', 'Forbidden')
  }
}

/**
 * MOCK - would be: await api.put(`/actors/${actorId}/privacy`, privacy)
 * Privacy is per-ACTOR, so one endpoint serves both Privatnost sections.
 */
export const updatePrivacy = async (actorId: string, privacy: PrivacyDto): Promise<void> => {
  await delay(400)
  if (!actorId) return rejectProblem(400, 'Nedostaje identifikator naloga.', 'Bad Request')
  if (account && account.id === actorId) account = { ...account, privacy }
}

/**
 * MOCK - would be: await api.delete(`/users/${username}`, { data: { password } })
 * Cascades to the user's own content and to every organization they OWN (backend spec §4.8).
 */
export const deleteAccount = async (password: string): Promise<void> => {
  await delay(600)
  if (password === MAGIC_WRONG_PASSWORD) {
    return rejectProblem(403, 'Pogrešna trenutna lozinka.', 'Forbidden')
  }
  account = null
}
