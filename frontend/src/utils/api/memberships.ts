// -----------------------------------------------------------------------------
// MOCK "my memberships & my invitations" service (backend spec §3.3); the real
// axios call is left commented above each mock body. Rejections are
// `ProblemDetail` shapes, and these magic inputs reach every branch:
//   - leaveOrganization      : the OWNER row's slug -> 409 "Prenesite vlasništvo…"
//   - resolveInvitationToken : "otkazana"    -> CANCELED
//                              "prihvacena"  -> ACCEPTED
//                              "odbijena"    -> DECLINED
//                              "tudja"       -> WAITING, targetsCurrentUser false
//                              "nepostojeci" -> 404
//                              anything else -> a healthy WAITING preview, bound
//                                               to a real row in "my invitations"
//
// The lists are MUTABLE module state so an accept produces the membership card the
// UI highlights and a refetch after leave/decline does not resurrect the row.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ActorRefDto } from './profiles'
import type { OrganizationRole } from './organizations'
import type { ProblemDetail } from './types'

// ── DTOs ──────────────────────────────────────────────────────────────────────

/** One organization I belong to, plus the facts about *my* membership. */
export interface MyOrganizationDto {
  /** Always actorType 'org'; ActorRefDto so ActorAvatar/actorPath work unchanged. */
  organization: ActorRefDto
  role: OrganizationRole
  joinedAt: string
  /** When my role last changed; equals joinedAt until the first change. */
  roleUpdatedAt: string
  /** Who invited me - null for a founder. */
  invitedBy: ActorRefDto | null
  memberCount: number
  /** OWNER/ADMIN - show a link into the org's settings. */
  canManage: boolean
}

/** An invitation addressed to me and still waiting. */
export interface MyInvitationDto {
  id: string
  organization: ActorRefDto
  role: OrganizationRole
  invitedBy: ActorRefDto | null
  createdAt: string
}

/** What `/i/:token` resolves to, before the viewer is necessarily logged in. */
export interface InvitationPreviewDto {
  id: string
  status: 'WAITING' | 'ACCEPTED' | 'DECLINED' | 'CANCELED'
  organization: ActorRefDto
  role: OrganizationRole
  invitedBy: ActorRefDto | null
  /** Authenticated callers only: false means the invitation went to a different account. */
  targetsCurrentUser?: boolean
}

// ── Mock plumbing ─────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/invitations',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const iso = (daysAgo: number): string =>
  new Date(Date.now() - daysAgo * 24 * 60 * 60 * 1000).toISOString()

const MAGIC_NOT_FOUND_TOKEN = 'nepostojeci'
const MAGIC_TOKEN_STATUS: Readonly<Record<string, InvitationPreviewDto['status']>> = {
  otkazana: 'CANCELED',
  prihvacena: 'ACCEPTED',
  odbijena: 'DECLINED',
}
const MAGIC_OTHER_ACCOUNT_TOKEN = 'tudja'

// Mock actor ids are derived from the handle so a given handle always refers to
// the same actor across this module. The real backend supplies UUIDs.
const org = (handle: string, name: string): ActorRefDto => ({
  actorType: 'org',
  id: `org-${handle}`,
  name,
  handle,
  avatarUrl: null,
})

const person = (handle: string, name: string): ActorRefDto => ({
  actorType: 'user',
  id: `user-${handle}`,
  name,
  handle,
  avatarUrl: null,
})

// ── Mutable stores (see the header note) ──────────────────────────────────────

let myOrganizations: MyOrganizationDto[] | null = null
let myInvitations: MyInvitationDto[] | null = null

const seedOrganizations = (): MyOrganizationDto[] => {
  if (!myOrganizations) {
    myOrganizations = [
      {
        organization: org('moja-kompanija', 'Moja Kompanija'),
        role: 'OWNER',
        joinedAt: iso(320),
        roleUpdatedAt: iso(320),
        invitedBy: null, // founder - created the organization at registration
        memberCount: 10,
        canManage: true,
      },
      {
        organization: org('vega-it', 'Vega IT'),
        role: 'ADMIN',
        joinedAt: iso(148),
        roleUpdatedAt: iso(67),
        invitedBy: person('marko', 'Marko Marković'),
        memberCount: 10,
        canManage: true,
      },
      {
        organization: org('htec-group', 'HTEC Group'),
        role: 'RECRUITER',
        joinedAt: iso(54),
        roleUpdatedAt: iso(54),
        invitedBy: person('jelena', 'Jelena Petrović'),
        memberCount: 10,
        canManage: false,
      },
    ]
  }
  return myOrganizations
}

const seedInvitations = (): MyInvitationDto[] => {
  if (!myInvitations) {
    myInvitations = [
      {
        id: 'my-inv-1',
        organization: org('nordeus', 'Nordeus'),
        role: 'EDITOR',
        invitedBy: person('jelena', 'Jelena Petrović'),
        createdAt: iso(2),
      },
      {
        id: 'my-inv-2',
        organization: org('levi9', 'Levi9'),
        role: 'RECRUITER',
        invitedBy: person('marko', 'Marko Marković'),
        createdAt: iso(11),
      },
    ]
  }
  return myInvitations
}

