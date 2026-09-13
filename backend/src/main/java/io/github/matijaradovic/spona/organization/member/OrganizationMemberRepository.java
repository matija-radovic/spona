package io.github.matijaradovic.spona.organization.member;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMember, OrganizationMemberId> {

    long countByOrganization_Id(UUID organizationId);
}
