// The fixed reaction palette. The picker renders these 5-per-row x 4 rows
// (profile-page.r1 §7.3). Order is meaningful only for the first two: index 0 is
// 👍 and index 1 is ❤️; the rest are distinct, sensible reactions.

export const REACTION_EMOJIS: readonly string[] = [
  '👍', '❤️', '😂', '🎉', '😮',
  '😢', '🙏', '👏', '🔥', '💡',
  '😍', '🤔', '😎', '🚀', '💯',
  '🙌', '😅', '🤝', '👀', '✅',
] as const
