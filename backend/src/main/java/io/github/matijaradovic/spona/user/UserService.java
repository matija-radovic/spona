package io.github.matijaradovic.spona.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.common.util.ReservedHandles;

@Service
public class UserService {

    /** Mirrors the CHECK on `username`, so an unavailable answer covers a malformed handle too. */
    private static final String HANDLE_PATTERN = "^[a-z0-9_-]{3,30}$";

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Available means registrable: legal, not reserved and not already held. */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        String handle = username.trim();
        return handle.matches(HANDLE_PATTERN)
                && !ReservedHandles.isReserved(handle)
                && !users.existsByUsername(handle);
    }
}
