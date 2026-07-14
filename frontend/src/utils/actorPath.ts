// Builds the public profile URL for an actor. Cross-page (post cards, search
// results, header dropdown), so it lives in utils rather than any one page.
// Users live under /users/:username, organizations under /companies/:slug -
// the `handle` carries whichever identifier the URL segment needs.

export const actorPath = (a: { actorType: 'user' | 'org'; handle: string }): string =>
  a.actorType === 'org' ? `/companies/${a.handle}` : `/users/${a.handle}`
