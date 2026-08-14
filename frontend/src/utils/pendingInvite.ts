// A guest clicking an emailed `/i/{token}` link must authenticate first, so the raw
// token is parked in sessionStorage for exactly that round trip and consumed once.

const KEY ='spona:pending-invite'

export interface PendingInvite {
  token: string
  orgName: string
}

const isPendingInvite = (value: unknown): value is PendingInvite => {
  if (typeof value !== 'object' || value === null) return false
  const invite = value as Record<string, unknown>
  return typeof invite.token === 'string' && typeof invite.orgName === 'string'
}

export const stashPendingInvite = (invite: PendingInvite): void => {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(invite))
  } catch {
    /* storage unavailable - the invitee can still open the link again */
  }
}

export const peekPendingInvite =(): PendingInvite | null => {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    return isPendingInvite(parsed) ? parsed : null
  } catch {
    return null // malformed JSON or storage unavailable -> treat as absent
  }
}

/** The only way the token leaves storage. */
export const consumePendingInvite =(): PendingInvite | null => {
  const invite = peekPendingInvite()
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    /* storage unavailable - nothing to clear */
  }
  return invite
}

export const invitePathAfterAuth = (username: string): string | null =>
  peekPendingInvite() ? `/users/${username}/settings/organizations` : null
