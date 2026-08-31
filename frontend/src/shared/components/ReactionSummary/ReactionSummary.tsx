import './ReactionSummary.scss'
import { useTranslation } from 'react-i18next'
import type { ReactionSummaryDto } from '@utils/api/reactions'

interface ReactionSummaryProps {
  summary: ReactionSummaryDto
  onMore: () => void
}

const ReactionSummary = ({ summary, onMore }: ReactionSummaryProps) => {
  const { t } = useTranslation()

  if (summary.total === 0) return null

  return (
    <button
      type="button"
      className="reaction-summary"
      onClick={(e) => {
        e.stopPropagation()
        onMore()
      }}
      aria-label={t('post.allReactions', { count: summary.total })}
    >
      {summary.top.map((entry) => (
        <span key={entry.emoji} className="reaction-summary-item">
          <span className="reaction-summary-emoji" aria-hidden>{entry.emoji}</span>
          <span className="reaction-summary-count">{entry.count}</span>
        </span>
      ))}

      {summary.othersCount > 0 && (
        <span className="reaction-summary-more">+{summary.othersCount}</span>
      )}
    </button>
  )
}

export default ReactionSummary
