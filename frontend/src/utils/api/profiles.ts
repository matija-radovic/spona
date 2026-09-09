// -----------------------------------------------------------------------------
// MOCK profiles service. The Spring Boot backend does not exist yet, so every
// call fakes a delayed response instead of hitting the network. The real axios
// call is left commented above each mock body. DTO shapes are separate from the
// DB model (new_docx/types.ts): user-facing DTOs OMIT `createdBy`, ship a
// stripped discriminated actor reference, and NEVER carry the private email or
// any privacy flag (job-posting visibility is enforced entirely backend-side,
// backend-contract.md §8 rule 8).
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`
// uniformly. Magic inputs make the failure/empty paths reachable:
//   - fetchProfile    : handle === "nepostojeci" -> 404 Not Found
//   - fetchProfile    : handle === "bez-oglasa"  -> own profile, no job postings
//   - searchActors    : q === "prazno"           -> empty page (0 results)
//   - fetchFollowList : q === "prazno"           -> empty page (0 results)
//   - fetchFollowList : q === "greska"           -> 500 Internal Server Error
//   - setFollow       : empty actorId            -> 400 Bad Request
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PagedResponse, ProblemDetail } from './types'
import {
  ORGANIZATIONS,
  PEOPLE,
  findOrgById,
  findPerson,
  findPersonById,
  orgIdFor,
  orgRef,
  orgViewerCanEdit,
  orgViewerCanManagePostings,
  personRef,
  userIdFor,
  viewerRoleIn,
} from './mockActors'

// ── DTOs ────────────────────────────────────────────────────────────────────

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
       *  from `viewerCanEdit` because the two diverge in opposite directions: a
       *  RECRUITER manages an organization's postings and not its profile, an
       *  EDITOR the reverse. */
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

// ── Mock plumbing ────────────────────────────────────────────────────────────

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const makeProblem = (status: number, detail: string, title = ''): ProblemDetail => ({
  type: 'about:blank',
  title,
  status,
  detail,
  instance: '/api/actors',
})

const rejectProblem = <T>(status: number, detail: string, title?: string): Promise<T> =>
  Promise.reject(makeProblem(status, detail, title))

const MAGIC_NOT_FOUND_HANDLE = 'nepostojeci'
const MAGIC_EMPTY_QUERY = 'prazno'
/** `searchActors` has no error magic, so a list's error state and its load-more
 *  retry are otherwise unreachable by hand. */
const MAGIC_ERROR_QUERY = 'greska'
const MAX_MOCK_PAGES = 3

/** Cheap stable hash so a given handle always renders the same profile shape. */
const hashString = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i += 1) hash = (hash * 31 + value.charCodeAt(i)) >>> 0
  return hash
}

const iso = (daysAgo: number): string =>
  new Date(Date.now() - daysAgo * 24 * 60 * 60 * 1000).toISOString()

// Covers every rendering branch of the Contact pane: a mailto, a tel, three
// link types (one schemeless, one with `www.`), a fax that never links, and an
// `other` whose value is deliberately not a URL.
const CONTACTS_FULL: ContactEntry[] = [
  { type: 'email', description: 'Email za saradnju', value: 'kontakt@primer.rs' },
  { type: 'phone', description: 'Telefon', value: '+381 64 123 4567' },
  { type: 'linkedin', description: 'Poslovni profil', value: 'linkedin.com/in/primer' },
  { type: 'instagram', description: 'Instagram', value: 'https://www.instagram.com/primer' },
  { type: 'website', description: 'Portfolio', value: 'https://primer.rs' },
  { type: 'fax', description: 'Faks', value: '+381 11 765 4321' },
  { type: 'other', description: 'Kancelarija', value: 'Bulevar kralja Aleksandra 73' },
]

// ── Named mock profiles (specific shapes the panes/tabs need to exercise) ──────
// Keyed by handle. Identity (id, handle, display name, bio) comes from
// `mockActors.ts` so a profile and a membership row cannot disagree about which
// actor a handle is. `my-account` is the editable "own" user; the organizations
// below are generated from the same fixture, and their two authority flags are
// computed from the viewer's role there rather than hand-set per entry - the
// real backend derives both from the auth token.

const USER_PROFILES: Record<string, ProfileDto> = {
  'my-account': {
    actorType: 'user',
    id: userIdFor('my-account'),
    username: 'my-account',
    fullName: 'Ana Jovanović',
    bio: findPerson('my-account')?.bio ?? null,
    avatarUrl: null,
    location: 'Beograd',
    otherContactInfo: CONTACTS_FULL.slice(0, 2),
    followers: 128,
    following: 87,
    createdAt: iso(420),
    viewerCanEdit: true,
    viewerCanManageJobPostings: true,
  },
  'bez-oglasa': {
    actorType: 'user',
    // The `-empty` suffix is what jobs.ts reads to serve no postings at all: a
    // manager with nothing published, whose profile therefore has no postings tab.
    id: userIdFor('bez-oglasa'),
    username: 'bez-oglasa',
    fullName: 'Petar Petrović',
    bio: findPerson('bez-oglasa')?.bio ?? null,
    avatarUrl: null,
    location: null,
    otherContactInfo: [],
    followers: 3,
    following: 11,
    createdAt: iso(4),
    viewerCanEdit: true,
    viewerCanManageJobPostings: true,
  },
  marko: {
    actorType: 'user',
    id: userIdFor('marko'),
    username: 'marko',
    fullName: 'Marko Marković',
    bio: findPerson('marko')?.bio ?? null,
    avatarUrl: null,
    location: 'Novi Sad',
    otherContactInfo: [], // Contact tab hidden
    followers: 42,
    following: 30,
    createdAt: iso(180),
    isFollowedByViewer: false,
  },
  jelena: {
    actorType: 'user',
    id: userIdFor('jelena'),
    username: 'jelena',
    fullName: 'Jelena Petrović',
    bio: findPerson('jelena')?.bio ?? null,
    avatarUrl: null,
    location: 'Niš',
    otherContactInfo: CONTACTS_FULL, // every type, incl. the non-linking ones
    followers: 512,
    following: 210,
    createdAt: iso(700),
    isFollowedByViewer: true,
  },
}

/** Contact shapes cycle so the tab is present on some organizations and absent
 *  on others, which is the branch the Contact pane needs both sides of. */
const ORG_CONTACT_SHAPES: readonly ContactEntry[][] = [
  CONTACTS_FULL.slice(0, 3),
  [],
  CONTACTS_FULL.slice(0, 1),
]

/**
 * One profile per fixture organization, so every role the viewer can hold has a
 * page to hold it on. Both authority flags are computed from that role: an OWNER
 * and an ADMIN may edit the profile, everyone but an EDITOR may manage the
 * postings, and a non-member gets neither. Hand-setting them per entry is what
 * let the profile and the membership list contradict each other.
 *
 * Built per request rather than once, because the role is session state:
 * accepting an invitation has to change what this page says about the viewer.
 */
const orgProfile = (slug: string): ProfileDto | undefined => {
  const index = ORGANIZATIONS.findIndex((row) => row.slug === slug)
  if (index < 0) return undefined

  const row = ORGANIZATIONS[index]
  const role = viewerRoleIn(slug)
  return {
    actorType: 'org',
    id: row.id,
    slug: row.slug,
    displayName: row.name,
    description: row.description,
    website: row.website,
    avatarUrl: null,
    location: row.location,
    otherContactInfo: ORG_CONTACT_SHAPES[index % ORG_CONTACT_SHAPES.length],
    followers: 96 + index * 470,
    createdAt: iso(300 + index * 240),
    isFollowedByViewer: role === null && index % 2 === 1,
    viewerCanEdit: orgViewerCanEdit(role),
    viewerCanManageJobPostings: orgViewerCanManagePostings(role),
  }
}

/** Fallback org description pool, mirroring the tone of the named ORG_PROFILES
 *  entries. Includes one long entry so search rows relying on the fallback
 *  (most of ORG_NAMES has no named mock) still exercise the "read more"
 *  truncation, and a null entry so the row correctly renders without one. */
const ORG_FALLBACK_DESCRIPTIONS: Array<string | null> = [
  'Organizacija na platformi Spona.',
  'Regionalna IT kompanija sa timovima raspoređenim po više gradova, koja radi na projektima za klijente iz Evrope i Severne Amerike, od enterprise softvera do sopstvenih proizvoda.',
  null,
]

/** Deterministic fallback so any typed handle still renders a believable page.
 *  The id follows the same `<actorType>-<handle>` rule the named fixtures do,
 *  not a hash: a fallback whose id nothing else can compute is an actor no other
 *  mock can refer to. Nobody outside the fixture list is a member of anything,
 *  so both authority flags stay absent. */
const fallbackProfile = (actorType: 'user' | 'org', handle: string): ProfileDto => {
  const seed = hashString(handle)
  const locations = ['Beograd', 'Novi Sad', 'Niš', null, 'Kragujevac'] as const
  const shared = {
    id: actorType === 'org' ? orgIdFor(handle) : userIdFor(handle),
    avatarUrl: null,
    location: locations[seed % locations.length],
    otherContactInfo: seed % 2 === 0 ? CONTACTS_FULL.slice(0, 2) : [],
    followers: 20 + (seed % 900),
    createdAt: iso(30 + (seed % 900)),
    isFollowedByViewer: seed % 3 === 0,
  }
  if (actorType === 'org') {
    const name = handle.charAt(0).toUpperCase() + handle.slice(1).replace(/-/g, ' ')
    return {
      actorType: 'org',
      slug: handle,
      displayName: name,
      description: ORG_FALLBACK_DESCRIPTIONS[seed % ORG_FALLBACK_DESCRIPTIONS.length],
      website: seed % 3 === 0 ? `https://${handle}.rs` : null,
      ...shared,
    }
  }
  const name = handle.charAt(0).toUpperCase() + handle.slice(1)
  return {
    actorType: 'user',
    username: handle,
    fullName: name,
    bio: seed % 2 === 0 ? 'Korisnik platforme Spona.' : null,
    following: 5 + (seed % 300),
    ...shared,
  }
}

