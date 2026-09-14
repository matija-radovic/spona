import api from '@utils/api'

import type { ActorRefDto } from './profiles'
import type { OrganizationRole } from './organizations'

export interface MyOrganizationDto {
  /** Always an organization */
  organization: ActorRefDto
  role: OrganizationRole
  joinedAt: string
  /** Equals joinedAt until the first role change */
  roleUpdatedAt: string
  /** Null for a founder */
  invitedBy: ActorRefDto | null
  memberCount: number
}

/** A waiting invitation addressed to the caller */
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
