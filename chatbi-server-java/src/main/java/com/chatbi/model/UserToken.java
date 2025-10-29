package com.chatbi.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 用户令牌实体，表示从token中解析出的用户信息。
 */
public class UserToken {

    /**
     * 用户唯一标识（可选）
     */
    private String userId;

    /**
     * 用户名（可选）
     */
    private String userName;

    /**
     * 角色名称集合
     */
    private Set<String> roleNames = new HashSet<>();

    public UserToken() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Set<String> getRoleNames() {
        return roleNames == null ? Collections.emptySet() : roleNames;
    }

    public void setRoleNames(Set<String> roleNames) {
        this.roleNames = roleNames == null ? new HashSet<>() : new HashSet<>(roleNames);
    }

    @Override
    public String toString() {
        return "UserToken{" +
                "userId='" + userId + '\'' +
                ", userName='" + userName + '\'' +
                ", roleNames=" + roleNames +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserToken userToken = (UserToken) o;
        return Objects.equals(userId, userToken.userId) &&
                Objects.equals(userName, userToken.userName) &&
                Objects.equals(getRoleNames(), userToken.getRoleNames());
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, userName, getRoleNames());
    }
}
