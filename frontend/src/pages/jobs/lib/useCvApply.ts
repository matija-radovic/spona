import { useRef, useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { CV_ACCEPTED_EXT, CV_ACCEPTED_TYPES, CV_MAX_BYTES } from '@utils/constants/validation'
import { submitApplication } from '@utils/api/jobs'
import { parseProblem } from '@utils/api/types'

// NOTE: this object intentionally holds NO ref — the file-input ref stays local
// to CvDropApply. A returned ref makes the react-hooks/refs lint treat every
// `cv.*` read during render as a ref access.
export interface CvApply {
  file: File | null
  /** client-validation reason, else the last server failure, else null */
  errorText: string | null
  showValid: boolean
  applyDisabled: boolean
  submitting: boolean
  success: boolean
  dragActive: boolean
  tooltipOpen: boolean
  acceptTypes: string
  /** hand a picked/dropped file to the flow (validates immediately) */
  onFile: (file: File) => void
  dismissTooltip: () => void
  handleSubmit: () => void
  /** spread onto the element that should act as the drop target */
  dropHandlers: {
    onDragEnter: (event: DragEvent) => void
    onDragOver: (event: DragEvent) => void
    onDragLeave: (event: DragEvent) => void
    onDrop: (event: DragEvent) => void
  }
}

/**
 * CV drag-drop + validate + submit state, decoupled from where the drop target
 * lives. JobDetail spreads `dropHandlers` on the whole panel (so the entire job
 * area accepts a drop) while `CvDropApply` renders the controls in the apply row.
 */
export const useCvApply = (jobId: string): CvApply => {
  const { t } = useTranslation()
  const dragCounter = useRef(0)

  const [file, setFile] = useState<File | null>(null)
  // clientError disables Apply; serverError does not (retry must stay possible).
  const [clientError, setClientError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [tooltipOpen, setTooltipOpen] = useState(false)
  const [dragActive, setDragActive] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  // Frontend validation — mirrored exactly in handleSubmit before firing.
  const validateCv = (candidate: File): string | null => {
    const nameOk = CV_ACCEPTED_EXT.some((ext) => candidate.name.toLowerCase().endsWith(ext))
    const typeOk = CV_ACCEPTED_TYPES.includes(candidate.type)
    if (!typeOk && !nameOk) return t('errors.invalidFileType')
    if (candidate.size > CV_MAX_BYTES) return t('errors.fileTooLarge')
    return null
  }

  const acceptFile = (candidate: File) => {
    setFile(candidate)
    setServerError(null)
    const error = validateCv(candidate)
    setClientError(error)
    // The error tooltip opens automatically (unlike TextField); a real hover-out
    // or an ✕ click then dismisses it.
    setTooltipOpen(error !== null)
  }

  const handleSubmit = async () => {
    if (!file || submitting) return
    const error = validateCv(file)
    if (error) {
      setClientError(error)
      setTooltipOpen(true)
      return
    }
    setSubmitting(true)
    setServerError(null)
    try {
      await submitApplication(jobId, file)
      setSuccess(true)
    } catch (err) {
      setServerError(parseProblem(err).detail || t('errors.serverError'))
      setTooltipOpen(true)
    } finally {
      setSubmitting(false)
    }
  }

  const errorText = clientError ?? serverError

  return {
    file,
    errorText,
    showValid: file !== null && errorText === null,
    applyDisabled: file === null || clientError !== null || submitting,
    submitting,
    success,
    dragActive,
    tooltipOpen,
    acceptTypes: CV_ACCEPTED_TYPES.join(','),
    onFile: acceptFile,
    dismissTooltip: () => setTooltipOpen(false),
    handleSubmit,
    dropHandlers: {
      onDragEnter: (event) => {
        event.preventDefault()
        dragCounter.current += 1
        setDragActive(true)
      },
      onDragOver: (event) => event.preventDefault(),
      onDragLeave: (event) => {
        event.preventDefault()
        dragCounter.current -= 1
        if (dragCounter.current <= 0) setDragActive(false)
      },
      onDrop: (event) => {
        event.preventDefault()
        dragCounter.current = 0
        setDragActive(false)
        const dropped = event.dataTransfer.files?.[0]
        if (dropped) acceptFile(dropped)
      },
    },
  }
}
