package io.github.matijaradovic.spona.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureTokensTest {

    /** 32 bytes are 43 unpadded Base64url characters, and every one of them is URL safe. */
    @Test
    void mintsAUrlSafeTokenOfTheExpectedWidth() {
        assertThat(SecureTokens.newToken()).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void mintsADifferentTokenEveryTime() {
        Set<String> minted = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            minted.add(SecureTokens.newToken());
        }

        assertThat(minted).hasSize(500);
    }

    @Test
    void hashesToTheWidthOfTheColumn() {
        assertThat(SecureTokens.hash(SecureTokens.newToken()))
                .hasSize(SecureTokens.HASH_LENGTH)
                .matches("^[0-9a-f]+$");
    }

    @Test
    void hashesTheSameTokenToTheSameValueAndNothingElseToIt() {
        String token = SecureTokens.newToken();

        assertThat(SecureTokens.hash(token))
                .isEqualTo(SecureTokens.hash(token))
                .isNotEqualTo(token)
                .isNotEqualTo(SecureTokens.hash(SecureTokens.newToken()));
    }

    /** The published SHA-256 of the empty string, so the algorithm is checked against something. */
    @Test
    void matchesTheKnownDigest() {
        assertThat(SecureTokens.hash(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
