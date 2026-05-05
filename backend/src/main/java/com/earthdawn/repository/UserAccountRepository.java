package com.earthdawn.repository;

import com.earthdawn.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    List<UserAccount> findAllByOrderByUsernameAsc();
    boolean existsByUsername(String username);
}
