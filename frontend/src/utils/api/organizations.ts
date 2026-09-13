// -----------------------------------------------------------------------------
// "An organization I belong to" service. The organization functions call the
// backend; the member and invitation functions are still MOCKS, with the real
// axios call left commented above each mock body. Their rejections are
// `ProblemDetail` shapes, and these magic inputs reach every persona and failure:
//   - setMemberRole      : userId === MAGIC_FAILING_MEMBER_ID -> 500 (revert test)
//   - removeMember       : the same id                  -> 500
//   - fetchInvitations   : any seat below ADMIN         -> 403 (OWNER and ADMIN only)
//   - createInvitation   : identifier === "nepostojeci" -> 422 "Korisnik ne postoji."
//                          identifier === "marko"       -> 409 already a member
//                          identifier containing "@"    -> 201, unresolved row
//
// Every role comes from `mockActors.ts`, which is where the profile, membership
// and job mocks read the same organizations from.
//
// The member and invitation lists are MUTABLE module state, seeded per slug on first
// read; a pure mock would silently undo every successful action on the next `Osveži`.
//
// Creating an invitation SENDS AN EMAIL; cancel/decline/dismiss do not.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PrivacyDto } from './settings'
import type { ProblemDetail } from './types'
import { PEOPLE, VIEWER, userIdFor, viewerRoleIn } from './mockActors'

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
  /** ISO - when they joined. */
  assignedAt: string
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

/** The one member whose every mutation fails, so the optimistic revert is testable. */
export const MAGIC_FAILING_MEMBER_ID = userIdFor('luka')

const VIEWER_USERNAME = VIEWER.username

// ── Mutable stores (see the header note) ──────────────────────────────────────

const members = new Map<string, OrgMemberDto[]>()
const invitations = new Map<string, InvitationDto[]>()

/** Everyone but the viewer, by handle. Exactly one OWNER and two ADMINs, so
 *  transfer always has options; the names come from the shared fixture. */
const ROSTER: readonly { username: string; role: OrganizationRole }[] = [
  { username: 'marko', role: 'OWNER' },
  { username: 'jelena', role: 'ADMIN' },
  { username: 'nikola', role: 'ADMIN' },
  { username: 'milica', role: 'EDITOR' },
  { username: 'stefan', role: 'EDITOR' },
  { username: 'ivana', role: 'RECRUITER' },
  { username: 'luka', role: 'RECRUITER' },
  { username: 'dusan', role: 'PUBLISHER' },
  { username: 'tijana', role: 'RECRUITER' },
]

const fullNameOf = (username: string): string =>
  PEOPLE.find((row) => row.username === username)?.fullName ?? username

/** The viewer's role from the shared membership state, or ADMIN for an organization
 *  the fixtures do not know. */
const roleOf = (slug: string): OrganizationRole => viewerRoleIn(slug) ?? 'ADMIN'

const seedMembers = (slug: string, viewerRole: OrganizationRole = roleOf(slug)): OrgMemberDto[] => {
  const existing = members.get(slug)
  if (existing) return existing

  const viewerIsOwner = viewerRole === 'OWNER'

  const rows: OrgMemberDto[] = [
    {
      userId: userIdFor(VIEWER_USERNAME),
      username: VIEWER_USERNAME,
      fullName: VIEWER.fullName,
      avatarUrl: null,
      role: viewerRole,
      assignedAt: iso(320),
    },
    // Demote the founder when the viewer owns the org, keeping EXACTLY ONE OWNER row.
    ...ROSTER.map((member, index) => ({
      userId: userIdFor(member.username),
      username: member.username,
      fullName: fullNameOf(member.username),
      avatarUrl: null,
      role: viewerIsOwner && member.role === 'OWNER' ? ('ADMIN' as OrganizationRole) : member.role,
      assignedAt: iso(300 - index * 24),
    })),
  ]

  members.set(slug, rows)
  return rows
}

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

/**
 * MOCK - would be: const { data } = await api.get(`/organizations/${slug}/members`)
 * Bare array (a member list is a bounded set - no paging speculatively).
 */
export const fetchOrgMembers = async (slug: string): Promise<OrgMemberDto[]> => {
  await delay(1420)
  return seedMembers(slug).map((row) => ({ ...row }))
}

/** MOCK - would be: await api.put(`/organizations/${slug}/members/${userId}`, { role }) */
export const setMemberRole = async (
  slug: string,
  userId: string,
  role: OrganizationRole,
): Promise<void> => {
  await delay(380)
  if (userId === MAGIC_FAILING_MEMBER_ID) {
    return rejectProblem(500, 'Greška servera. Pokušajte ponovo.', 'Internal Server Error')
  }
  members.set(
    slug,
    seedMembers(slug).map((row) => (row.userId === userId ? { ...row, role } : row)),
  )
}

/** MOCK - would be: await api.delete(`/organizations/${slug}/members/${userId}`) */
export const removeMember = async (slug: string, userId: string): Promise<void> => {
  await delay(380)
  if (userId === MAGIC_FAILING_MEMBER_ID) {
    return rejectProblem(500, 'Greška servera. Pokušajte ponovo.', 'Internal Server Error')
  }
  members.set(
    slug,
    seedMembers(slug).filter((row) => row.userId !== userId),
  )
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
