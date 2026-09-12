// -----------------------------------------------------------------------------
// MOCK "my memberships & my invitations" service (backend spec §3.3); the real
// axios call is left commented above each mock body. Rejections are
// `ProblemDetail` shapes, and one magic input reaches the error branch:
//   - leaveOrganization : the OWNER row's slug -> 409 "Prenesite vlasništvo…"
//
// The membership rows come from `mockActors.ts`, which is also where the profile,
// settings and job mocks read the same organizations from - one role per
// organization, one actor id per handle. A role declared here and contradicted
// there is what made every organization read as "not mine" on its own profile.
//
// The lists are MUTABLE module state so an accept produces the membership card the
// UI highlights and a refetch after leave/decline does not resurrect the row.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ActorRefDto } from './profiles'
import type { OrganizationRole } from './organizations'
import type { ProblemDetail } from './types'
import { ORGANIZATIONS, orgRef, personRef, setViewerRole, viewerMemberships, viewerRoleIn } from './mockActors'

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
}

/** An invitation addressed to me and still waiting. */
export interface MyInvitationDto {
  id: string
  organization: ActorRefDto
  role: OrganizationRole
  invitedBy: ActorRefDto | null
  createdAt: string
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

/** The organizations an invitation can come from: the ones the viewer does not
 *  start in, so accepting one is a state change rather than a no-op. */
const NON_MEMBER_ORGS = ORGANIZATIONS.filter((row) => row.initialViewerRole === null)

// ── Mutable stores (see the header note) ──────────────────────────────────────

let myOrganizations: MyOrganizationDto[] | null = null
let myInvitations: MyInvitationDto[] | null = null

/** Founder of the one organization the viewer owns; invited into the rest. */
const INVITERS: readonly (ActorRefDto | null)[] = [
  null,
  personRef('marko'),
  personRef('jelena'),
  personRef('ivana'),
]

const seedOrganizations = (): MyOrganizationDto[] => {
  if (!myOrganizations) {
    myOrganizations = viewerMemberships().map((row, index) => {
      const role = viewerRoleIn(row.slug) as OrganizationRole
      return {
        organization: orgRef(row.slug),
        role,
        joinedAt: iso(320 - index * 80),
        roleUpdatedAt: iso(320 - index * 90),
        invitedBy: INVITERS[index % INVITERS.length],
        memberCount: 10,
      }
    })
  }
  return myOrganizations
}

const seedInvitations = (): MyInvitationDto[] => {
  if (!myInvitations) {
    myInvitations = NON_MEMBER_ORGS.map((row, index) => ({
      id: `my-inv-${index + 1}`,
      organization: orgRef(row.slug),
      role: (index % 2 === 0 ? 'EDITOR' : 'RECRUITER') as OrganizationRole,
      invitedBy: index % 2 === 0 ? personRef('jelena') : personRef('marko'),
      createdAt: iso(2 + index * 9),
    }))
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
  setViewerRole(slug, null)
}

/** MOCK - would be: const { data } = await api.get(`/users/${username}/invitations`) */
export const fetchMyInvitations = async (username: string): Promise<MyInvitationDto[]> => {
  void username
  await delay(400)
  return seedInvitations().map((row) => ({ ...row }))
}

/**
 * MOCK - would be: await api.post(`/invitations/${id}/accept`)
 * The id is not a capability: the server also requires that the invitation targets
 * the caller, so a guessed id is a 403.
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
  if (already) return
  myOrganizations = [...seedOrganizations(), membershipFrom(invitation)]
  // The role is shared state: the profile head, the identity picker, the
  // settings page and the postings pane all read it, so joining has to move it
  // rather than only appending a row to this list.
  setViewerRole(invitation.organization.handle, invitation.role)
}

/** MOCK - would be: await api.post(`/invitations/${id}/decline`) */
export const declineInvitation = async (id: string): Promise<void> => {
  await delay(420)
  myInvitations = seedInvitations().filter((row) => row.id !== id)
}
