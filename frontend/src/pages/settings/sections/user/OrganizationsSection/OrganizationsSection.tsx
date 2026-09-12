import './OrganizationsSection.scss'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconPlus } from '@tabler/icons-react'
import {
  ActorAvatar,
  ActorAvatarSize,
  Button,
  ButtonVariant,
  SectionPanel,
  Tooltip,
} from '@shared/components'
import useFormat from '@shared/hooks/useFormat'
import { actorPath } from '@utils/actorPath'
import { parseProblem } from '@utils/api/types'
import {
  acceptInvitation,
  declineInvitation,
  fetchMyInvitations,
  fetchMyOrganizations,
  leaveOrganization,
  type MyInvitationDto,
  type MyOrganizationDto,
} from '@utils/api/memberships'
import { SettingsModal } from '../../../components/SettingsModal'
import { RoleChip } from '../../../components/RoleChip'
import { useUserSettingsContext } from '../../../lib/settingsOutletContext'

const OrganizationsSection = () => {
  const { t } = useTranslation()
  const format = useFormat()
  const { settings } = useUserSettingsContext()

  const [invitations, setInvitations] = useState<MyInvitationDto[]>([])
  const [organizations, setOrganizations] = useState<MyOrganizationDto[]>([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [highlighted, setHighlighted] = useState<string | null>(null)

  const [leaveTarget, setLeaveTarget] = useState<MyOrganizationDto | null>(null)
  const [leaveBusy, setLeaveBusy] = useState(false)
  const [leaveError, setLeaveError] = useState<string | null>(null)

  const username = settings.username

  const load = useCallback(
    (): Promise<void> =>
      Promise.allSettled([fetchMyInvitations(username), fetchMyOrganizations(username)]).then(
        ([invitationsResult, organizationsResult]) => {
          if (invitationsResult.status === 'fulfilled') setInvitations(invitationsResult.value)
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
        },
      ),
    [username, t],
  )

  useEffect(() => {
    void load()
  }, [load])

  const onAcceptRow = async (invitation: MyInvitationDto) => {
    setBusyId(invitation.id)
    setError(null)
    try {
      await acceptInvitation(invitation.id)
      await load()
      setHighlighted(invitation.organization.handle)
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setBusyId(null)
    }
  }

  // No confirm modal: declining is recoverable, the org can simply invite again.
  const onDeclineRow = async (invitation: MyInvitationDto) => {
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

  const onLeave = async () => {
    if (!leaveTarget) return
    setLeaveBusy(true)
    setLeaveError(null)
    try {
      await leaveOrganization(username, leaveTarget.organization.handle)
      setLeaveTarget(null)
      await load()
    } catch (caught) {
      setLeaveError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setLeaveBusy(false)
    }
  }

  const createButton = (
    <Button variant={ButtonVariant.PRIMARY} to="/companies/new">
      <IconPlus aria-hidden />
      {t('settings.organizations.create')}
    </Button>
  )

  return (
    <SectionPanel title={t('settings.organizations.title')} className="organizations-section">
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
            {organizations.length > 0 && (
              <Button
                variant={ButtonVariant.TERTIARY}
                to="/companies/new"
                className="organizations-create-icon"
              >
                <IconPlus aria-hidden />
                <span className="organizations-create-label">
                  {t('settings.organizations.create')}
                </span>
              </Button>
            )}
          </div>

          {organizations.length === 0 ? (
            <div className="organizations-empty">
              <p>{t('settings.organizations.empty')}</p>
              {createButton}
            </div>
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
                        <Button
                          variant={ButtonVariant.TERTIARY}
                          to={`/companies/${membership.organization.handle}/settings`}
                        >
                          {t('settings.organizations.manage')}
                        </Button>

                        {membership.role === 'OWNER' ? (
                          <Tooltip label={t('settings.organizations.ownerCannotLeave')}>
                            <Button variant={ButtonVariant.DANGER} disabled>
                              {t('settings.organizations.leave')}
                            </Button>
                          </Tooltip>
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

      <SettingsModal
        isOpen={leaveTarget !== null}
        onClose={() => {
          if (!leaveBusy) {
            setLeaveTarget(null)
            setLeaveError(null)
          }
        }}
        title={t('settings.confirm.question')}
        footer={
          <>
            <Button
              variant={ButtonVariant.GHOST}
              onClick={() => setLeaveTarget(null)}
              disabled={leaveBusy}
            >
              {t('common.cancel')}
            </Button>
            <Button variant={ButtonVariant.DANGER} onClick={onLeave} disabled={leaveBusy}>
              {leaveBusy ? t('common.loading') : t('settings.organizations.leave')}
            </Button>
          </>
        }
      >
        <p>
          {t('settings.organizations.leaveConfirm', {
            name: leaveTarget?.organization.name ?? '',
          })}
        </p>
        {leaveError && <p className="settings-modal-error">{leaveError}</p>}
      </SettingsModal>
    </SectionPanel>
  )
}

export default OrganizationsSection