/** Resolves the profile a handle maps to: a named mock, else the deterministic
 *  fallback. Shared by fetchProfile and the search projection so a result row
 *  and the profile page it links to draw from one source. */
const resolveProfile = (actorType: 'user' | 'org', handle: string): ProfileDto =>
  (actorType === 'org' ? orgProfile(handle) : USER_PROFILES[handle]) ??
  fallbackProfile(actorType, handle)

/**
 * MOCK - would be: const { data } = await api.get(
 *   actorType === 'org' ? `/organizations/${handle}` : `/users/${handle}`)
 * The two collections are spelled out because the handle routes are TYPED:
 * `/users/{username}` and `/organizations/{slug}`, never `/orgs` or `/companies`.
 * Single-entry: resolves the bare DTO (no wrapper). Unknown magic handle -> 404.
 */
export const fetchProfile = async (
  actorType: 'user' | 'org',
  handle: string,
): Promise<ProfileDto> => {
  await delay(420)
  if (handle === MAGIC_NOT_FOUND_HANDLE) {
    return rejectProblem(404, 'Traženi profil ne postoji.', 'Not Found')
  }
  return resolveProfile(actorType, handle)
}

/** MOCK. Follows the actor (or unfollows when `next` is false):
 *   await api({ method: next ? 'put' : 'delete', url: `/actors/${actorId}/follow` }) */
