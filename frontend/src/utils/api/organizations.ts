// -----------------------------------------------------------------------------
// MOCK "an organization I belong to" service (backend spec §3.1/§3.2); the real
// axios call is left commented above each mock body. Rejections are
// `ProblemDetail` shapes, and these magic inputs reach every persona and failure:
//   - fetchOrgSettings   : "moja-kompanija" -> OWNER · "vega-it" -> ADMIN
//                          "nordeus" -> EDITOR · "htec-group" -> RECRUITER
//                          "quantox" -> PUBLISHER
//                          any other slug -> 403 (not a member; the guard bounces)
//                          "nepostojeci" -> 404
//   - changeOrgSlug      : nextSlug === "zauzeto"       -> 409 Conflict
//   - transferOwnership  : confirmSlug !== slug         -> 422 Unprocessable
//   - deleteOrg          : confirmSlug !== slug         -> 422 Unprocessable
//   - setMemberRole      : userId === MAGIC_FAILING_MEMBER_ID -> 500 (revert test)
//   - removeMember       : the same id                  -> 500
//   - fetchInvitations   : any seat below ADMIN         -> 403 (OWNER and ADMIN only)
//   - createInvitation   : identifier === "nepostojeci" -> 422 "Korisnik ne postoji."
//                          identifier === "marko"       -> 409 already a member
//                          identifier containing "@"    -> 201, unresolved row
//
// Every role comes from `mockActors.ts`, which is where the profile, membership
// and job mocks read the same organizations from. A slug the viewer holds no role
// in is a `403` rather than a synthesized ADMIN seat: the settings page and the
// profile head have to agree about who you are, and the head reads the role.
//
// The member and invitation lists are MUTABLE module state, seeded per slug on first
// read; a pure mock would silently undo every successful action on the next `Osveži`.
//
// Creating an invitation SENDS AN EMAIL (backend spec §6); cancel/decline/dismiss do not.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PrivacyDto } from './settings'
import type { ProblemDetail } from './types'
import { PEOPLE, VIEWER, findOrg, orgIdFor, userIdFor, viewerRoleIn } from './mockActors'

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
  avatarUrl: string | null
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
  /** Set from the create form's AvatarPicker; settings edits it later. */
  avatarUrl: string | null
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

const MAGIC_NOT_FOUND_SLUG = 'nepostojeci'
const MAGIC_TAKEN_SLUG = 'zauzeto'
const MAGIC_UNKNOWN_IDENTIFIER = 'nepostojeci'
const MAGIC_EXISTING_MEMBER = 'marko'

/** The one member whose every mutation fails, so the optimistic revert is testable. */
export const MAGIC_FAILING_MEMBER_ID = userIdFor('luka')

const VIEWER_USERNAME = VIEWER.username

const titleFromSlug = (slug: string): string =>
  slug
    .split('-')
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')

// ── Mutable stores (see the header note) ──────────────────────────────────────

const organizations = new Map<string, OrgSettingsDto>()
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

/**
 * The viewer's live role in an organization. A fixture organization answers from
 * the shared membership state, so joining or leaving one is felt here too and a
 * cached row cannot keep serving a role the viewer no longer holds. An
 * organization created or renamed inside the session has no fixture entry, so it
 * answers from its own stored row.
 */
const liveRoleOf = (slug: string): OrganizationRole | null =>
  findOrg(slug) ? viewerRoleIn(slug) : (organizations.get(slug)?.viewerRole ?? null)

/** As above, for the mutation paths: they all run after the settings page loaded
 *  the org, so the last arm is only reachable by a direct call in a test. */
const roleOf = (slug: string): OrganizationRole => liveRoleOf(slug) ?? 'ADMIN'

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

/** Only ever called for a slug the viewer holds a role in; `fetchOrgSettings`
 *  refuses the rest, so `viewerRole` is never invented here. */
const seedOrg = (slug: string, viewerRole: OrganizationRole = roleOf(slug)): OrgSettingsDto => {
  const existing = organizations.get(slug)
  if (existing) return existing

  const seed = findOrg(slug)
  const displayName = seed?.name ?? titleFromSlug(slug)
  const org: OrgSettingsDto = {
    id: orgIdFor(slug),
    slug,
    displayName,
    legalName: seed?.legalName ?? `${displayName} d.o.o.`,
    description: seed?.description ?? 'Softverska kompanija sa timovima u Beogradu i Novom Sadu.',
    website: seed?.website ?? `https://${slug}.rs`,
    avatarUrl: null,
    location: seed?.location ?? 'Beograd',
    otherContactInfo: [
      { type: 'email', description: 'Kontakt', value: 'kontakt@primer.rs' },
      { type: 'phone', description: 'Telefon', value: '+381 11 234 567' },
    ],
    privacy: { showJobPostings: true },
    viewerRole,
    memberCount: seedMembers(slug, viewerRole).length,
  }

  organizations.set(slug, org)
  return org
}

