// -----------------------------------------------------------------------------
// "An organization I belong to" service. The organization and member functions
// call the backend; the invitation functions are still MOCKS, with the real
// axios call left commented above each mock body. Their rejections are
// `ProblemDetail` shapes, and these magic inputs reach every persona and failure:
//   - fetchInvitations   : any seat below ADMIN         -> 403 (OWNER and ADMIN only)
//   - createInvitation   : identifier === "nepostojeci" -> 422 "Korisnik ne postoji."
//                          identifier === "marko"       -> 409 already a member
//                          identifier containing "@"    -> 201, unresolved row
//
// Every role comes from `mockActors.ts`, which is where the profile, membership
// and job mocks read the same organizations from.
//
// The invitation lists are MUTABLE module state, seeded per slug on first read; a
// pure mock would silently undo every successful action on the next `Osveži`.
//
// Creating an invitation SENDS AN EMAIL; cancel/decline/dismiss do not.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PrivacyDto } from './settings'
import type { ProblemDetail } from './types'
import { VIEWER, viewerRoleIn } from './mockActors'

// ── Local enum mirror (the project mirrors backend enums as local unions) ──────

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'PUBLISHER' | 'EDITOR' | 'RECRUITER'

// ── DTOs ──────────────────────────────────────────────────────────────────────

export interface OrgSettingsDto {
  id: string
  slug: string
  displayName: string
  legalName: string
  description: string | null
  website: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
  privacy: PrivacyDto
  /** Drives ALL gating; recomputed server-side per request, never read from a token. */
  viewerRole: OrganizationRole
  memberCount: number
}

export interface UpdateOrgProfilePayload {
  displayName: string
  legalName: string
  description: string | null
  website: string | null
  /** A new pick's PNG data URL; null keeps the current avatar. */
  avatar: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
}

/** The public org shape returned when a new organization is created. */
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
  otherContactInfo: ContactEntry[]
}

export interface CreateOrganizationPayload {
  slug: string
  displayName: string
  legalName: string
  description: string | null
  website: string | null
  avatar: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
}

export interface OrgMemberDto {
  userId: string
  username: string
  fullName: string
  avatarUrl: string | null
  role: OrganizationRole
  joinedAt: string
}

/** Org-side view: WAITING rows plus not-yet-dismissed DECLINED rows, nothing else. */
export interface InvitationDto {
  id: string
  status: 'WAITING' | 'DECLINED'
  /** Set when the invitation was addressed to a profile. */
  invitee: { username: string; fullName: string; avatarUrl: string | null } | null
  /** Set instead of `invitee` when it was addressed to an email address. */
  email: string | null
  role: OrganizationRole
  createdAt: string
  /** ISO - when it was declined; null while WAITING. */
  resolvedAt: string | null
  invitedByUsername: string
}

// ── Mock plumbing ─────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/organizations',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const iso = (daysAgo: number): string =>
  new Date(Date.now() - daysAgo * 24 * 60 * 60 * 1000).toISOString()

const MAGIC_UNKNOWN_IDENTIFIER = 'nepostojeci'
const MAGIC_EXISTING_MEMBER = 'marko'

const VIEWER_USERNAME = VIEWER.username

// ── Mutable store (see the header note) ───────────────────────────────────────

const invitations = new Map<string, InvitationDto[]>()

const seedInvitations = (slug: string): InvitationDto[] => {
  const existing = invitations.get(slug)
  if (existing) return existing

  const rows: InvitationDto[] = [
    {
      id: `inv-${slug}-1`,
      status: 'WAITING',
      invitee: { username: 'sara', fullName: 'Sara Đukić', avatarUrl: null },
      email: null,
      role: 'EDITOR',
      createdAt: iso(3),
      resolvedAt: null,
      invitedByUsername: VIEWER_USERNAME,
    },
    {
      id: `inv-${slug}-2`,
      status: 'WAITING',
      invitee: null,
      email: 'nova.koleginica@primer.rs',
      role: 'RECRUITER',
      createdAt: iso(9),
      resolvedAt: null,
      invitedByUsername: 'jelena',
    },
    {
      id: `inv-${slug}-3`,
      status: 'DECLINED',
      invitee: { username: 'bojan', fullName: 'Bojan Lukić', avatarUrl: null },
      email: null,
      role: 'ADMIN',
      createdAt: iso(21),
      resolvedAt: iso(18),
      invitedByUsername: VIEWER_USERNAME,
    },
  ]

  invitations.set(slug, rows)
  return rows
}

// ── Organization ──────────────────────────────────────────────────────────────

export const createOrganization = async (
  payload: CreateOrganizationPayload,
): Promise<OrganizationDto> => {
  const { data } = await api.post<OrganizationDto>('/organizations', payload)
  return data
}