export const setFollow = async (actorId: string, next: boolean): Promise<void> => {
  void next
  await delay(250)
  if (!actorId) return rejectProblem(400, 'Nedostaje identifikator naloga.', 'Bad Request')
}

// ── Actor search ──────────────────────────────────────────────────────────────

const ORG_NAMES = ['Nordeus', 'Vega IT', 'HTEC Group', 'Levi9', 'Zühlke', 'Endava', 'Quantox', 'Symphony']
const USER_NAMES = ['Ana Jovanović', 'Marko Marković', 'Jelena Petrović', 'Nikola Ilić', 'Milica Kostić', 'Stefan Nikolić', 'Ivana Đorđević', 'Luka Pavlović']

const slugify = (value: string): string =>
  value
    .toLowerCase()
    .replace(/[čć]/g, 'c')
    .replace(/[š]/g, 's')
    .replace(/[ž]/g, 'z')
    .replace(/[đ]/g, 'dj')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')

/**
 * Builds one search result. Identity (name / handle) is generated from the name
 * pools, while the essentials (description / bio, follower counts, avatar) are
 * projected from the very profile the row links to - resolveProfile is the same
 * source fetchProfile reads - so the list never invents data of its own.
 */
const buildSearchItem = (type: 'org' | 'user', index: number): ActorSearchItemDto => {
  const pool = type === 'org' ? ORG_NAMES : USER_NAMES
  const base = pool[index % pool.length]
  const name = `${base} ${Math.floor(index / pool.length) || ''}`.trim()
  const handle = type === 'org' ? slugify(name) : `${slugify(base)}${index}`

  const profile = resolveProfile(type, handle)
  if (profile.actorType === 'org') {
    return {
      actorType: 'org',
      id: profile.id,
      name,
      handle,
      avatarUrl: profile.avatarUrl,
      followers: profile.followers,
      isFollowedByViewer: profile.isFollowedByViewer,
      description: profile.description,
    }
  }
  return {
    actorType: 'user',
    id: profile.id,
    name,
    handle,
    avatarUrl: profile.avatarUrl,
    followers: profile.followers,
    following: profile.following,
    isFollowedByViewer: profile.isFollowedByViewer,
    bio: profile.bio,
  }
}

