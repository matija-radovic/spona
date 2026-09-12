package io.github.matijaradovic.spona.organization.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMember, OrganizationMemberId> {
}