const store = (slug: string, next: OrgSettingsDto): OrgSettingsDto => {
  organizations.set(slug, next)
  return { ...next }
}

// ── Organization ──────────────────────────────────────────────────────────────

/**
 * MOCK - would be: const { data } = await api.post('/organizations', payload)
 * Authenticated (backend-contract §2): the caller becomes the OWNER. Slug
 * `zauzeto` reaches the taken-slug path.
 */
export const createOrganization = async (
  payload: CreateOrganizationPayload,
): Promise<OrganizationDto> => {
  await delay(600)
  if (payload.slug.trim().toLowerCase() === MAGIC_TAKEN_SLUG) {
    return rejectProblem(409, 'Adresa organizacije je već zauzeta.', 'Conflict')
  }
  return {
    id: orgIdFor(payload.slug),
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

/**
 * MOCK - would be: const { data } = await api.get(`/organizations/${slug}/settings`)
 * Membership is the gate, and it is the same membership the profile head and the
 * identity picker read: an organization the viewer holds no role in is a `403`,
 * which `OrgSettings` turns into a bounce to the public profile.
 */
export const fetchOrgSettings = async (slug: string): Promise<OrgSettingsDto> => {
  await delay(430)
  if (slug === MAGIC_NOT_FOUND_SLUG) {
    return rejectProblem(404, 'Tražena organizacija ne postoji.', 'Not Found')
  }
  const viewerRole = liveRoleOf(slug)
  if (!viewerRole) {
    return rejectProblem(403, 'Niste član ove organizacije.', 'Forbidden')
  }
  const org = seedOrg(slug, viewerRole)
  return { ...org, viewerRole, memberCount: seedMembers(slug, viewerRole).length }
}

/** MOCK - would be: const { data } = await api.put(`/organizations/${slug}`, payload) */
export const updateOrgProfile = async (
  slug: string,
  payload: UpdateOrgProfilePayload,
): Promise<OrgSettingsDto> => {
  await delay(520)
  return store(slug, { ...seedOrg(slug), ...payload })
}

/**
 * MOCK - would be: const { data } = await api.put(`/organizations/${slug}/slug`, { slug: nextSlug })
 * The slug is the org's URL identity, so a success moves every stored row with it.
 */
export const changeOrgSlug = async (slug: string, nextSlug: string): Promise<OrgSettingsDto> => {
  await delay(480)
  if (nextSlug.trim().toLowerCase() === MAGIC_TAKEN_SLUG) {
    return rejectProblem(409, 'Adresa organizacije je već zauzeta.', 'Conflict')
  }

  const org = seedOrg(slug)
  const memberRows = seedMembers(slug)
  const invitationRows = seedInvitations(slug)
  organizations.delete(slug)
  members.delete(slug)
  invitations.delete(slug)
  members.set(nextSlug, memberRows)
  invitations.set(nextSlug, invitationRows)

  return store(nextSlug, { ...org, slug: nextSlug })
}

/**
 * MOCK - would be:
 *   await api.post(`/organizations/${slug}/transfer-ownership`, { userId, confirmSlug })
 * Only to an ADMIN, demoting the previous owner in the same transaction.
 */
export const transferOwnership = async (
  slug: string,
  userId: string,
  confirmSlug: string,
): Promise<void> => {
  await delay(620)
  if (confirmSlug !== slug) {
    return rejectProblem(422, 'Uneta adresa se ne poklapa sa adresom organizacije.', 'Unprocessable Entity')
  }

  const rows = seedMembers(slug)
  members.set(
    slug,
    rows.map((row) => {
      if (row.userId === userId) return { ...row, role: 'OWNER' }
      if (row.role === 'OWNER') return { ...row, role: 'ADMIN' }
      return row
    }),
  )

  const org = organizations.get(slug)
  if (org) organizations.set(slug, { ...org, viewerRole: 'ADMIN' })
}

/** MOCK - would be: await api.delete(`/organizations/${slug}`, { data: { confirmSlug } }) */
export const deleteOrg = async (slug: string, confirmSlug: string): Promise<void> => {
  await delay(600)
  if (confirmSlug !== slug) {
    return rejectProblem(422, 'Uneta adresa se ne poklapa sa adresom organizacije.', 'Unprocessable Entity')
  }
  organizations.delete(slug)
  members.delete(slug)
  invitations.delete(slug)
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
  const viewerRole = liveRoleOf(slug)
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