const hrefFor = (q: string, type: string, page: number, size: number): string => {
  const sp = new URLSearchParams()
  sp.set('page', String(page))
  sp.set('size', String(size))
  sp.set('type', type)
  if (q) sp.set('q', q)
  return `/api/actors?${sp.toString()}`
}

/**
 * MOCK - would be:
 *   const { data } = await api.get('/actors', { params: { q, type, page, size } })
 * Results are capped at MAX_MOCK_PAGES so infinite scroll terminates. The magic
 * query yields an empty page.
 */
export const searchActors = async (
  q: string,
  type: 'org' | 'user',
  page = 1,
  size = 20,
): Promise<PagedResponse<ActorSearchItemDto>> => {
  await delay(380)

  const empty = q.trim().toLowerCase() === MAGIC_EMPTY_QUERY
  const totalElements = empty ? 0 : 48
  const totalPages = totalElements === 0 ? 0 : Math.min(Math.ceil(totalElements / size), MAX_MOCK_PAGES)
  const last = page >= totalPages
  const count = totalElements === 0 || page > totalPages ? 0 : last ? totalElements - (page - 1) * size : size

  const content: ActorSearchItemDto[] = Array.from({ length: count }, (_, i) =>
    buildSearchItem(type, (page - 1) * size + i),
  )

  return {
    _links: {
      self: { href: hrefFor(q, type, page, size) },
      ...(page > 1 ? { prev: { href: hrefFor(q, type, page - 1, size) } } : {}),
      ...(last ? {} : { next: { href: hrefFor(q, type, page + 1, size) } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}

// ── Follower / following lists ────────────────────────────────────────────────

/** Exported the way `feed.ts` exports `FEED_PAGE_SIZE`, so the mock's page cap
 *  and the caller's request cannot disagree. */
export const FOLLOW_PAGE_SIZE = 20

/** The profile an actor id addresses. The id rule is `<actorType>-<handle>`
 *  (mockActors.ts), so the handle is recoverable, but the fixtures are consulted
 *  first because `bez-oglasa` carries a suffixed id that the rule cannot invert. */
const profileForActorId = (actorId: string): ProfileDto | null => {
  const org = findOrgById(actorId)
  if (org) return resolveProfile('org', org.slug)

  const person = findPersonById(actorId)
  if (person) return resolveProfile('user', person.username)

  const parsed = /^(user|org)-(.+)$/.exec(actorId)
  if (!parsed) return null
  return resolveProfile(parsed[1] === 'org' ? 'org' : 'user', parsed[2])
}

/**
 * The fixture pool a list draws from before it starts minting rows, so the top
 * of every list is the same people and organizations the rest of the mocks know
 * and every profile link lands on a named page.
 *
 * `id !== actorId` is `CHECK followerId <> followeeId`: nobody follows
 * themselves. That is also the whole rule behind the viewer's appearances -
 * VIEWER is the first entry in PEOPLE and therefore the first row of everybody
 * else's follower list, and is absent from their own.
 */
const followFixtures = (actorId: string, direction: FollowDirection): ActorRefDto[] => {
  const people = PEOPLE.filter((row) => row.id !== actorId).map((row) => personRef(row.username))
  // Only a user follows (types.ts, Follows), so an organization can never be a
  // follower. A following list interleaves them, which is the branch ActorRow
  // needs in order to draw an organization avatar and an organization path.
  if (direction === 'followers') return people

  const orgs = ORGANIZATIONS.filter((row) => row.id !== actorId).map((row) => orgRef(row.slug))
  const mixed: ActorRefDto[] = []
  let person = 0
  let org = 0
  while (person < people.length || org < orgs.length) {
    if ((mixed.length % 3 === 2 || person >= people.length) && org < orgs.length) {
      mixed.push(orgs[org])
      org += 1
    } else {
      mixed.push(people[person])
      person += 1
    }
  }
  return mixed
}

/** Past the fixture pool, a row is minted from the same name pools the search
 *  results use. The ordinal is offset by the pool length so a generated name
 *  always carries an index suffix and can never collide with a fixture handle. */
const mintedFollowRef = (actorType: 'user' | 'org', ordinal: number): ActorRefDto => {
  const pool = actorType === 'org' ? ORG_NAMES : USER_NAMES
  const index = ordinal + pool.length
  const base = pool[index % pool.length]
  const name = `${base} ${Math.floor(index / pool.length)}`
  const handle = actorType === 'org' ? slugify(name) : `${slugify(base)}${index}`
  return {
    actorType,
    id: actorType === 'org' ? orgIdFor(handle) : userIdFor(handle),
    name,
    handle,
    avatarUrl: null,
  }
}

/** The caller's edge, projected from the very profile the row links to, so the
 *  row and the page behind it cannot disagree about it. Deterministic and mixed
 *  rather than all one answer: the named fixtures carry both, and the fallback
 *  hashes the handle, so both button states are on screen on the first page. */
const followRow = (ref: ActorRefDto): FollowActorDto => ({
  ...ref,
  isFollowedByViewer: resolveProfile(ref.actorType, ref.handle).isFollowedByViewer,
})

const followCandidates = (
  actorId: string,
  direction: FollowDirection,
  total: number,
): FollowActorDto[] => {
  const fixtures = followFixtures(actorId, direction)
  return Array.from({ length: total }, (_, i) => {
    if (i < fixtures.length) return followRow(fixtures[i])
    // Minted rows keep the fixtures' every-third-row interleave.
    const actorType = direction === 'following' && i % 3 === 2 ? 'org' : 'user'
    return followRow(mintedFollowRef(actorType, i - fixtures.length))
  })
}

/** Case-folded over name and handle, the same two fields `GET /actors?q=`
 *  matches, so an actor found in search is found here. */
const matchesFollowQuery = (row: FollowActorDto, q: string): boolean =>
  !q || row.name.toLowerCase().includes(q) || row.handle.toLowerCase().includes(q)

const followHrefFor = (
  actorId: string,
  direction: FollowDirection,
  q: string,
  page: number,
  size: number,
): string => {
  const sp = new URLSearchParams()
  sp.set('page', String(page))
  sp.set('size', String(size))
  if (q) sp.set('q', q)
  return `/api/actors/${actorId}/${direction}?${sp.toString()}`
}

/**
 * MOCK - would be: const { data } = await api.get(
 *   `/actors/${actorId}/${direction}`, { params: { q, page, size } })
 *
 * ONE function with a direction parameter rather than two functions: the routes
 * differ by their last path segment and agree about everything else - the same
 * envelope, the same row, the same `q`, the same page semantics. Two mocks would
 * be two places for the row builder to drift.
 *
 * `following` answers 404 for an organization id, because only a user follows:
 * the list is structurally empty, and an empty 200 would invite a client to draw
 * a tab that should not be there (backend-contract.md §7d).
 *
 * `totalElements` for an empty `q` is the profile's own follower / following
 * count, so the number on the tab and the number the list reports come from one
 * place. The MAX_MOCK_PAGES clamp on top of it is the mock's depth and not a
 * contract: the real backend serves both from one COUNT(*).
 */
export const fetchFollowList = async (
  actorId: string,
  direction: FollowDirection,
  q: string,
  page = 1,
  size = FOLLOW_PAGE_SIZE,
): Promise<PagedResponse<FollowActorDto>> => {
  await delay(400)

  const query = q.trim().toLowerCase()
  if (query === MAGIC_ERROR_QUERY) {
    return rejectProblem(500, 'Greška servera. Pokušajte ponovo.', 'Internal Server Error')
  }

  const profile = profileForActorId(actorId)
  if (!profile) return rejectProblem(404, 'Traženi nalog ne postoji.', 'Not Found')

  let stated: number
  if (direction === 'following') {
    if (profile.actorType === 'org') {
      return rejectProblem(404, 'Organizacija ne prati naloge.', 'Not Found')
    }
    stated = profile.following
  } else {
    stated = profile.followers
  }

  const candidates = followCandidates(actorId, direction, Math.min(stated, MAX_MOCK_PAGES * size))
  const matched =
    query === MAGIC_EMPTY_QUERY ? [] : candidates.filter((row) => matchesFollowQuery(row, query))

  const totalElements = matched.length
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
  const last = page >= totalPages
  const content = matched.slice((page - 1) * size, page * size)

  return {
    _links: {
      self: { href: followHrefFor(actorId, direction, q, page, size) },
      ...(page > 1 ? { prev: { href: followHrefFor(actorId, direction, q, page - 1, size) } } : {}),
      ...(last ? {} : { next: { href: followHrefFor(actorId, direction, q, page + 1, size) } }),
    },
    content,
    page,
    size,
    totalElements,
    totalPages,
    last,
  }
}
