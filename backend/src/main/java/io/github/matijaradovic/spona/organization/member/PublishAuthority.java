package io.github.matijaradovic.spona.organization.member;

public final class PublishAuthority {

    private PublishAuthority() {
    }

    public static boolean canPublishContent(OrganizationRole role) {
        return role == OrganizationRole.OWNER
                || role == OrganizationRole.ADMIN
                || role == OrganizationRole.PUBLISHER
                || role == OrganizationRole.EDITOR;
    }

    public static boolean canManageJobPostings(OrganizationRole role) {
        return role == OrganizationRole.OWNER
                || role == OrganizationRole.ADMIN
                || role == OrganizationRole.PUBLISHER
                || role == OrganizationRole.RECRUITER;
    }

    public static boolean canEditProfile(OrganizationRole role) {
        return role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN;
    }

    public static boolean canReadSettings(OrganizationRole role) {
        return role != null;
    }

    public static boolean canManageOrganization(OrganizationRole role) {
        return role == OrganizationRole.OWNER;
    }

    public static boolean canManageMembers(OrganizationRole role) {
        return role == OrganizationRole.OWNER || role == OrganizationRole.ADMIN;
    }

    public static boolean canManageRole(OrganizationRole caller, OrganizationRole role) {
        if (role == null || role == OrganizationRole.OWNER) {
            return false;
        }
        return caller == OrganizationRole.OWNER
                || (caller == OrganizationRole.ADMIN && role != OrganizationRole.ADMIN);
    }
}
