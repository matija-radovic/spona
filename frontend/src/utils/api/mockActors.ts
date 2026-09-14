// -----------------------------------------------------------------------------
// MOCK actor fixtures. Not a service: no delay, no promise, no DTO. This is the
// one place a handle becomes an actor id, the one place the people and
// organizations the mocks share are declared, and the one place the signed-in
// viewer's memberships live.
//
// It exists because the id used to be crafted independently in six service
// mocks and two spellings disagreed: `moja-kompanija` was `org-moja` in
// profiles/posts/jobs/activity and `org-moja-kompanija` in memberships and
// organizations. Nothing noticed until a surface joined a `ProfileDto` to a
// membership row by id, at which point every organization read as "not mine".
//
// THE RULE: an actor id is `<actorType>-<handle>`, for every actor, everywhere,
// including the deterministic fallback a typed handle resolves to. Derive it
// with `orgIdFor`; never write the string. The one exception is `bez-oglasa`,
// whose id carries the `-empty` suffix `jobs.ts` reads to serve an actor with
// no postings, and it is declared here rather than at a call site.
//
// The real backend supplies UUIDs and computes every viewer flag off the JWT.
// -----------------------------------------------------------------------------

import type { ActorRefDto } from './profiles'
import type { OrganizationRole } from './organizations'

// ── The id rule ───────────────────────────────────────────────────────────────

const orgIdFor = (slug: string): string => `org-${slug}`

// ── People ────────────────────────────────────────────────────────────────────

export interface MockPerson {
  id: string
  username: string
  fullName: string
  bio: string | null
}

const person = (username: string, fullName: string, bio: string | null, id?: string): MockPerson => ({
  id: id ?? `user-${username}`,
  username,
  fullName,
  bio,
})

export const PEOPLE: readonly MockPerson[] = [
  person('my-account', 'Ana Jovanović', 'Frontend inženjerka. Gradim pristupačne interfejse i volim dobar dizajn sistema.'),
  person('marko', 'Marko Marković', 'Backend developer. Java, Spring, PostgreSQL.'),
  person('jelena', 'Jelena Petrović', 'Product designer.\nRadim na proizvodima za region.'),
  person('nikola', 'Nikola Ilić', 'Sistem inženjer.'),
  person('milica', 'Milica Kostić', 'Copywriter i urednica sadržaja.'),
  person('stefan', 'Stefan Nikolić', 'Mobile developer, Kotlin i Swift.'),
  person('ivana', 'Ivana Đorđević', 'IT regruter. Volim da spajam ljude i timove.'),
  person('luka', 'Luka Pavlović', 'Tehnički regruter.'),
  person('dusan', 'Dušan Simić', 'Urednik i prevodilac.'),
  person('tijana', 'Tijana Ristić', 'Regruterka za inženjerske pozicije.'),
  person('mila', 'Mila Jovanović', 'QA inženjerka.'),
  person('ana', 'Ana Petrović', 'Studentkinja informatike, tražim praksu.'),
  // The `-empty` suffix is what jobs.ts reads to serve an actor with no
  // postings at all: a manager whose profile therefore has no postings tab.
  person('bez-oglasa', 'Petar Petrović', 'Tek sam se registrovao.', 'user-bez-oglasa-empty'),
]

export const VIEWER = PEOPLE[0]

const findPerson = (username: string): MockPerson | undefined =>
  PEOPLE.find((row) => row.username === username)

export const personRef = (username: string): ActorRefDto => {
  const row = findPerson(username)
  return {
    actorType: 'user',
    id: row?.id ?? `user-${username}`,
    name: row?.fullName ?? username,
    handle: username,
    avatarUrl: null,
  }
}

// ── Organizations, and the viewer's role in each ──────────────────────────────

export interface MockOrg {
  id: string
  slug: string
  name: string
  legalName: string
  description: string
  website: string | null
  location: string
  initialViewerRole: OrganizationRole | null
}

