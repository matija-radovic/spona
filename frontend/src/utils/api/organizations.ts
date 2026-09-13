import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PrivacyDto } from './settings'

export type OrganizationRole = 'OWNER' | 'ADMIN' | 'PUBLISHER' | 'EDITOR' | 'RECRUITER'

// --- DTOs ---

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
  /** Null once the inviter's account is gone. */
  invitedByUsername: string | null
}

// --- Organization ---

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

// --- Members ---

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

// --- Invitations ---

export const fetchInvitations = async (slug: string): Promise<InvitationDto[]> => {
  const { data } = await api.get<InvitationDto[]>(`/organizations/${slug}/invitations`)
  return data
}

/** The backend classifies `identifier` as a username or an address and never looks an address up. */
export const createInvitation = async (
  slug: string,
  identifier: string,
  role: OrganizationRole,
): Promise<InvitationDto> => {
  const { data } = await api.post<InvitationDto>(`/organizations/${slug}/invitations`, {
    identifier,
    role,
  })
  return data
}

export const cancelInvitation = async (slug: string, invitationId: string): Promise<void> => {
  await api.delete(`/organizations/${slug}/invitations/${invitationId}`)
}

/** DECLINED rows only: sets `dismissedAt`, hiding the row without deleting the history. */
export const dismissInvitation = async (slug: string, invitationId: string): Promise<void> => {
  await api.post(`/organizations/${slug}/invitations/${invitationId}/dismiss`)
}
