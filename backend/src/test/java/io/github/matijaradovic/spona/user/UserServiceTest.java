package io.github.matijaradovic.spona.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users);

    @Test
    void aFreeHandleIsAvailable() {
        when(users.existsByUsername("marko")).thenReturn(false);

        assertThat(service.isUsernameAvailable("marko")).isTrue();
    }

    @Test
    void aHandleSomebodyHoldsIsNot() {
        when(users.existsByUsername("marko")).thenReturn(true);

        assertThat(service.isUsernameAvailable("marko")).isFalse();
    }

    /** Registration would refuse it anyway, so answering before the query is the honest answer. */
    @Test
    void aReservedHandleIsUnavailableWithoutALookup() {
        assertThat(service.isUsernameAvailable("settings")).isFalse();

        verify(users, never()).existsByUsername(anyString());
    }

    @Test
    void aHandleTheColumnWouldRefuseIsUnavailable() {
        assertThat(service.isUsernameAvailable("ma")).isFalse();
        assertThat(service.isUsernameAvailable("Marko")).isFalse();
        assertThat(service.isUsernameAvailable("marko!")).isFalse();
        assertThat(service.isUsernameAvailable("m".repeat(31))).isFalse();

        verify(users, never()).existsByUsername(anyString());
    }
}
