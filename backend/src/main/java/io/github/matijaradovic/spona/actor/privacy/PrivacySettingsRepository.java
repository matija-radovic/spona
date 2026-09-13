package io.github.matijaradovic.spona.actor.privacy;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacySettingsRepository extends JpaRepository<PrivacySettings, UUID> {
}
