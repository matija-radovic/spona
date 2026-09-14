import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PagedResponse } from './types'

// --- DTOs ---

export interface ActorRefDto {
  actorType: 'user' | 'org'
  /** Globally unique, unlike the handle */
  id: string
  /** fullName of a user, displayName of an org */
  name: string
  /** username of a user, slug of an org */
  handle: string
  avatarUrl: string | null
}

/** Carries no email, a displayed email is an otherContactInfo entry */
export type ProfileDto =
  | {
    actorType: 'user'
    id: string
    username: string
    fullName: string
    bio: string | null
    avatarUrl: string | null
    location: string | null
    otherContactInfo: ContactEntry[]
    followers: number
    following: number
    createdAt: string
    /** Authenticated viewers only */
    isFollowedByViewer?: boolean
    viewerCanEdit?: boolean
    /** Separate from viewerCanEdit, a RECRUITER manages postings but not the profile */
    viewerCanManageJobPostings?: boolean
  }
  | {
    actorType: 'org'
    id: string
    slug: string
    displayName: string
    description: string | null
    website: string | null
    avatarUrl: string | null
    location: string | null
    otherContactInfo: ContactEntry[]
    followers: number
    createdAt: string
    isFollowedByViewer?: boolean
    viewerCanEdit?: boolean
    viewerCanManageJobPostings?: boolean
  }

export type ActorSearchItemDto =
  | {
    actorType: 'user'
    id: string
    name: string
    handle: string
    avatarUrl: string | null
    bio: string | null
    followers: number
    following: number
    isFollowedByViewer?: boolean
  }
  | {
    actorType: 'org'
    id: string
    name: string
    handle: string
    avatarUrl: string | null
    description: string | null
    followers: number
    isFollowedByViewer?: boolean
  }

export interface FollowActorDto extends ActorRefDto {
  isFollowedByViewer?: boolean
}

export type FollowDirection = 'followers' | 'following'

// --- Calls ---

export const fetchProfile = async (
  actorType: 'user' | 'org',
  handle: string,
): Promise<ProfileDto> => {
  const { data } = await api.get<ProfileDto>(
    actorType === 'org' ? `/organizations/${handle}` : `/users/${handle}`,
  )
  return data
}

export const setFollow = async (actorId: string, next: boolean): Promise<void> => {
  if (next) await api.put(`/actors/${actorId}/follow`)
  else await api.delete(`/actors/${actorId}/follow`)
}

export const searchActors = async (
  q: string,
  type: 'org' | 'user',
  page = 1,
  size = 20,
): Promise<PagedResponse<ActorSearchItemDto>> => {
  const { data } = await api.get<PagedResponse<ActorSearchItemDto>>('/actors', {
    params: { q, type, page, size },
  })
  return data
}

export const FOLLOW_PAGE_SIZE = 20

export const fetchFollowList = async (
  actorId: string,
  direction: FollowDirection,
  q: string,
  page = 1,
  size = FOLLOW_PAGE_SIZE,
): Promise<PagedResponse<FollowActorDto>> => {
  const { data } = await api.get<PagedResponse<FollowActorDto>>(
    `/actors/${actorId}/${direction}`,
    { params: { q, page, size } },
  )
  return data
}
