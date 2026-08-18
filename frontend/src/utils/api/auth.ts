// -----------------------------------------------------------------------------
// MOCK auth service: every call fakes a response, with the real axios call left
// commented above each mock body. DTO shapes mirror new_docx/types.ts, and a
// single-entry GET/POST returns the bare data object (no _links yet).
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
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

export interface OrganizationDto {
  id: string
  actorType: 'org'
  createdAt: string
  slug: string
  displayName: string
  legalName: string
  description: string | null
  website: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: AdditionalContactInformation
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

export interface CreateOrganizationPayload {
  slug: string
  displayName: string
  legalName: string
  description: string | null
  website: string | null
  /** Set from the registration stage's AvatarPicker; settings edits it later. */
  avatarUrl: string | null
  location: string | null
  otherContactInfo: AdditionalContactInformation
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))
const fakeId = () => crypto.randomUUID()

/** Usernames the mock treats as already registered, to exercise the taken flow. */
const TAKEN_USERNAMES = new Set(['admin', 'test', 'spona', 'user'])

/** MOCK - would be: const { data } = await api.post('/auth/login', payload) */
export const loginRequest = async (payload: LoginPayload): Promise<UserDto> => {
  await delay(450)
  return {
    id: fakeId(),
    actorType: 'user',
    createdAt: new Date().toISOString(),
    username: payload.identifier,
    fullName: payload.identifier,
    email: payload.identifier.includes('@') ? payload.identifier : `${payload.identifier}@example.com`,
    bio: null,
    avatarUrl: null,
    location: null,
    otherContactInfo: [],
  }
}

/**
 * MOCK - would be: await api.post('/auth/password-reset', { email })
 * Every syntactically valid address gets the same answer, so the endpoint never
 * reveals which emails are registered; a malformed one is the only visible failure.
 */
export const requestPasswordReset = async (email: string): Promise<void> => {
  await delay(500)
  if (!email.includes('@')) {
    const problem: ProblemDetail = {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'Nevažeća email adresa.',
      instance: '/api/auth/password-reset',
    }
    return Promise.reject(problem)
  }
}

/** MOCK - would be: const { data } = await api.get('/users', { params: { username } }) */
export const checkUsernameAvailable = async (username: string): Promise<boolean> => {
  await delay(400)
  return !TAKEN_USERNAMES.has(username.trim().toLowerCase())
}

/** MOCK - would be: const { data } = await api.post('/users', payload) */
export const createUser = async (payload: CreateUserPayload): Promise<UserDto> => {
  await delay(500)
  return {
    id: fakeId(),
    actorType: 'user',
    createdAt: new Date().toISOString(),
    username: payload.username,
    email: payload.email,
    fullName: payload.fullName,
    bio: payload.bio,
    avatarUrl: payload.avatarUrl,
    location: payload.location,
    otherContactInfo: payload.otherContactInfo,
  }
}

/** MOCK - would be: const { data } = await api.post('/organizations', payload) */
export const createOrganization = async (
  payload: CreateOrganizationPayload,
): Promise<OrganizationDto> => {
  await delay(500)
  return {
    id: fakeId(),
    actorType: 'org',
    createdAt: new Date().toISOString(),
    slug: payload.slug,
    displayName: payload.displayName,
    legalName: payload.legalName,
    description: payload.description,
    website: payload.website,
    avatarUrl: payload.avatarUrl,
    location: payload.location,
    otherContactInfo: payload.otherContactInfo,
  }
}
