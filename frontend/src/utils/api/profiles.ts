// -----------------------------------------------------------------------------
// MOCK profiles service. The Spring Boot backend does not exist yet, so every
// call fakes a delayed response instead of hitting the network. The real axios
// call is left commented above each mock body. DTO shapes are separate from the
// DB model (types-ref.r3): user-facing DTOs OMIT `createdBy`, ship a stripped
// discriminated actor reference, and NEVER carry the private email or any
// privacy flag (job-posting visibility is enforced entirely backend-side, §9).
//
// All rejections are `ProblemDetail` shapes so callers can `parseProblem(err)`
// uniformly. Magic inputs make the failure/empty paths reachable:
//   - fetchProfile : handle === "nepostojeci" -> 404 Not Found
//   - searchActors : q === "prazno"           -> empty page (0 results)
//   - setFollow    : empty actorId            -> 400 Bad Request
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import type { ContactEntry } from '@shared/components'
import type { PagedResponse, ProblemDetail } from './types'

// ── DTOs ────────────────────────────────────────────────────────────────────

/** Stripped actor reference used everywhere an author/identity renders. */
export interface ActorRefDto {
  actorType: 'user' | 'org'
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
      following: number
      createdAt: string
      isFollowedByViewer?: boolean
      viewerCanEdit?: boolean
    }

/** Actor search-result summary. Stripped down from `ProfileDto` to the fields
 *  a search row can plausibly need (no id/location/contact/createdAt/
 *  viewerCanEdit) - but, unlike `ActorRefDto`, still a real per-type summary
 *  (bio/description + follower counts), not just an identity reference. */
export type ActorSearchItemDto =
  | {
      actorType: 'user'
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
      name: string
      handle: string
      avatarUrl: string | null
      description: string | null
      followers: number
      following: number
      isFollowedByViewer?: boolean
    }

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
// Keyed by handle. `ja` and `moja-kompanija` are the editable "own" cases - log
// in with one of those usernames to preview your own profile (the real backend
// derives viewerCanEdit from the auth token; the mock keys it off the handle).

const USER_PROFILES: Record<string, ProfileDto> = {
  "user-ja": {
    actorType: 'user',
    id: 'user-ja',
    username: 'user-ja',
    fullName: 'Ana Jovanović',
    bio: 'Frontend inženjerka. Gradim pristupačne interfejse i volim dobar dizajn sistema.',
    avatarUrl: null,
    location: 'Beograd',
    otherContactInfo: CONTACTS_FULL.slice(0, 2),
    followers: 128,
    following: 87,
    createdAt: iso(420),
    viewerCanEdit: true,
  },
  marko: {
    actorType: 'user',
    id: 'user-marko',
    username: 'marko',
    fullName: 'Marko Marković',
    bio: 'Backend developer. Java, Spring, PostgreSQL.',
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
    id: 'user-jelena',
    username: 'jelena',
    fullName: 'Jelena Petrović',
    bio: 'Product designer.\nRadim na proizvodima za region.',
    avatarUrl: null,
    location: 'Niš',
    otherContactInfo: CONTACTS_FULL, // every type, incl. the non-linking ones
    followers: 512,
    following: 210,
    createdAt: iso(700),
    isFollowedByViewer: true,
  },
}

const ORG_PROFILES: Record<string, ProfileDto> = {
  nordeus: {
    actorType: 'org',
    id: 'org-nordeus',
    slug: 'nordeus',
    displayName: 'Nordeus',
    description: 'Gejming studio iz Beograda. Pravimo igre koje igraju milioni.',
    website: 'https://nordeus.com',
    avatarUrl: null,
    location: 'Beograd',
    otherContactInfo: CONTACTS_FULL.slice(0, 3),
    followers: 3400,
    following: 12,
    createdAt: iso(1500),
    isFollowedByViewer: false,
  },
  'moja-kompanija': {
    actorType: 'org',
    id: 'org-moja',
    slug: 'moja-kompanija',
    displayName: 'Moja Kompanija',
    description: 'Softverska agencija. Radimo web i mobilne aplikacije po meri.',
    website: 'https://moja-kompanija.rs',
    avatarUrl: null,
    location: 'Beograd',
    otherContactInfo: CONTACTS_FULL.slice(0, 1),
    followers: 96,
    following: 20,
    createdAt: iso(300),
    viewerCanEdit: true,
  },
  'vega-it': {
    actorType: 'org',
    id: 'org-vega',
    slug: 'vega-it',
    displayName: 'Vega IT',
    description: 'IT kompanija sa timovima u Novom Sadu i Beogradu.',
    website: null,
    avatarUrl: null,
    location: 'Novi Sad',
    otherContactInfo: [], // Contact tab hidden
    followers: 1800,
    following: 40,
    createdAt: iso(2000),
    isFollowedByViewer: true,
  },
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

/** Deterministic fallback so any typed handle still renders a believable page. */
const fallbackProfile = (actorType: 'user' | 'org', handle: string): ProfileDto => {
  const seed = hashString(handle)
  const locations = ['Beograd', 'Novi Sad', 'Niš', null, 'Kragujevac'] as const
  const shared = {
    id: `${actorType}-${seed}`,
    avatarUrl: null,
    location: locations[seed % locations.length],
    otherContactInfo: seed % 2 === 0 ? CONTACTS_FULL.slice(0, 2) : [],
    followers: 20 + (seed % 900),
    following: 5 + (seed % 300),
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
    ...shared,
  }
}

/** Resolves the profile a handle maps to: a named mock, else the deterministic
 *  fallback. Shared by fetchProfile and the search projection so a result row
 *  and the profile page it links to draw from one source. */
const resolveProfile = (actorType: 'user' | 'org', handle: string): ProfileDto =>
  (actorType === 'org' ? ORG_PROFILES[handle] : USER_PROFILES[handle]) ??
  fallbackProfile(actorType, handle)

/**
 * MOCK - would be: const { data } = await api.get(`/${actorType}s/${handle}`)
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
      name,
      handle,
      avatarUrl: profile.avatarUrl,
      followers: profile.followers,
      following: profile.following,
      isFollowedByViewer: profile.isFollowedByViewer,
      description: profile.description,
    }
  }
  return {
    actorType: 'user',
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
