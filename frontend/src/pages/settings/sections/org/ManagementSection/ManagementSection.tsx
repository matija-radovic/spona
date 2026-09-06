import './ManagementSection.scss'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import {
  Button,
  ButtonVariant,
  Dropdown,
  FieldShell,
  Input,
  SectionPanel,
  type DropdownOption,
} from '@shared/components'
import useTimedFlag from '@shared/hooks/useTimedFlag'
import { parseProblem } from '@utils/api/types'
import { SLUG_MIN, SLUG_MAX, SLUG_REGEX } from '@utils/constants/validation'
import {
  changeOrgSlug,
  deleteOrg,
  fetchOrgMembers,
  transferOwnership,
  type OrgMemberDto,
} from '@utils/api/organizations'
import { SettingsModal } from '../../../components/SettingsModal'
import { isOwner } from '../../../lib/permissions'
import { useOrgSettingsContext } from '../../../lib/settingsOutletContext'

type Translate = (key: string, opts?: Record<string, unknown>) => string

const buildSlugSchema = (t: Translate) =>
  z.object({
    slug: z
      .string()
      .min(SLUG_MIN, t('errors.rangeLength', { min: SLUG_MIN, max: SLUG_MAX }))
      .max(SLUG_MAX, t('errors.rangeLength', { min: SLUG_MIN, max: SLUG_MAX }))
      .regex(SLUG_REGEX, t('errors.slugFormat')),
  })

type SlugData = z.infer<ReturnType<typeof buildSlugSchema>>

