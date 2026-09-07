import './CvDropApply.scss'
import { useRef, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconCheck, IconX, IconUpload, IconFileText } from '@tabler/icons-react'
import { Button, ButtonVariant, ButtonSize } from '@shared/components/form/Button'
import { Tooltip } from '@shared/components/overlay/Tooltip'
import type { CvApply } from '@pages/jobs/lib/useCvApply'

interface CvDropApplyProps {
  cv: CvApply
}

/**
 * Presentational apply controls - one row: [choose file] [selected file + validity]
 * [submit]. State + the drop target live in `useCvApply` (the drop target is the
 * whole detail panel, wired up by JobDetail).
 */
const CvDropApply = ({ cv }: CvDropApplyProps) => {
  const { t } = useTranslation()
  const inputRef = useRef<HTMLInputElement>(null)

  const onPick = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = event.target.files?.[0]
    event.target.value = '' // allow re-picking the same file
    if (picked) cv.onFile(picked)
  }

  const onChoose = () => {
    if (cv.requireAuth()) return
    inputRef.current?.click()
  }

  if (cv.success) {
    return (
      <div className="cv-apply cv-apply-success">
        <IconCheck aria-hidden />
        <span>{t('jobs.apply.success')}</span>
      </div>
    )
  }

  return (
    <div className="cv-apply">
      <input
        ref={inputRef}
        type="file"
        accept={cv.acceptTypes}
        className="cv-apply-input"
        onChange={onPick}
      />

      <Button
        variant={ButtonVariant.SECONDARY}
        size={ButtonSize.SMALL}
        onClick={onChoose}
      >
        <IconUpload aria-hidden />
        {t('jobs.apply.chooseFile')}
      </Button>

      {cv.file && (
        <span className="cv-apply-file">
          <IconFileText aria-hidden />
          <span className="cv-apply-file-name">{cv.file.name}</span>

          {cv.errorText ? (
            <Tooltip
              label={cv.errorText}
              open={cv.tooltipOpen}
              onOpenChange={cv.setTooltipOpen}
              className="cv-apply-indicator-wrap is-invalid"
            >
              <span className="cv-apply-indicator" role="img" aria-label={cv.errorText}>
                <IconX aria-hidden />
              </span>
            </Tooltip>
          ) : (
            <span className="cv-apply-indicator-wrap is-valid">
              <span
                className="cv-apply-indicator"
                role="img"
                aria-label={t('jobs.apply.selected')}
              >
                <IconCheck aria-hidden />
              </span>
            </span>
          )}
        </span>
      )}

      <Button
        variant={ButtonVariant.PRIMARY}
        size={ButtonSize.SMALL}
        onClick={cv.handleSubmit}
        disabled={cv.applyDisabled}
      >
        {cv.submitting ? t('common.loading') : t('jobs.apply.submit')}
      </Button>
    </div>
  )
}

export default CvDropApply
