import './ContactInfoWidget.scss'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconChevronDown, IconX } from '@tabler/icons-react'
import { Button, ButtonVariant } from '../Button'
import {
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
} from '@utils/constants/validation'
import { CONTACT_TYPE_OPTIONS, contactTypeOption } from './icons'
import type { ContactEntry } from './contactEntry'

interface ContactInfoWidgetProps {
  label: string
  value: ContactEntry[]
  onChange: (next: ContactEntry[]) => void
}

const ContactInfoWidget = ({ label, value, onChange }: ContactInfoWidgetProps) => {
  const { t } = useTranslation()
  const [type, setType] = useState<string>(CONTACT_TYPE_OPTIONS[0].key)
  const [description, setDescription] = useState('')
  const [draftValue, setDraftValue] = useState('')
  // Errors only surface once the row has been touched or an Add was attempted,
  // so the builder starts clean instead of flagging an empty value on mount.
  const [touched, setTouched] = useState(false)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!dropdownOpen) return
    const onClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [dropdownOpen])

  // ── Live validity (read per keystroke, the same way the rest of the form does)
  const trimmedValue = draftValue.trim()
  const limitReached = value.length >= CONTACT_MAX_ENTRIES
  const descTooLong = description.length > CONTACT_DESC_MAX
  const valueTooLong = draftValue.length > CONTACT_VALUE_MAX
  const valueEmpty = trimmedValue === ''
  const valueError = touched && (valueEmpty || valueTooLong)
  const canAdd = !limitReached && !valueEmpty && !descTooLong && !valueTooLong

  const addEntry = () => {
    setTouched(true)
    if (!canAdd) return
    onChange([...value, { type, description: description.trim(), value: trimmedValue }])
    setDescription('')
    setDraftValue('')
    setTouched(false)
  }

  const removeEntry = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  const onValueKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      addEntry()
    }
  }

  const selected = contactTypeOption(type)
  const SelectedIcon = selected.icon

  return (
    <div className="contact-info">
      <span className="contact-info-label">{label}</span>

      <div className="contact-info-builder">
        {/* Row 1 - type capsule + description */}
        <div className="contact-info-row">
          <div className="contact-info-type" ref={dropdownRef}>
            <button
              type="button"
              className="contact-info-capsule"
              onClick={() => setDropdownOpen((open) => !open)}
              aria-haspopup="listbox"
              aria-expanded={dropdownOpen}
              aria-label={t('auth.contact.selectType')}
            >
              <SelectedIcon />
              <IconChevronDown />
            </button>

            {dropdownOpen && (
              <ul className="contact-info-dropdown" role="listbox">
                {CONTACT_TYPE_OPTIONS.map((option) => {
                  const OptionIcon = option.icon
                  return (
                    <li key={option.key}>
                      <button
                        type="button"
                        role="option"
                        aria-selected={option.key === type}
                        className={`contact-info-option${option.key === type ? ' is-selected' : ''}`}
                        onClick={() => {
                          setType(option.key)
                          setDropdownOpen(false)
                        }}
                      >
                        <OptionIcon />
                        <span>{t(option.labelKey)}</span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>

          <input
            className={`contact-info-input${descTooLong ? ' has-error' : ''}`}
            type="text"
            value={description}
            maxLength={CONTACT_DESC_MAX}
            onChange={(event) => setDescription(event.target.value)}
            placeholder={t(selected.hintKey)}
            aria-label={t('auth.contact.descriptionPlaceholder')}
          />
        </div>

        {/* Row 2 - value + add (kept on its own row so the mobile keyboard
            doesn't cover the whole builder at once) */}
        <div className="contact-info-row">
          <input
            className={`contact-info-input${valueError ? ' has-error' : ''}`}
            type="text"
            value={draftValue}
            maxLength={CONTACT_VALUE_MAX}
            onChange={(event) => {
              setDraftValue(event.target.value)
              setTouched(true)
            }}
            onKeyDown={onValueKeyDown}
            placeholder={t('auth.contact.valuePlaceholder')}
            enterKeyHint="done"
            aria-label={t('auth.contact.valuePlaceholder')}
          />
          <Button
            variant={ButtonVariant.PRIMARY}
            className="contact-info-add"
            onClick={addEntry}
            disabled={limitReached}
          >
            {t('auth.contact.add')}
          </Button>
        </div>

        {descTooLong && (
          <p className="contact-info-error">{t('errors.maxLength', { max: CONTACT_DESC_MAX })}</p>
        )}
        {valueError && (
          <p className="contact-info-error">
            {valueTooLong
              ? t('errors.maxLength', { max: CONTACT_VALUE_MAX })
              : t('errors.contactValueRequired')}
          </p>
        )}
        {limitReached && (
          <p className="contact-info-error">{t('errors.contactMax', { max: CONTACT_MAX_ENTRIES })}</p>
        )}
      </div>

      {value.length > 0 && (
        <ul className="contact-info-list">
          {value.map((entry, index) => {
            const option = contactTypeOption(entry.type)
            const EntryIcon = option.icon
            return (
              <li key={`${entry.type}-${index}`} className="contact-info-item">
                <span className="contact-info-item-icon">
                  <EntryIcon />
                </span>
                <span className="contact-info-item-text">
                  {entry.description && <span className="contact-info-item-desc">{entry.description}</span>}
                  <span className="contact-info-item-value">{entry.value}</span>
                </span>
                <button
                  type="button"
                  className="contact-info-item-remove"
                  onClick={() => removeEntry(index)}
                  aria-label={t('auth.contact.remove')}
                >
                  <IconX />
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default ContactInfoWidget
