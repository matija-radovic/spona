import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PagedResponse } from './types'

// --- DTOs ---

/** Stripped actor reference used everywhere an author/identity renders. */
export interface ActorRefDto {
  actorType: 'user' | 'org'
  /** The only globally unique address, and what the `/actors/{actorId}/…`
   *  routes take. A handle is unique only within its own subtype. */
  id: string
  /** fullName (user) or displayName (org). */
  name: string
  /** username (user) or org slug - builds the profile URL. */
  handle: string
  avatarUrl: string | null
}

/** Full profile payload. Email is intentionally ABSENT - private information;
 *  a display email only ever appears as an otherContactInfo entry. */
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
    /** Present ONLY for authenticated viewers. */
    isFollowedByViewer?: boolean
    /** True when this is the viewer's own profile (or an org they can edit). */
    viewerCanEdit?: boolean
    /** Server-computed from the role matrix, absent meaning false. Separate
     *  from `viewerCanEdit` because a RECRUITER manages an organization's
     *  postings and not its profile, and an EDITOR manages neither. */
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

/** Actor search-result summary. Stripped down from `ProfileDto` to the fields
 *  a search row can plausibly need (no location/contact/createdAt/
 *  viewerCanEdit) - but, unlike `ActorRefDto`, still a real per-type summary
 *  (bio/description + follower counts), not just an identity reference. `id` is
 *  here because the row's FollowButton calls an actor-id route; the handle is
 *  unique only within a subtype. */
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

/** One row of a follower or following list: the identity, plus the CALLER's own
 *  edge to it, which is what the row's FollowButton renders and writes. Never
 *  the profile owner's edge (backend-contract.md §8 rule 21). It extends
 *  `ActorRefDto` rather than declaring a fresh shape, because a row draws an
 *  avatar, a name, a handle and a profile link and that is exactly what
 *  `ActorRefDto` carries; a second identity type is how two of them start
 *  disagreeing about whether `avatarUrl` may be absent. */
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

/** unfollows when `next` is false */
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

/** One function for both directions: the routes differ by their last path
 *  segment only. `following` answers 404 for an organization id, because only
 *  a user follows (backend-contract.md §7d). */
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
