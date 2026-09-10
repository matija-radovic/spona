package io.github.matijaradovic.spona.support;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.matijaradovic.spona.support.NamedEnumProbe.ProbePurpose;

public interface NamedEnumProbeRepository extends JpaRepository<NamedEnumProbe, UUID> {

    List<NamedEnumProbe> findByPurpose(ProbePurpose purpose);
}
