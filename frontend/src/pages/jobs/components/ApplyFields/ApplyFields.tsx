import './ApplyFields.scss'
import type { ReactNode } from 'react'
import { Controller, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { IconInbox } from '@tabler/icons-react'
import {
  Button,
  ButtonVariant,
  ButtonSize,
  ContactInfoWidget,
  FieldShell,
  Input,
} from '@shared/components'
import type { ContactEntry } from '@shared/components'
import { APPLICATION_TYPE_VALUES } from '@pages/jobs/lib/jobEnums'
import type { JobFormData } from '@pages/jobs/lib/jobSchema'

/** Entries the publisher already has on file, minus the ones already added. */
const missingFrom = (current: ContactEntry[], saved: readonly ContactEntry[]): ContactEntry[] =>
  saved.filter((entry) => !current.some((held) => held.type === entry.type && held.value === entry.value))

interface ApplyFieldsProps {
  register: UseFormRegister<JobFormData>
  control: Control<JobFormData>
  errors: FieldErrors<JobFormData>
  /** The publishing actor's saved contact entries, offered as a starting point. */
  savedContacts?: readonly ContactEntry[]
  /** Lock the current pick, edits are available. User can't change the pick*/
  typeLocked?: boolean
  /** Stands in for the CV-drop note, which configures nothing */
  cvDropPanel?: ReactNode
}

/** The application-type picker and the chosen type's panel */
const ApplyFields = ({
  register,
  control,
  errors,
  savedContacts = [],
  typeLocked = false,
  cvDropPanel,
}: ApplyFieldsProps) => {
  const { t } = useTranslation()

  return (
    <div className="apply-fields">
      <Controller
        control={control}
        name="applicationType"
        render={({ field: typeField }) => (
          <>
            <FieldShell
              label={t('jobs.create.applyTitle')}
              htmlLabel={false}
              className="apply-fields-picker"
              hint={typeLocked ? t('jobs.applyTypeLocked') : undefined}
            >
              {typeLocked ? (
                <span className="apply-fields-locked">
                  {t(`jobs.application.${typeField.value}`)}
                </span>
              ) : (
                <div
                  className="apply-fields-toggles"
                  role="group"
                  aria-label={t('jobs.create.applyType')}
                >
                  {APPLICATION_TYPE_VALUES.map((value) => (
                    <button
                      key={value}
                      type="button"
                      className={`apply-fields-toggle${typeField.value === value ? ' is-active' : ''}`}
                      onClick={() => typeField.onChange(value)}
                      aria-pressed={typeField.value === value}
                    >
                      {t(`jobs.application.${value}`)}
                    </button>
                  ))}
                </div>
              )}
            </FieldShell>

            <div className="apply-fields-panel">
              {typeField.value === 'CONTACT_ONLY' && (
                <Controller
                  control={control}
                  name="contactInformation"
                  render={({ field }) => {
                    const offered = missingFrom(field.value, savedContacts)
                    return (
                      <>
                        <p className="apply-fields-hint">{t('jobs.create.contactHint')}</p>

                        {offered.length > 0 && (
                          <Button
                            variant={ButtonVariant.TERTIARY}
                            size={ButtonSize.SMALL}
                            className="apply-fields-prefill"
                            onClick={() => field.onChange([...field.value, ...offered])}
                          >
                            {t('jobs.create.useSavedContacts')}
                          </Button>
                        )}

                        <ContactInfoWidget
                          label={t('jobs.fields.contactInformation')}
                          value={field.value}
                          onChange={field.onChange}
                        />

                        {errors.contactInformation?.message && (
                          <p className="apply-fields-error">{errors.contactInformation.message}</p>
                        )}
                      </>
                    )
                  }}
                />
              )}

              {typeField.value === 'EXTERNAL_URL' && (
                <FieldShell
                  label={t('jobs.fields.externalUrl')}
                  required
                  hint={t('jobs.create.externalHint')}
                >
                  <Input
                    type="url"
                    placeholder="https://"
                    error={errors.externalUrl?.message}
                    {...register('externalUrl')}
                  />
                </FieldShell>
              )}

              {typeField.value === 'CV_DROP' &&
                (cvDropPanel ?? (
                  <p className="apply-fields-note">
                    <IconInbox aria-hidden />
                    {t('jobs.create.cvDropHint')}
                  </p>
                ))}
            </div>
          </>
        )}
      />
    </div>
  )
}

export default ApplyFields
