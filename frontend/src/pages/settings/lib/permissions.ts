// Hardcoded mirror of the backend's permission matrix - the only place it lives on the client.
import type { OrganizationRole, OrgMemberDto } from '@utils/api/organizations'

const ASSIGNABLE: Readonly<Record<OrganizationRole, readonly OrganizationRole[]>> = {
  OWNER: ['ADMIN', 'EDITOR', 'RECRUITER'],
  ADMIN: ['EDITOR', 'RECRUITER'],
  EDITOR: [],
  RECRUITER: [],
}

export const isOwner = (viewerRole: OrganizationRole): boolean => viewerRole === 'OWNER'

export const canEditOrgProfile = (viewerRole: OrganizationRole): boolean =>
  viewerRole === 'OWNER' || viewerRole === 'ADMIN'

export const canManageInvitations = (viewerRole: OrganizationRole): boolean =>
  canEditOrgProfile(viewerRole)

export const assignableRoles = (viewerRole: OrganizationRole): readonly OrganizationRole[] =>
  ASSIGNABLE[viewerRole]

export const canManageMember = (
  viewerRole: OrganizationRole,
  member: OrgMemberDto,
  viewerUsername: string | undefined,
): boolean => {
  if (member.username === viewerUsername) return false
  if (member.role === 'OWNER') return false
  if (viewerRole === 'OWNER') return true
  if (viewerRole === 'ADMIN') return member.role === 'EDITOR' || member.role === 'RECRUITER'
  return false
}
