import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import { getToken } from '@utils/authToken'

type AdditionalContactInformation = ContactEntry[]

export interface UserDto {
  id: string
  actorType: 'user'
  createdAt: string
  username: string
  fullName: string
  email: string
  bio: string | null
  avatarUrl: string | null
  location: string | null
  otherContactInfo: AdditionalContactInformation
}

export interface AuthResponse {
  token: string
  user: UserDto
}

export interface LoginPayload {
  identifier: string
  password: string
}

export interface CreateUserPayload {
  username: string
  email: string
  password: string
  fullName: string
  bio: string | null
  avatar: string | null
  location: string | null
  otherContactInfo: AdditionalContactInformation
}

/**
 * An unverified account is `403` with `/problems/email-not-verified`, and that
 * arm mails a fresh link itself, so the form only says to check the inbox. Bad
 * credentials are a plain 401.
 */
export const loginRequest = async (payload: LoginPayload): Promise<AuthResponse> => {
  const { data } = await api.post<AuthResponse>('/auth/login', payload)
  return data
}

export const fetchMe = async (): Promise<UserDto> => {
  const { data } = await api.get<UserDto>('/auth/me')
  return data
}

export const logoutRequest = async (): Promise<void> => {
  const token = getToken()
  await api.post('/auth/logout', null, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })
}

export const registerUser = async (payload: CreateUserPayload): Promise<void> => {
  await api.post('/auth/register', payload)
}

export const verifyEmail = async (token: string): Promise<void> => {
  await api.post('/auth/verify', { token })
}

export const resendVerification = async (email: string): Promise<void> => {
  await api.post('/auth/verify/resend', { email })
}

export const checkUsernameAvailable = async (username: string): Promise<boolean> => {
  const { data } = await api.get<boolean>('/users', { params: { username } })
  return data
}

export const requestPasswordReset = async (email: string): Promise<void> => {
  await api.post('/auth/password-reset', { email })
}

export const confirmPasswordReset = async (token: string, newPassword: string): Promise<void> => {
  await api.post('/auth/password-reset/confirm', { token, newPassword })
}
