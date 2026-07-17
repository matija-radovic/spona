import './ReactionSummary.scss'
import { useTranslation } from 'react-i18next'
import type { ReactionSummaryDto } from '@utils/api/activity'

interface ReactionSummaryProps {
  summary: ReactionSummaryDto
  /** Navigate to the full breakdown (the post page) - the "+N" target. */
  onMore: () => void
}

/**
 * Read-only summary shown on the right of a post/comment action row: up to three
 * top emojis with their counts, then a `+N` button (reactions outside the top 3)
 * that jumps to the post. Renders nothing when there are no reactions.
 */
const ReactionSummary = ({ summary, onMore }: ReactionSummaryProps) => {
  const { t } = useTranslation()

  if (summary.total === 0) return null

  return (
    <div className="reaction-summary">
      {summary.top.map((entry) => (
        <span key={entry.emoji} className="reaction-summary-item">
          <span className="reaction-summary-emoji" aria-hidden>{entry.emoji}</span>
          <span className="reaction-summary-count">{entry.count}</span>
        </span>
      ))}

      {summary.othersCount > 0 && (
        <button
          type="button"
          className="reaction-summary-more"
          onClick={(e) => {
            e.stopPropagation()
            onMore()
          }}
          aria-label={t('post.moreReactions', { count: summary.othersCount })}
        >
          +{summary.othersCount}
        </button>
      )}
    </div>
  )
}

export default ReactionSummary
