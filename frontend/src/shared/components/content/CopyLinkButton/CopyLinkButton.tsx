import './CopyLinkButton.scss'
import { useTranslation } from 'react-i18next'
import { IconLink, IconCheck } from '@tabler/icons-react'
import useCopyLink from '@shared/hooks/useCopyLink'

interface CopyLinkButtonProps {
  /** Absolute URL to copy, e.g. `window.location.origin + path`. */
  url: string
  /** Visible label; when omitted the button is icon-only. */
  label?: string
  className?: string
}

const CopyLinkButton = ({ url, label, className }: CopyLinkButtonProps) => {
  const { t } = useTranslation()
  const { copied, copy } = useCopyLink(url)

  const text = copied ? t('common.linkCopied') : (label ?? t('common.copyLink'))
  const rootClass = ['copy-link', copied ? 'is-copied' : '', className ?? ''].filter(Boolean).join(' ')

  return (
    <button type="button" className={rootClass} onClick={copy} aria-label={text}>
      {copied ? <IconCheck aria-hidden /> : <IconLink aria-hidden />}
      {label && <span className="copy-link-label">{text}</span>}
    </button>
  )
}

export default CopyLinkButton
