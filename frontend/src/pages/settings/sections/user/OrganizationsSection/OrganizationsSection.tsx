import './OrganizationsSection.scss'
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize, Button, ButtonVariant } from '@shared/components'
import { useUser } from '@shared/contexts'
import useFormat from '@shared/hooks/useFormat'
import { actorPath } from '@utils/actorPath'
import { parseProblem } from '@utils/api/types'
import type { ActorRefDto } from '@utils/api/profiles'
import type { OrganizationRole } from '@utils/api/organizations'
import {
  acceptInvitation,
  declineInvitation,
  fetchMyInvitations,
  fetchMyOrganizations,
  leaveOrganization,
  resolveInvitationToken,
  type MyInvitationDto,
  type MyOrganizationDto,
} from '@utils/api/memberships'
import { consumePendingInvite } from '@utils/pendingInvite'
import { SettingsModal } from '../../../components/SettingsModal'
import { SettingsSection } from '../../../components/SettingsSection'
import { RoleChip } from '../../../components/RoleChip'
import { useUserSettingsContext } from '../../../lib/settingsOutletContext'
import { InvitationModal, InviteModalState, stateFromPreview } from './InvitationModal'

/** What the invitation modal is currently showing. `id` is null for a terminal
 *  state, which has nothing left to accept. */
interface InviteModalData {
  state: InviteModalState
  id: string | null
  organization: ActorRefDto | null
  role: OrganizationRole | null
  invitedBy: ActorRefDto | null
}

const TERMINAL_MODAL: InviteModalData = {
  state: InviteModalState.NOT_FOUND,
  id: null,
  organization: null,
  role: null,
  invitedBy: null,
}