export const fetchOrgSettings = async (slug: string): Promise<OrgSettingsDto> => {
  const { data } = await api.get<OrgSettingsDto>(`/organizations/${slug}/settings`)
  return data
}

export const updateOrgProfile = async (
  slug: string,
  payload: UpdateOrgProfilePayload,
): Promise<OrgSettingsDto> => {
  const { data } = await api.put<OrgSettingsDto>(`/organizations/${slug}`, payload)
  return data
}

export const changeOrgSlug = async (slug: string, nextSlug: string): Promise<OrgSettingsDto> => {
  const { data } = await api.put<OrgSettingsDto>(`/organizations/${slug}/slug`, { slug: nextSlug })
  return data
}

export const transferOwnership = async (
  slug: string,
  userId: string,
  confirmSlug: string,
): Promise<void> => {
  await api.post(`/organizations/${slug}/transfer-ownership`, { userId, confirmSlug })
}

export const deleteOrg = async (slug: string, confirmSlug: string): Promise<void> => {
  await api.delete(`/organizations/${slug}`, { data: { confirmSlug } })
}

// ── Members ───────────────────────────────────────────────────────────────────

export const fetchOrgMembers = async (slug: string): Promise<OrgMemberDto[]> => {
  const { data } = await api.get<OrgMemberDto[]>(`/organizations/${slug}/members`)
  return data
}

export const setMemberRole = async (
  slug: string,
  userId: string,
  role: OrganizationRole,
): Promise<void> => {
  await api.put(`/organizations/${slug}/members/${userId}`, { role })
}

export const removeMember = async (slug: string, userId: string): Promise<void> => {
  await api.delete(`/organizations/${slug}/members/${userId}`)
}

// ── Invitations ───────────────────────────────────────────────────────────────

/**
 * MOCK - would be: const { data } = await api.get(`/organizations/${slug}/invitations`)
 * WAITING rows plus DECLINED rows the org has not dismissed, newest first. OWNER and
 * ADMIN only: the rows name people outside the organization.
 */
export const fetchInvitations = async (slug: string): Promise<InvitationDto[]> => {
  await delay(400)
  const viewerRole = viewerRoleIn(slug)
  if (viewerRole !== 'OWNER' && viewerRole !== 'ADMIN') {
    return rejectProblem(403, 'Pozivnice vide samo vlasnik i administratori.', 'Forbidden')
  }
  return seedInvitations(slug)
    .map((row) => ({ ...row }))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

/**
 * MOCK - would be: const { data } = await api.post(`/organizations/${slug}/invitations`, { identifier, role })
 * The backend classifies `identifier` as a username or an email and answers 201
 * either way, so the endpoint never becomes an account-existence oracle.
 */
export const createInvitation = async (
  slug: string,
  identifier: string,
  role: OrganizationRole,
): Promise<InvitationDto> => {
  await delay(520)

  const value = identifier.trim()
  const lower = value.toLowerCase()
  if (lower === MAGIC_UNKNOWN_IDENTIFIER) {
    return rejectProblem(422, 'Korisnik ne postoji.', 'Unprocessable Entity')
  }
  if (lower === MAGIC_EXISTING_MEMBER) {
    return rejectProblem(409, 'Korisnik je već član organizacije.', 'Conflict')
  }

  const isEmail = value.includes('@')
  const created: InvitationDto = {
    id: `inv-${slug}-${Date.now()}`,
    status: 'WAITING',
    invitee: isEmail
      ? null
      : { username: lower, fullName: lower.charAt(0).toUpperCase() + lower.slice(1), avatarUrl: null },
    email: isEmail ? lower : null,
    role,
    createdAt: new Date().toISOString(),
    resolvedAt: null,
    invitedByUsername: VIEWER_USERNAME,
  }

  invitations.set(slug, [created, ...seedInvitations(slug)])
  return { ...created }
}

/** MOCK - would be: await api.delete(`/organizations/${slug}/invitations/${invitationId}`) */
export const cancelInvitation = async (slug: string, invitationId: string): Promise<void> => {
  await delay(360)
  invitations.set(
    slug,
    seedInvitations(slug).filter((row) => row.id !== invitationId),
  )
}

/**
 * MOCK - would be: await api.post(`/organizations/${slug}/invitations/${invitationId}/dismiss`)
 * DECLINED rows only: sets `dismissedAt`, hiding the row without deleting the history.
 */
export const dismissInvitation = async (slug: string, invitationId: string): Promise<void> => {
  await delay(360)
  invitations.set(
    slug,
    seedInvitations(slug).filter((row) => row.id !== invitationId || row.status !== 'DECLINED'),
  )
}
