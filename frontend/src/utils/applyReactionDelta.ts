import { REACTION_TOP_MAX, type ReactionSummaryDto } from '@utils/api/reactions'

// Optimistic recomputation of a reaction summary
export const applyReactionDelta = (
  summary: ReactionSummaryDto,
  emoji: string,
  next: boolean,
): ReactionSummaryDto => {
  const viewer = new Set(summary.viewerReactions ?? [])
  if (next) viewer.add(emoji)
  else viewer.delete(emoji)

  const total = Math.max(0, summary.total + (next ? 1 : -1))
  const topIndex = summary.top.findIndex((entry) => entry.emoji === emoji)
  let top = summary.top.map((entry) => ({ ...entry }))
  let othersCount = summary.othersCount

  const hasRoom = top.length < REACTION_TOP_MAX && othersCount === 0

  if (topIndex >= 0) {
    top[topIndex] = { ...top[topIndex], count: top[topIndex].count + (next ? 1 : -1) }
    top = top.filter((entry) => entry.count > 0)
  } else if (next && hasRoom) {
    top = [...top, { emoji, count: 1 }]
  } else {
    othersCount = Math.max(0, othersCount + (next ? 1 : -1))
  }

  return { top, othersCount, total, viewerReactions: [...viewer] }
}