const OrganizationsSection = () => {
  const { t } = useTranslation()
  const format = useFormat()
  const { settings } = useUserSettingsContext()
  const { logout } = useUser()
  const [searchParams, setSearchParams] = useSearchParams()

  const [invitations, setInvitations] = useState<MyInvitationDto[]>([])
  const [organizations, setOrganizations] = useState<MyOrganizationDto[]>([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [highlighted, setHighlighted] = useState<string | null>(null)

  const [modal, setModal] = useState<InviteModalData | null>(null)
  const [modalBusy, setModalBusy] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)
  const [leaveTarget, setLeaveTarget] = useState<MyOrganizationDto | null>(null)

  const username = settings.username
  const inviteParam = searchParams.get('invite')

  // Captured at mount so the strip below cannot re-trigger the load or re-open the modal.
  const [initialInviteId] = useState(inviteParam)

  const load = useCallback(
    (openInviteId?: string | null): Promise<void> =>
      Promise.allSettled([fetchMyInvitations(username), fetchMyOrganizations(username)]).then(
        ([invitationsResult, organizationsResult]) => {
          const rows =
            invitationsResult.status === 'fulfilled' ? invitationsResult.value : []
          if (invitationsResult.status === 'fulfilled') setInvitations(rows)
          if (organizationsResult.status === 'fulfilled') {
            setOrganizations(organizationsResult.value)
          }

          const failure =
            invitationsResult.status === 'rejected'
              ? invitationsResult.reason
              : organizationsResult.status === 'rejected'
                ? organizationsResult.reason
                : null
          setError(failure ? parseProblem(failure).detail || t('errors.serverError') : null)
          setLoaded(true)

          if (!openInviteId) return
          const row = rows.find((invitation) => invitation.id === openInviteId)
          setModal(
            row
              ? {
                  state: InviteModalState.WAITING,
                  id: row.id,
                  organization: row.organization,
                  role: row.role,
                  invitedBy: row.invitedBy,
                }
              : TERMINAL_MODAL,
          )
        },
      ),
    [username, t],
  )

  useEffect(() => {
    void load(initialInviteId)
  }, [load, initialInviteId])

  // Strip `?invite` so a reload or a shared URL never re-opens the modal.
  useEffect(() => {
    if (!inviteParam) return
    const next = new URLSearchParams(searchParams)
    next.delete('invite')
    setSearchParams(next, { replace: true })
  }, [inviteParam, searchParams, setSearchParams])

  // Land the token an invited guest parked before registering: read-and-clear, then resolve.
  useEffect(() => {
    const pending = consumePendingInvite()
    if (!pending) return

    let alive = true
    resolveInvitationToken(pending.token)
      .then((preview) => {
        if (!alive) return
        setModal({
          state: stateFromPreview(preview),
          id: preview.status === 'WAITING' ? preview.id : null,
          organization: preview.organization,
          role: preview.role,
          invitedBy: preview.invitedBy,
        })
      })
      .catch(() => {
        if (alive) setModal(TERMINAL_MODAL)
      })

    return () => {
      alive = false
    }
  }, [])

  /** Changes both blocks, so it refetches both. Resolves to the failure message, or null. */
  const runAccept =async (id: string, slug: string | null): Promise<string | null> => {
    try {
      await acceptInvitation(id)
      await load()
      setHighlighted(slug)
      return null
    } catch (caught) {
      return parseProblem(caught).detail || t('errors.serverError')
    }
  }

  const onAcceptRow = async (invitation: MyInvitationDto) => {
    setBusyId(invitation.id)
    setError(null)
    setError(await runAccept(invitation.id, invitation.organization.handle))
    setBusyId(null)
  }

  // No confirm modal: declining is recoverable, the org can simply invite again.
  const onDeclineRow =async (invitation: MyInvitationDto) => {
    setBusyId(invitation.id)
    setError(null)
    try {
      await declineInvitation(invitation.id)
      await load()
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setBusyId(null)
    }
  }

  const closeModal = () => {
    if (modalBusy) return
    setModal(null)
    setModalError(null)
  }

  const onModalAccept = async () => {
    if (!modal?.id) return
    setModalBusy(true)
    setModalError(null)
    const message = await runAccept(modal.id, modal.organization?.handle ?? null)
    setModalBusy(false)
    if (message) setModalError(message)
    else setModal(null)
  }

  const onModalDecline = async () => {
    if (!modal?.id) return
    setModalBusy(true)
    setModalError(null)
    try {
      await declineInvitation(modal.id)
      await load()
      setModal(null)
    } catch (caught) {
      setModalError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setModalBusy(false)
    }
  }

  const onLeave = async () => {
    if (!leaveTarget) return
    setModalBusy(true)
    setModalError(null)
    try {
      await leaveOrganization(username, leaveTarget.organization.handle)
      setLeaveTarget(null)
      await load()
    } catch (caught) {
      setModalError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setModalBusy(false)
    }
  }

  return (
    <SettingsSection title={t('settings.organizations.title')} className="organizations-section">
      {error && <p className="settings-inline-error">{error}</p>}

      {!loaded && <p className="settings-note">{t('common.loading')}</p>}

      {invitations.length > 0 && (
        <div className="settings-block">
          <div className="settings-block-head">
            <h5 className="settings-block-title">{t('settings.organizations.invitations')}</h5>
          </div>

          <ul className="organizations-invites">
            {invitations.map((invitation) => (
              <li key={invitation.id} className="organizations-invite">
                <ActorAvatar
                  avatarUrl={invitation.organization.avatarUrl}
                  actorType="org"
                  size={ActorAvatarSize.SM}
                  alt={invitation.organization.name}
                />

                <div className="organizations-invite-identity">
                  <Link
                    className="organizations-invite-name"
                    to={actorPath(invitation.organization)}
                  >
                    {invitation.organization.name}
                  </Link>
                  <span className="organizations-invite-meta">
                    {t('settings.organizations.invitedYou', {
                      name: invitation.invitedBy?.name ?? '-',
                    })}
                    {' · '}
                    {format.date(invitation.createdAt)}
                  </span>
                </div>

                <RoleChip role={invitation.role} />

                <div className="organizations-invite-actions">
                  <Button
                    variant={ButtonVariant.PRIMARY}
                    onClick={() => onAcceptRow(invitation)}
                    disabled={busyId === invitation.id}
                  >
                    {t('settings.organizations.accept')}
                  </Button>
                  <Button
                    variant={ButtonVariant.TERTIARY}
                    onClick={() => onDeclineRow(invitation)}
                    disabled={busyId === invitation.id}
                  >
                    {t('settings.organizations.decline')}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {loaded && (
        <div className="settings-block">
          <div className="settings-block-head">
            <h5 className="settings-block-title">{t('settings.organizations.mine')}</h5>
          </div>

          {organizations.length === 0 ? (
            <p className="settings-empty">{t('settings.organizations.empty')}</p>
          ) : (
            <ul className="organizations-list">
              {organizations.map((membership) => {
                const isHighlighted = highlighted === membership.organization.handle
                return (
                  <li
                    key={membership.organization.handle}
                    className={`organizations-card${isHighlighted ? ' is-highlighted' : ''}`}
                  >
                    <div className="organizations-card-head">
                      <ActorAvatar
                        avatarUrl={membership.organization.avatarUrl}
                        actorType="org"
                        size={ActorAvatarSize.SM}
                        alt={membership.organization.name}
                      />
                      <Link
                        className="organizations-card-name"
                        to={actorPath(membership.organization)}
                      >
                        {membership.organization.name}
                      </Link>
                      <RoleChip role={membership.role} />

                      <div className="organizations-card-actions">
                        {membership.canManage && (
                          <Button
                            variant={ButtonVariant.TERTIARY}
                            to={`/companies/${membership.organization.handle}/settings`}
                          >
                            {t('settings.organizations.manage')}
                          </Button>
                        )}

                        {membership.role === 'OWNER' ? (
                          <span className="organizations-card-note">
                            {t('settings.organizations.ownerCannotLeave')}
                          </span>
                        ) : (
                          <Button
                            variant={ButtonVariant.DANGER}
                            onClick={() => setLeaveTarget(membership)}
                          >
                            {t('settings.organizations.leave')}
                          </Button>
                        )}
                      </div>
                    </div>

                    <dl className="organizations-card-detail">
                      <dt>{t('settings.organizations.role')}</dt>
                      <dd>{t(`settings.roles.${membership.role}`)}</dd>

                      <dt>{t('settings.organizations.joined')}</dt>
                      <dd>{format.date(membership.joinedAt)}</dd>

                      <dt>{t('settings.organizations.roleUpdated')}</dt>
                      <dd>{format.date(membership.roleUpdatedAt)}</dd>

                      <dt>{t('settings.organizations.invitedBy')}</dt>
                      <dd>
                        {membership.invitedBy ? (
                          <Link to={actorPath(membership.invitedBy)}>
                            @{membership.invitedBy.handle}
                          </Link>
                        ) : (
                          '-'
                        )}
                      </dd>

                      <dt>{t('settings.organizations.memberCount')}</dt>
                      <dd>{membership.memberCount}</dd>
                    </dl>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}

      <InvitationModal
        isOpen={modal !== null}
        state={modal?.state ?? InviteModalState.WAITING}
        organization={modal?.organization ?? null}
        role={modal?.role ?? null}
        invitedBy={modal?.invitedBy ?? null}
        busy={modalBusy}
        error={modalError}
        onAccept={onModalAccept}
        onDecline={onModalDecline}
        onLogout={logout}
        onClose={closeModal}
      />

      <SettingsModal
        isOpen={leaveTarget !== null}
        onClose={() => {
          if (!modalBusy) {
            setLeaveTarget(null)
            setModalError(null)
          }
        }}
        title={t('settings.confirm.question')}
        footer={
          <>
            <Button
              variant={ButtonVariant.GHOST}
              onClick={() => setLeaveTarget(null)}
              disabled={modalBusy}
            >
              {t('common.cancel')}
            </Button>
            <Button variant={ButtonVariant.DANGER} onClick={onLeave} disabled={modalBusy}>
              {modalBusy ? t('common.loading') : t('settings.organizations.leave')}
            </Button>
          </>
        }
      >
        <p>
          {t('settings.organizations.leaveConfirm', {
            name: leaveTarget?.organization.name ?? '',
          })}
        </p>
        {modalError && <p className="settings-modal-error">{modalError}</p>}
      </SettingsModal>
    </SettingsSection>
  )
}

export default OrganizationsSection