const organization = (
  slug: string,
  name: string,
  legalName: string,
  description: string,
  website: string | null,
  location: string,
  initialViewerRole: OrganizationRole | null,
): MockOrg => ({
  id: orgIdFor(slug),
  slug,
  name,
  legalName,
  description,
  website,
  location,
  initialViewerRole,
})

/**
 * One organization per role, so every branch that reads a role has a handle to
 * reach it with, plus two the viewer does not belong to. Any handle outside this
 * list resolves to a non-member as well, so "not mine" needs no fixture of its
 * own.
 */
export const ORGANIZATIONS: readonly MockOrg[] = [
  organization(
    'moja-kompanija',
    'Moja Kompanija',
    'Moja Kompanija d.o.o.',
    'Softverska agencija. Radimo web i mobilne aplikacije po meri.',
    'https://moja-kompanija.rs',
    'Beograd',
    'OWNER',
  ),
  organization(
    'vega-it',
    'Vega IT',
    'Vega IT Sourcing d.o.o.',
    'IT kompanija sa timovima u Novom Sadu i Beogradu.',
    'https://vegaitsourcing.rs',
    'Novi Sad',
    'ADMIN',
  ),
  organization(
    'nordeus',
    'Nordeus',
    'Nordeus d.o.o. Beograd',
    'Gejming studio iz Beograda. Pravimo igre koje igraju milioni.',
    'https://nordeus.com',
    'Beograd',
    'EDITOR',
  ),
  organization(
    'htec-group',
    'HTEC Group',
    'HTEC d.o.o. Novi Sad',
    'Inženjerska kuća koja gradi proizvode za klijente iz Evrope i SAD.',
    'https://htecgroup.com',
    'Novi Sad',
    'RECRUITER',
  ),
  organization(
    'quantox',
    'Quantox',
    'Quantox Technology d.o.o.',
    'Softverska kompanija sa razvojnim centrima širom Srbije.',
    'https://quantox.com',
    'Čačak',
    'PUBLISHER',
  ),
  organization(
    'levi9',
    'Levi9',
    'Levi9 Global Sourcing d.o.o.',
    'Tehnološki servis sa timovima u Srbiji, Rumuniji i Ukrajini.',
    'https://levi9.com',
    'Novi Sad',
    null,
  ),
  organization(
    'zuhlke',
    'Zühlke',
    'Zühlke Engineering d.o.o.',
    'Konsultantska kuća za inženjering i inovacije.',
    'https://zuehlke.com',
    'Beograd',
    null,
  ),
]

const findOrg = (slug: string): MockOrg | undefined =>
  ORGANIZATIONS.find((row) => row.slug === slug)

export const findOrgById = (id: string): MockOrg | undefined =>
  ORGANIZATIONS.find((row) => row.id === id)

export const orgRef = (slug: string): ActorRefDto => {
  const row = findOrg(slug)
  return {
    actorType: 'org',
    id: orgIdFor(slug),
    name: row?.name ?? slug,
    handle: slug,
    avatarUrl: null,
  }
}

// ── The viewer's memberships ──────────────────────────────────────────────────
//
// Every mock asks this rather than keeping its own copy, so the profile head,
// the identity picker, the settings page and the postings pane agree.

const viewerRoles = new Map<string, OrganizationRole | null>(
  ORGANIZATIONS.map((row) => [row.slug, row.initialViewerRole]),
)

export const viewerRoleIn = (slug: string): OrganizationRole | null =>
  viewerRoles.get(slug) ?? null

// ── The flags the server computes, computed here because this IS the server ───
//
// These stand in for a backend decision and belong to the mock alone. No page
// may derive them: `viewerCanManageJobPostings` gates request parameters the
// server rejects, which is exactly why it travels on the DTO rather than being
// worked out in the browser (design-choices.md, §Acting Identity).

/** Managing an organization's postings and their applications is everyone but an EDITOR. */
export const orgViewerCanManagePostings = (role: OrganizationRole | null): boolean =>
  role !== null && role !== 'EDITOR'
