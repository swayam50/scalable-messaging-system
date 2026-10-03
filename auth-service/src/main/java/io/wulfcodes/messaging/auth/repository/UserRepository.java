package io.wulfcodes.messaging.auth.repository;

import io.wulfcodes.messaging.auth.model.po.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(String prefix);
}
