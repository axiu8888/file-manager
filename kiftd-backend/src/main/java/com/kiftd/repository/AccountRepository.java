package com.kiftd.repository;

import com.kiftd.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByAccountName(String accountName);
    boolean existsByAccountName(String accountName);
}
