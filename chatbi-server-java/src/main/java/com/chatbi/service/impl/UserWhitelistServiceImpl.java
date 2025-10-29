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
    public boolean isWhitelisted(UserToken userToken, String rawToken) {
        // Prefer userId, then userName, then token value
        if (userToken != null && StringUtils.hasText(userToken.getUserId())) {
            if (userWhitelistRepository.existsByUserIdAndIsActiveTrue(userToken.getUserId())) {
                return true;
            }
        }
        if (userToken != null && StringUtils.hasText(userToken.getUserName())) {
            if (userWhitelistRepository.existsByUserNameAndIsActiveTrue(userToken.getUserName())) {
                return true;
            }
        }
        if (StringUtils.hasText(rawToken)) {
            return userWhitelistRepository.existsByTokenValueAndIsActiveTrue(rawToken);
        }
        return false;
    }
}
