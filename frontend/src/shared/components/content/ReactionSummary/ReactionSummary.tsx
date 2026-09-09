import './ReactionSummary.scss'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ReactionBreakdownModal } from '@shared/components/overlay/ReactionBreakdownModal'
import { formatCompactCount } from '@utils/format'
import type { ReactionSummaryDto, ReactionTargetType } from '@utils/api/reactions'

interface ReactionSummaryProps {
  summary: ReactionSummaryDto
  targetType: ReactionTargetType
  targetId: string
}

const ReactionSummary = ({ summary, targetType, targetId }: ReactionSummaryProps) => {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  if (summary.total === 0) return null

  return (
    <>
      <button
        type="button"
        className="reaction-summary"
        onClick={(e) => {
          e.stopPropagation()
          setOpen(true)
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
          <span className="reaction-summary-more">+{formatCompactCount(summary.othersCount)}</span>
        )}
      </button>

      <ReactionBreakdownModal
        isOpen={open}
        onClose={() => setOpen(false)}
        targetType={targetType}
        targetId={targetId}
      />
    </>
  )
}

export default ReactionSummary
