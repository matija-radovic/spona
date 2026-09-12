import './MembersSection.scss'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconRefresh } from '@tabler/icons-react'
import { SectionPanel } from '@shared/components'
import { useUser } from '@shared/contexts'
import { parseProblem } from '@utils/api/types'
import {
  cancelInvitation,
  createInvitation,
  dismissInvitation,
  fetchInvitations,
  fetchOrgMembers,
  removeMember,
  setMemberRole,
  type InvitationDto,
  type OrganizationRole,
  type OrgMemberDto,
} from '@utils/api/organizations'
import { canManageInvitations } from '../../../lib/permissions'
import { useOrgSettingsContext } from '../../../lib/settingsOutletContext'
import { MembersTable } from './MembersTable'
import { InvitationsTable } from './InvitationsTable'
import { InviteForm } from './InviteForm'

const MembersSection = () => {
  const { t } = useTranslation()
  const { settings } = useOrgSettingsContext()
  const { user } = useUser()

  const slug = settings.slug
  const canManage = canManageInvitations(settings.viewerRole)

  const [members, setMembers] = useState<OrgMemberDto[]>([])
  const [invitations, setInvitations] = useState<InvitationDto[]>([])
  const [loaded, setLoaded] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [membersError, setMembersError] = useState<string | null>(null)
  const [invitationsError, setInvitationsError] = useState<string | null>(null)
  const [busyUserId, setBusyUserId] = useState<string | null>(null)
  const [busyInvitationId, setBusyInvitationId] = useState<string | null>(null)

  const message = useCallback(
    (caught: unknown) => parseProblem(caught).detail || t('errors.serverError'),
    [t],
  )

  // Promise chains, not async/await, so the effect below writes no state synchronously.
  const loadMembers = useCallback(
    (): Promise<void> =>
      fetchOrgMembers(slug)
        .then((rows) => {
          setMembers(rows)
          setMembersError(null)
        })
        .catch((caught: unknown) => setMembersError(message(caught))),
    [slug, message],
  )

  const loadInvitations = useCallback(
    (): Promise<void> =>
      fetchInvitations(slug)
        .then((rows) => {
          setInvitations(rows)
          setInvitationsError(null)
        })
        .catch((caught: unknown) => setInvitationsError(message(caught))),
    [slug, message],
  )

  const loadAll = useCallback(
    (): Promise<void> =>
      Promise.all([loadMembers(), canManage ? loadInvitations() : undefined]).then(() =>
        setLoaded(true),
      ),
    [loadMembers, loadInvitations, canManage],
  )

  useEffect(() => {
    void loadAll()
  }, [loadAll])

  const onRefresh = async () => {
    setRefreshing(true)
    await loadAll()
    setRefreshing(false)
  }

  // ── Members: optimistic, revert on rejection ───────────────────────────────

  const onRoleChange = async (member: OrgMemberDto, role: OrganizationRole) => {
    if (member.role === role) return

    const previous = members
    setMembers(members.map((row) => (row.userId === member.userId ? { ...row, role } : row)))
    setMembersError(null)
    setBusyUserId(member.userId)
    try {
      await setMemberRole(slug, member.userId, role)
    } catch (caught) {
      setMembers(previous)
      setMembersError(message(caught))
    } finally {
      setBusyUserId(null)
    }
  }

  const onRemove = async (member: OrgMemberDto) => {
    const previous = members
    setMembers(members.filter((row) => row.userId !== member.userId))
    setMembersError(null)
    setBusyUserId(member.userId)
    try {
      await removeMember(slug, member.userId)
    } catch (caught) {
      setMembers(previous)
      setMembersError(message(caught))
    } finally {
      setBusyUserId(null)
    }
  }

  // ── Invitations: request, then refetch - on success AND on failure ─────────

  const onInvite = async (identifier: string, role: OrganizationRole): Promise<string | null> => {
    setInvitationsError(null)
    try {
      await createInvitation(slug, identifier, role)
      await loadInvitations()
      return null
    } catch (caught) {
      await loadInvitations()
      return message(caught)
    }
  }

  const onCancel = async (invitation: InvitationDto) => {
    setBusyInvitationId(invitation.id)
    setInvitationsError(null)
    try {
      await cancelInvitation(slug, invitation.id)
    } catch (caught) {
      setInvitationsError(message(caught))
    } finally {
      await loadInvitations()
      setBusyInvitationId(null)
    }
  }

  const onDismiss = async (invitation: InvitationDto) => {
    setBusyInvitationId(invitation.id)
    setInvitationsError(null)
    try {
      await dismissInvitation(slug, invitation.id)
    } catch (caught) {
      setInvitationsError(message(caught))
    } finally {
      await loadInvitations()
      setBusyInvitationId(null)
    }
  }

  const refreshButton = (
    <button
      type="button"
      className="settings-icon-button members-refresh"
      onClick={onRefresh}
      disabled={refreshing}
      aria-label={t('settings.members.refresh')}
    >
      <IconRefresh aria-hidden />
    </button>
  )

  return (
    <SectionPanel title={t('settings.members.title')} className="members-section">
      {!loaded && <p className="settings-note">{t('common.loading')}</p>}

      {loaded && (
        <>
          {!canManage && <p className="settings-note">{t('settings.readOnly')}</p>}

          <div className="settings-block members-block">
            <div className="settings-block-head">
              <h5 className="settings-block-title">
                {t('settings.members.memberTableTitle')}
                <span className="settings-count">{members.length}</span>
              </h5>
              {refreshButton}
            </div>

            {membersError && <p className="settings-inline-error">{membersError}</p>}

            <MembersTable
              members={members}
              viewerRole={settings.viewerRole}
              viewerUsername={user?.username}
              busyUserId={busyUserId}
              onRoleChange={onRoleChange}
              onRemove={onRemove}
            />
          </div>

          {canManage && (
            <div className="settings-block">
              <div className="settings-block-head">
                <h5 className="settings-block-title">{t('settings.invitations.title')}</h5>
                {refreshButton}
              </div>

              <InviteForm viewerRole={settings.viewerRole} onInvite={onInvite} />

              {invitationsError && <p className="settings-inline-error">{invitationsError}</p>}

              {invitations.length === 0 ? (
                <p className="settings-empty">{t('settings.invitations.empty')}</p>
              ) : (
                <InvitationsTable
                  invitations={invitations}
                  busyId={busyInvitationId}
                  onCancel={onCancel}
                  onDismiss={onDismiss}
                />
              )}
            </div>
          )}
        </>
      )}
    </SectionPanel>
  )
}

export default MembersSection
