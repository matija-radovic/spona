package io.github.matijaradovic.spona.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import io.github.matijaradovic.spona.config.props.JwtProperties;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class JwtServiceTest {

    private static final String SECRET = "dev-only-secret-change-me-at-least-32-bytes";

    private final JwtService service = new JwtService(new JwtProperties(SECRET, Duration.ofDays(7)));

    @Test
    void signsAndParsesTheFourClaims() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.userWithId("matija", id);

        Jwt jwt = service.parse(service.sign(user));

        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getClaimAsString(JwtService.USERNAME_CLAIM)).isEqualTo("matija");
        Instant issuedAt = Objects.requireNonNull(jwt.getIssuedAt());
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        JwtService other = new JwtService(new JwtProperties("a-completely-different-secret-of-32-plus", Duration.ofDays(7)));
        String token = other.sign(TestFixtures.userWithId("matija", UUID.randomUUID()));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    /**
     * `JwtClaimsSet` refuses an `exp` before its `iat`, so the expired token is signed here rather
     * than through `sign`. A whole day back clears the decoder's 60 second clock skew allowance.
     */
    @Test
    void rejectsAnExpiredToken() {
        Instant yesterday = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.USERNAME_CLAIM, "matija")
                .issuedAt(yesterday)
                .expiresAt(yesterday.plusSeconds(60))
                .build();

        String token = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build()
                .encode(JwtEncoderParameters.from(claims)).getTokenValue();

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesASecretShorterThanTheHmacDigest() {
        JwtProperties tooShort = new JwtProperties("sixteen-bytes-ok", Duration.ofDays(7));

        assertThatThrownBy(() -> new JwtService(tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> service.parse("not.a.jwt")).isInstanceOf(JwtException.class);
    }
}