/** The membership an accepted invitation turns into. */
const membershipFrom = (invitation: MyInvitationDto): MyOrganizationDto => ({
  organization: invitation.organization,
  role: invitation.role,
  joinedAt: new Date().toISOString(),
  roleUpdatedAt: new Date().toISOString(),
  invitedBy: invitation.invitedBy,
  memberCount: 11,
  canManage: invitation.role === 'OWNER' || invitation.role === 'ADMIN',
})

// ── Calls ─────────────────────────────────────────────────────────────────────

/**
 * MOCK - would be: const { data } = await api.get(`/users/${username}/organizations`)
 * Bare array - you belong to a handful of organizations, so there is no paging.
 */
export const fetchMyOrganizations = async (username: string): Promise<MyOrganizationDto[]> => {
  void username
  await delay(430)
  return seedOrganizations().map((row) => ({ ...row }))
}

/**
 * MOCK - would be: await api.delete(`/users/${username}/organizations/${slug}`)
 * The UI hides the control on an OWNER row; this proves the SERVER is the real gate.
 */
export const leaveOrganization = async (username: string, slug: string): Promise<void> => {
  void username
  await delay(480)

  const row = seedOrganizations().find((entry) => entry.organization.handle === slug)
  if (row?.role === 'OWNER') {
    return rejectProblem(409, 'Prenesite vlasništvo pre napuštanja organizacije.', 'Conflict')
  }

  myOrganizations = seedOrganizations().filter((entry) => entry.organization.handle !== slug)
}

/** MOCK - would be: const { data } = await api.get(`/users/${username}/invitations`) */
export const fetchMyInvitations = async (username: string): Promise<MyInvitationDto[]> => {
  void username
  await delay(400)
  return seedInvitations().map((row) => ({ ...row }))
}

/**
 * MOCK - would be: const { data } = await api.get(`/invitations/by-token/${token}`)
 * PUBLIC, so the preview carries only what the email already said, plus
 * `targetsCurrentUser` for an authenticated caller. A healthy token always resolves
 * to a REAL waiting row - one is created if none is left, so the link never dead-ends.
 */
export const resolveInvitationToken = async (token: string): Promise<InvitationPreviewDto> => {
  await delay(450)

  if (token === MAGIC_NOT_FOUND_TOKEN) {
    return rejectProblem(404, 'Pozivnica ne postoji.', 'Not Found')
  }

  const terminal = MAGIC_TOKEN_STATUS[token]
  if (terminal) {
    return {
      id: `inv-token-${token}`,
      status: terminal,
      organization: org('nordeus', 'Nordeus'),
      role: 'EDITOR',
      invitedBy: person('jelena', 'Jelena Petrović'),
      targetsCurrentUser: true,
    }
  }

  if (token === MAGIC_OTHER_ACCOUNT_TOKEN) {
    return {
      id: `inv-token-${token}`,
      status: 'WAITING',
      organization: org('vega-it', 'Vega IT'),
      role: 'ADMIN',
      invitedBy: person('marko', 'Marko Marković'),
      targetsCurrentUser: false,
    }
  }

  const waiting = seedInvitations()
  if (waiting.length === 0) {
    const revived: MyInvitationDto = {
      id: `my-inv-${Date.now()}`,
      organization: org('nordeus', 'Nordeus'),
      role: 'EDITOR',
      invitedBy: person('jelena', 'Jelena Petrović'),
      createdAt: new Date().toISOString(),
    }
    myInvitations = [revived]
    return { ...revived, status: 'WAITING', targetsCurrentUser: true }
  }

  const row = waiting[0]
  return {
    id: row.id,
    status: 'WAITING',
    organization: row.organization,
    role: row.role,
    invitedBy: row.invitedBy,
    targetsCurrentUser: true,
  }
}

/**
 * MOCK - would be: await api.post(`/invitations/${id}/accept`)
 * Addressed by row ID, never the token, keeping it out of history and `Referer` headers.
 */
export const acceptInvitation = async (id: string): Promise<void> => {
  await delay(520)

  const invitation = seedInvitations().find((row) => row.id === id)
  if (!invitation) {
    return rejectProblem(404, 'Pozivnica ne postoji.', 'Not Found')
  }

  myInvitations = seedInvitations().filter((row) => row.id !== id)
  const already = seedOrganizations().some(
    (row) => row.organization.handle === invitation.organization.handle,
  )
  // Joining twice is not an error worth showing a person (backend spec §4.3).
  if (!already) myOrganizations = [...seedOrganizations(), membershipFrom(invitation)]
}

/** MOCK - would be: await api.post(`/invitations/${id}/decline`) */
export const declineInvitation = async (id: string): Promise<void> => {
  await delay(420)
  myInvitations = seedInvitations().filter((row) => row.id !== id)
}
