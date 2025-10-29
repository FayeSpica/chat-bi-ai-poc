package com.chatbi.service;

public interface UserWhitelistService {

    boolean isWhitelisted(String userId);
    
    /**
     * 获取用户的角色
     * @param userId 用户ID
     * @return 用户角色 (ADMIN/OPERATOR/READER)，如果用户不在白名单中则返回 null
     */
    String getUserRole(String userId);
}
