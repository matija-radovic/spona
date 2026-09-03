// Hardcoded mirror of the backend's permission matrix - the only place it lives on the client.
import type { OrganizationRole, OrgMemberDto } from '@utils/api/organizations'
import type { PostAsSurface } from '@shared/components/actor/PostAsSelect/postAsValue'

const ASSIGNABLE: Readonly<Record<OrganizationRole, readonly OrganizationRole[]>> = {
  OWNER: ['ADMIN', 'EDITOR', 'RECRUITER'],
  ADMIN: ['EDITOR', 'RECRUITER'],
  EDITOR: [],
  RECRUITER: [],
}

const PUBLISH_AS: Readonly<Record<OrganizationRole, Readonly<Record<PostAsSurface, boolean>>>> = {
  OWNER: { content: true, job: true },
  ADMIN: { content: true, job: true },
  EDITOR: { content: true, job: false },
  RECRUITER: { content: false, job: true },
}

export const isOwner = (viewerRole: OrganizationRole): boolean => viewerRole === 'OWNER'

export const canEditOrgProfile = (viewerRole: OrganizationRole): boolean =>
  viewerRole === 'OWNER' || viewerRole === 'ADMIN'

export const canManageInvitations = (viewerRole: OrganizationRole): boolean =>
  canEditOrgProfile(viewerRole)

export const assignableRoles = (viewerRole: OrganizationRole): readonly OrganizationRole[] =>
  ASSIGNABLE[viewerRole]

export const canPublishAs = (role: OrganizationRole, surface: PostAsSurface): boolean =>
  PUBLISH_AS[role][surface]

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
