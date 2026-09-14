import './ContactInfoWidget.scss'
import { useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { IconX } from '@tabler/icons-react'
import usePopover from '@shared/hooks/usePopover'
import { Button, ButtonVariant } from '../Button'
import { FieldShell } from '../FieldShell'
import { SelectTrigger } from '../SelectTrigger'
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
  const [touched, setTouched] = useState(false)

  const {
    open: typeOpen,
    setOpen: setTypeOpen,
    toggle: toggleType,
    rootRef,
    triggerRef,
    panelRef,
    above: openAbove,
  } = usePopover()

  const trimmedValue = draftValue.trim()
  const limitReached = value.length >= CONTACT_MAX_ENTRIES
  const descTooLong = description.length > CONTACT_DESC_MAX
  const valueTooLong = draftValue.length > CONTACT_VALUE_MAX
  const valueEmpty = trimmedValue === ''
  const valueError = touched && (valueEmpty || valueTooLong)
  const canAdd = !limitReached && !valueEmpty && !descTooLong && !valueTooLong

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
    <FieldShell label={label} htmlLabel={false}>
      <div className="contact-info">
        <div className="contact-info-builder">
          {/* Row 1 - type capsule + description */}
          <div className="contact-info-row">
            <div className="contact-info-type" ref={rootRef}>
              <SelectTrigger
                ref={triggerRef}
                className="contact-info-capsule"
                open={typeOpen}
                leading={<SelectedIcon />}
                onClick={toggleType}
                aria-haspopup="listbox"
                aria-label={`${t('auth.contact.selectType')}: ${t(selected.labelKey)}`}
              />

              {typeOpen && (
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
                                    setTypeOpen(false)
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
            <p className="contact-info-error">
              {t('errors.contactMax', { max: CONTACT_MAX_ENTRIES })}
            </p>
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
                    {entry.description && (
                      <span className="contact-info-item-desc">{entry.description}</span>
                    )}
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
    </FieldShell>
  )
}

export default ContactInfoWidget
