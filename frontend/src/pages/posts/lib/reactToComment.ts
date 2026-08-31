import { applyReactionDelta } from '@utils/applyReactionDelta'
import { setReaction, type ReactionSummaryDto } from '@utils/api/reactions'
import type { ThreadCommentDto } from '@utils/api/posts'

// Optimistic reaction toggle for one comment
export const reactToComment = (
  comment: ThreadCommentDto,
  emoji: string,
  next: boolean,
  write: (reactions: ReactionSummaryDto) => void,
): void => {
  write(applyReactionDelta(comment.reactions, emoji, next))
  setReaction('comment', comment.id, emoji, next).catch(() => write(comment.reactions))
}
