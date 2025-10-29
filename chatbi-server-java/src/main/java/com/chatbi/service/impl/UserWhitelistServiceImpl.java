package com.chatbi.service.impl;

import com.chatbi.model.UserToken;
import com.chatbi.repository.UserWhitelistRepository;
import com.chatbi.service.UserWhitelistService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserWhitelistServiceImpl implements UserWhitelistService {

    private final UserWhitelistRepository userWhitelistRepository;

    public UserWhitelistServiceImpl(UserWhitelistRepository userWhitelistRepository) {
        this.userWhitelistRepository = userWhitelistRepository;
    }

    @Override
    public boolean isWhitelisted(String userId) {
        if (StringUtils.hasText(userId)) {
            return userWhitelistRepository.existsByUserIdAndIsActiveTrue(userId);
        }
        return false;
    }
}
