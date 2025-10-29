package com.chatbi.repository;

import com.chatbi.model.UserWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWhitelistRepository extends JpaRepository<UserWhitelist, Long> {

    boolean existsByUserIdAndIsActiveTrue(String userId);
    
    Optional<UserWhitelist> findByUserIdAndIsActiveTrue(String userId);
}
