import './ContactInfoWidget.scss'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconChevronDown, IconX } from '@tabler/icons-react'
import { Button, ButtonVariant } from '../Button'
import usePopoverFlip from '@shared/hooks/usePopoverFlip'
import {
  CONTACT_DESC_MAX,
  CONTACT_VALUE_MAX,
  CONTACT_MAX_ENTRIES,
} from '@utils/constants/validation'
import {
  CONTACT_TYPE_OPTIONS,
  CONTACT_GROUP_ORDER,
  CONTACT_GROUP_LABEL_KEYS,
  contactTypeOption,
  resolveContactType,
} from './contactTypes'
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
  const capsuleRef = useRef<HTMLButtonElement>(null)

  // The panel is ~8 rows tall; near the bottom of a form it would otherwise run
  // past the fold and only be reachable by scrolling.
  const { above: openAbove, panelRef } = usePopoverFlip(dropdownOpen, capsuleRef)

  useEffect(() => {
    if (!dropdownOpen) return
    const onClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') setDropdownOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onClickOutside)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [dropdownOpen])

  // ── Live validity (read per keystroke, the same way the rest of the form does)
  const trimmedValue = draftValue.trim()
  const limitReached = value.length >= CONTACT_MAX_ENTRIES
  const descTooLong = description.length > CONTACT_DESC_MAX
  const valueTooLong = draftValue.length > CONTACT_VALUE_MAX
  const valueEmpty = trimmedValue === ''
  const valueError = touched && (valueEmpty || valueTooLong)
  const canAdd = !limitReached && !valueEmpty && !descTooLong && !valueTooLong

  // Upgrades `website`/`other` to a recognised brand as the value is typed, so
  // the icon the entry will be stored with is visible before Add. An explicit
  // pick is never overridden (see `resolveContactType`).
  const effectiveType = resolveContactType(type, trimmedValue)

  const addEntry = () => {
    setTouched(true)
    if (!canAdd) return
    onChange([
      ...value,
      { type: effectiveType, description: description.trim(), value: trimmedValue },
    ])
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

  const selected = contactTypeOption(effectiveType)
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
              ref={capsuleRef}
              className="contact-info-capsule"
              onClick={() => setDropdownOpen((open) => !open)}
              aria-haspopup="listbox"
              aria-expanded={dropdownOpen}
              aria-label={`${t('auth.contact.selectType')}: ${t(selected.labelKey)}`}
            >
              <SelectedIcon />
              <IconChevronDown />
            </button>

            {dropdownOpen && (
              <ul
                ref={panelRef}
                className={`contact-info-dropdown scroll-container${openAbove ? ' is-above' : ''}`}
                role="listbox"
                aria-label={t('auth.contact.selectType')}
              >
                {CONTACT_GROUP_ORDER.map((group) => (
                  <li
                    key={group}
                    role="group"
                    aria-label={t(CONTACT_GROUP_LABEL_KEYS[group])}
                    className="contact-info-group"
                  >
                    <p className="contact-info-group-label" aria-hidden>
                      {t(CONTACT_GROUP_LABEL_KEYS[group])}
                    </p>
                    <ul className="contact-info-group-options" role="presentation">
                      {CONTACT_TYPE_OPTIONS.filter((option) => option.group === group).map(
                        (option) => {
                          const OptionIcon = option.icon
                          return (
                            <li key={option.key} role="presentation">
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
                        },
                      )}
                    </ul>
                  </li>
                ))}
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
