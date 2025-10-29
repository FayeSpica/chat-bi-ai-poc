package com.chatbi.service;

import com.chatbi.model.UserToken;

public interface UserWhitelistService {

    boolean isWhitelisted(UserToken userToken, String rawToken);
}
