package io.github.matijaradovic.spona.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u"
            + " where locate(lower(:q), lower(u.fullName)) > 0 or locate(lower(:q), lower(u.username)) > 0"
            + " order by u.username")
    Page<User> search(String q, Pageable pageable);
}