/** Rename, transfer, delete - ordered most-reversible first. OWNER only. */
const ManagementSection = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { settings, refetch } = useOrgSettingsContext()

  const slug = settings.slug
  const owner = isOwner(settings.viewerRole)

  const { active: justSaved, raise: markSaved } = useTimedFlag()
  const [admins, setAdmins] = useState<OrgMemberDto[]>([])
  const [adminsLoaded, setAdminsLoaded] = useState(false)
  const [adminsError, setAdminsError] = useState<string | null>(null)
  const [transferTarget, setTransferTarget] = useState<string | null>(null)
  const [transferOpen, setTransferOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [confirmSlug, setConfirmSlug] = useState('')
  const [busy, setBusy] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)

  const schema = useMemo(() => buildSlugSchema(t), [t])
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SlugData>({ resolver: zodResolver(schema), defaultValues: { slug } })

  const loadAdmins = useCallback((): Promise<void> => {
    if (!owner) return Promise.resolve()
    return fetchOrgMembers(slug)
      .then((rows) => {
        setAdmins(rows.filter((row) => row.role === 'ADMIN'))
        setAdminsError(null)
      })
      .catch((caught: unknown) => {
        setAdmins([])
        setAdminsError(parseProblem(caught).detail || t('errors.serverError'))
      })
      .finally(() => setAdminsLoaded(true))
  }, [slug, owner, t])

  useEffect(() => {
    void loadAdmins()
  }, [loadAdmins])

  if (!owner) {
    return <Navigate to="../profile" replace />
  }

  const adminOptions: DropdownOption[] = admins.map((member) => ({
    value: member.userId,
    label: `${member.fullName} (@${member.username})`,
  }))

  const closeModals = () => {
    if (busy) return
    setTransferOpen(false)
    setDeleteOpen(false)
    setConfirmSlug('')
    setModalError(null)
  }

  const onRename = handleSubmit(async (values) => {
    try {
      const updated = await changeOrgSlug(slug, values.slug)
      reset({ slug: updated.slug })
      markSaved()
      navigate(`/companies/${updated.slug}/settings/management`, { replace: true })
    } catch (caught) {
      setError('slug', { message: parseProblem(caught).detail || t('errors.serverError') })
    }
  })

  const onTransfer = async () => {
    if (!transferTarget) return
    setBusy(true)
    setModalError(null)
    try {
      await transferOwnership(slug, transferTarget, confirmSlug)
      setTransferOpen(false)
      setConfirmSlug('')
      // The viewer is an ADMIN now, so the sidebar loses this very section.
      await refetch()
      navigate('../profile', { replace: true })
    } catch (caught) {
      setModalError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setBusy(false)
    }
  }

  const onDelete = async () => {
    setBusy(true)
    setModalError(null)
    try {
      await deleteOrg(slug, confirmSlug)
      navigate('/companies')
    } catch (caught) {
      setModalError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <SectionPanel title={t('settings.org.title')} className="management-section">
      <form className="settings-card" onSubmit={onRename} noValidate>
        <h5 className="settings-card-title">{t('settings.org.slug.title')}</h5>

        <FieldShell label={t('auth.org.slug')} hint={t('settings.org.slug.helper')}>
          <Input error={errors.slug?.message} {...register('slug')} />
        </FieldShell>

        <div className="settings-card-actions">
          {justSaved && <p className="settings-flash">{t('common.saved')}</p>}
          <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
            {isSubmitting ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </form>

      <section className="settings-card settings-card-danger">
        <h5 className="settings-card-title">{t('settings.org.transfer.title')}</h5>
        <p className="settings-card-hint">{t('settings.org.transfer.helper')}</p>

        <div className="management-transfer">
          {adminsError && <p className="settings-inline-error">{adminsError}</p>}

          <div className="settings-card-actions management-transfer-actions">
            <Dropdown
              label={t('settings.org.transfer.title')}
              placeholder={t('settings.members.member')}
              options={adminOptions}
              value={transferTarget}
              onChange={(next) => setTransferTarget(typeof next === 'string' ? next : null)}
              loading={!adminsLoaded}
              emptyLabel={
                adminsError ? t('common.noResults') : t('settings.org.transfer.needsAdmin')
              }
            />
            <Button
              variant={ButtonVariant.DANGER_OUTLINE}
              disabled={!transferTarget}
              onClick={() => {
                setConfirmSlug('')
                setModalError(null)
                setTransferOpen(true)
              }}
            >
              {t('settings.org.transfer.button')}
            </Button>
          </div>
        </div>

        {adminsLoaded && !adminsError && adminOptions.length === 0 && (
          <p className="settings-note">{t('settings.org.transfer.needsAdmin')}</p>
        )}
      </section>

      <section className="settings-card settings-card-danger">
        <h5 className="settings-card-title">{t('settings.org.delete.title')}</h5>
        <p className="settings-card-hint">{t('settings.org.delete.warning')}</p>

        <div className="settings-card-actions">
          <Button
            variant={ButtonVariant.DANGER}
            onClick={() => {
              setConfirmSlug('')
              setModalError(null)
              setDeleteOpen(true)
            }}
          >
            {t('settings.org.delete.button')}
          </Button>
        </div>
      </section>

      <SettingsModal
        isOpen={transferOpen}
        onClose={closeModals}
        title={t('settings.org.transfer.title')}
        footer={
          <>
            <Button variant={ButtonVariant.GHOST} onClick={closeModals} disabled={busy}>
              {t('common.cancel')}
            </Button>
            <Button
              variant={ButtonVariant.DANGER}
              onClick={onTransfer}
              disabled={busy || confirmSlug !== slug}
            >
              {busy ? t('common.loading') : t('settings.org.transfer.button')}
            </Button>
          </>
        }
      >
        <p className="settings-modal-warning">{t('settings.org.transfer.confirm')}</p>

        <FieldShell label={t('settings.org.transfer.typeSlug', { slug })}>
          <Input value={confirmSlug} onChange={(event) => setConfirmSlug(event.target.value)} />
        </FieldShell>

        {modalError && <p className="settings-modal-error">{modalError}</p>}
      </SettingsModal>

      <SettingsModal
        isOpen={deleteOpen}
        onClose={closeModals}
        title={t('settings.confirm.question')}
        footer={
          <>
            <Button variant={ButtonVariant.GHOST} onClick={closeModals} disabled={busy}>
              {t('common.cancel')}
            </Button>
            <Button
              variant={ButtonVariant.DANGER}
              onClick={onDelete}
              disabled={busy || confirmSlug !== slug}
            >
              {busy ? t('common.loading') : t('settings.org.delete.button')}
            </Button>
          </>
        }
      >
        <p className="settings-modal-warning">{t('settings.org.delete.warning')}</p>

        <FieldShell label={t('settings.confirm.typeSlug', { slug })}>
          <Input value={confirmSlug} onChange={(event) => setConfirmSlug(event.target.value)} />
        </FieldShell>

        {modalError && <p className="settings-modal-error">{modalError}</p>}
      </SettingsModal>
    </SectionPanel>
  )
}

export default ManagementSection
