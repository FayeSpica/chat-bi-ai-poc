package com.chatbi.repository;

import com.chatbi.model.UserWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserWhitelistRepository extends JpaRepository<UserWhitelist, Long> {

    boolean existsByUserIdAndIsActiveTrue(String userId);

    boolean existsByUserNameAndIsActiveTrue(String userName);

    boolean existsByTokenValueAndIsActiveTrue(String tokenValue);
}
