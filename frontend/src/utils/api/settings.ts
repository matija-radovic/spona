import api from '@utils/api'

import type { ContactEntry } from '@shared/components'

/** Boolean-only and additive. There is no public/private-account flag. */
export interface PrivacyDto {
  showJobPostings: boolean
}

export interface UserSettingsDto {
  id: string
  username: string
  /** Private: never in a public profile DTO. A *display* email is an otherContactInfo entry. */
  email: string
  fullName: string
  bio: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
  privacy: PrivacyDto
}

export interface UpdateProfilePayload {
  fullName: string
  bio: string | null
  avatar: string | null
  location: string | null
  otherContactInfo: ContactEntry[]
}

export interface ChangeUsernamePayload {
  username: string
}

export interface ChangeEmailPayload {
  email: string
  currentPassword: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

export const fetchUserSettings = async (username: string): Promise<UserSettingsDto> => {
  const { data } = await api.get<UserSettingsDto>(`/users/${username}/settings`)
  return data
}

export const updateProfile = async (
  username: string,
  payload: UpdateProfilePayload,
): Promise<UserSettingsDto> => {
  const { data } = await api.put<UserSettingsDto>(`/users/${username}`, payload)
  return data
}

/** The URL carries the username, so the caller must move the address bar with it. */
export const changeUsername = async (
  username: string,
  payload: ChangeUsernamePayload,
): Promise<UserSettingsDto> => {
  const { data } = await api.put<UserSettingsDto>(`/users/${username}/username`, payload)
  return data
}

/** A request, not a write: the address changes when the link mailed to it is opened. */
export const changeEmail = async (username: string, payload: ChangeEmailPayload): Promise<void> => {
  await api.post(`/users/${username}/email`, payload)
}

export const changePassword = async (
  username: string,
  payload: ChangePasswordPayload,
): Promise<void> => {
  await api.put(`/users/${username}/password`, payload)
}

/** Privacy is per actor, so one endpoint serves both privacy sections. */
export const updatePrivacy = async (actorId: string, privacy: PrivacyDto): Promise<void> => {
  await api.put(`/actors/${actorId}/privacy`, privacy)
}

export const deleteAccount = async (username: string, password: string): Promise<void> => {
  await api.delete(`/users/${username}`, { data: { password } })
}
