import api from '@utils/api'

import type { ContactEntry } from '@shared/components'

export interface PrivacyDto {
  showJobPostings: boolean
}

export interface UserSettingsDto {
  id: string
  username: string
  /** Private, never in a public profile */
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

export const changeUsername = async (
  username: string,
  payload: ChangeUsernamePayload,
): Promise<UserSettingsDto> => {
  const { data } = await api.put<UserSettingsDto>(`/users/${username}/username`, payload)
  return data
}

export const changeEmail = async (username: string, payload: ChangeEmailPayload): Promise<void> => {
  await api.post(`/users/${username}/email`, payload)
}

export const changePassword = async (
  username: string,
  payload: ChangePasswordPayload,
): Promise<void> => {
  await api.put(`/users/${username}/password`, payload)
}

export const updatePrivacy = async (actorId: string, privacy: PrivacyDto): Promise<void> => {
  await api.put(`/actors/${actorId}/privacy`, privacy)
}

export const deleteAccount = async (username: string, password: string): Promise<void> => {
  await api.delete(`/users/${username}`, { data: { password } })
}
