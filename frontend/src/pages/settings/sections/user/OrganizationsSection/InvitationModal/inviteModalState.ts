import type { InvitationPreviewDto } from '@utils/api/memberships'

/** Every value but `waiting` doubles as its `settings.invite.<state>` message key. */
export const InviteModalState =Object.freeze({
  WAITING: 'waiting',
  WRONG_ACCOUNT: 'wrongAccount',
  CANCELED: 'canceled',
  ALREADY_ACCEPTED: 'alreadyAccepted',
  DECLINED: 'declined',
  NOT_FOUND: 'notFound',
} as const)

export type InviteModalState = (typeof InviteModalState)[keyof typeof InviteModalState]

export const stateFromPreview =(preview: InvitationPreviewDto): InviteModalState => {
  if (preview.targetsCurrentUser === false) return InviteModalState.WRONG_ACCOUNT
  if (preview.status === 'CANCELED') return InviteModalState.CANCELED
  if (preview.status === 'ACCEPTED') return InviteModalState.ALREADY_ACCEPTED
  if (preview.status === 'DECLINED') return InviteModalState.DECLINED
  return InviteModalState.WAITING
}
