// -----------------------------------------------------------------------------
// "My memberships & my invitations" service. The membership functions call the
// backend; the invitation functions are still MOCKS, with the real axios call
// left commented above each mock body. Their rejections are `ProblemDetail`
// shapes.
//
// The invitations come from `mockActors.ts`, which is also where the profile,
// settings and job mocks read the same organizations from - one role per
// organization, one actor id per handle.
//
// The invitation list is MUTABLE module state so a refetch after accept/decline
// does not resurrect the row.
// -----------------------------------------------------------------------------

import api from '@utils/api'

import type { ActorRefDto } from './profiles'
import type { OrganizationRole } from './organizations'
import type { ProblemDetail } from './types'
import { ORGANIZATIONS, orgRef, personRef, setViewerRole, viewerRoleIn } from './mockActors'

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

// ── Mutable store (see the header note) ───────────────────────────────────────

let myInvitations: MyInvitationDto[] | null = null

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

// ── Calls ─────────────────────────────────────────────────────────────────────

export const fetchMyOrganizations = async (username: string): Promise<MyOrganizationDto[]> => {
  const { data } = await api.get<MyOrganizationDto[]>(`/users/${username}/organizations`)
  return data
}

export const leaveOrganization = async (username: string, slug: string): Promise<void> => {
  await api.delete(`/users/${username}/organizations/${slug}`)
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
  // Joining twice is not an error worth showing a person.
  if (viewerRoleIn(invitation.organization.handle) !== null) return
  // The role is shared state: the profile head, the identity picker, the
  // settings page and the postings pane all read it.
  setViewerRole(invitation.organization.handle, invitation.role)
}

/** MOCK - would be: await api.post(`/invitations/${id}/decline`) */
export const declineInvitation = async (id: string): Promise<void> => {
  await delay(420)
  myInvitations = seedInvitations().filter((row) => row.id !== id)
}
