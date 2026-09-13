import api from '@utils/api'

import type { ActorRefDto } from './profiles'
import type { OrganizationRole } from './organizations'

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

export const fetchMyOrganizations = async (username: string): Promise<MyOrganizationDto[]> => {
  const { data } = await api.get<MyOrganizationDto[]>(`/users/${username}/organizations`)
  return data
}

export const leaveOrganization = async (username: string, slug: string): Promise<void> => {
  await api.delete(`/users/${username}/organizations/${slug}`)
}

export const fetchMyInvitations = async (username: string): Promise<MyInvitationDto[]> => {
  const { data } = await api.get<MyInvitationDto[]>(`/users/${username}/invitations`)
  return data
}

export const acceptInvitation = async (id: string): Promise<void> => {
  await api.post(`/invitations/${id}/accept`)
}

export const declineInvitation = async (id: string): Promise<void> => {
  await api.post(`/invitations/${id}/decline`)
}
