package com.chatbi.interceptor;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.exception.TokenValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token验证拦截器
 * 拦截带有 @EnableAuth 注解的请求，验证请求头中的token
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenInterceptor.class);
    
    /**
     * Token在请求头中的名称
     */
    private static final String TOKEN_HEADER = "token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    
    /**
     * 这里可以配置有效的token列表，实际应用中应该从数据库或配置中心获取
     * 或者使用JWT等方式进行token验证
     */
    private static final String[] VALID_TOKENS = {
        "demo-token-123456",
        "test-token-abcdef"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只处理方法处理器
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // 检查类或方法上是否有 @EnableAuth 注解
        EnableAuth classAuth = handlerMethod.getBeanType().getAnnotation(EnableAuth.class);
        EnableAuth methodAuth = handlerMethod.getMethod().getAnnotation(EnableAuth.class);
        
        // 如果类或方法上有 @EnableAuth 注解，则进行token验证
        EnableAuth enableAuth = methodAuth != null ? methodAuth : classAuth;
        
        if (enableAuth != null) {
            // 从请求头获取token
            String token = getTokenFromRequest(request);
            
            if (token == null || token.trim().isEmpty()) {
                if (enableAuth.required()) {
                    logger.warn("Token missing for request: {} {}", 
                        request.getMethod(), request.getRequestURI());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"未提供认证token\",\"code\":401}");
                    return false;
                } else {
                    // 如果required为false，允许继续处理
                    return true;
                }
            }
            
            // 验证token
            if (!isValidToken(token)) {
                logger.warn("Invalid token for request: {} {}", 
                    request.getMethod(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"认证token无效\",\"code\":401}");
                return false;
            }
            
            // 角色校验：当注解中配置了 roleNames 时，要求用户至少具备其中一个角色
            String[] requiredRoles = enableAuth.roleNames();
            if (requiredRoles != null && requiredRoles.length > 0) {
                Set<String> userRoles = parseRolesFromToken(token);
                boolean hasAnyRequiredRole = Arrays.stream(requiredRoles)
                        .filter(r -> r != null && !r.trim().isEmpty())
                        .map(String::trim)
                        .anyMatch(req -> userRoles.contains(req));

                if (!hasAnyRequiredRole) {
                    logger.warn("Forbidden: missing required role. required={}, userRoles={}",
                            Arrays.toString(requiredRoles), userRoles);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"缺少访问所需角色\",\"code\":403}");
                    return false;
                }
            }

            logger.debug("Token validated successfully for request: {} {}", 
                request.getMethod(), request.getRequestURI());
        }
        
        return true;
    }

    /**
     * 从请求头获取token
     * 优先从 "token" 请求头获取，如果没有则从 "Authorization" 请求头获取（支持 Bearer 格式）
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 优先从 token 请求头获取
        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }
        
        // 从 Authorization 请求头获取（支持 Bearer 格式）
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && !authorization.trim().isEmpty()) {
            // 支持 "Bearer <token>" 格式
            if (authorization.startsWith("Bearer ")) {
                return authorization.substring(7).trim();
            }
            return authorization.trim();
        }
        
        return null;
    }

    /**
     * 验证token是否有效
     * 这里提供了简单的示例实现，实际应用中应该：
     * 1. 从数据库或缓存中查询token
     * 2. 验证token是否过期
     * 3. 使用JWT等方式验证token签名
     */
    private boolean isValidToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        
        // 简单示例：检查token是否在有效列表中
        // 实际应用中应该实现更复杂的验证逻辑
        for (String validToken : VALID_TOKENS) {
            if (validToken.equals(token)) {
                return true;
            }
        }
        
        // 这里可以添加JWT验证、数据库查询等逻辑
        // 例如：
        // return jwtTokenProvider.validateToken(token);
        // 或
        // return tokenService.isValidToken(token);
        
        return false;
    }

    /**
     * 从token中解析用户角色集合。
     * 这里提供示例实现：
     * - 对内置演示token映射固定角色
     * - 也支持形如 "roles:ADMIN,DBA,USER" 的简易明文格式
     */
    private Set<String> parseRolesFromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Collections.emptySet();
        }

        String trimmed = token.trim();

        // 简易明文格式：roles:ADMIN,DBA
        if (trimmed.startsWith("roles:")) {
            String rolesPart = trimmed.substring("roles:".length());
            String[] parts = rolesPart.split(",");
            Set<String> roles = new HashSet<>();
            for (String p : parts) {
                String role = p == null ? null : p.trim();
                if (role != null && !role.isEmpty()) {
                    roles.add(role);
                }
            }
            return roles;
        }

        // 演示token到角色的简单映射
        if ("demo-token-123456".equals(trimmed)) {
            return new HashSet<>(Arrays.asList("ADMIN", "DBA"));
        }
        if ("test-token-abcdef".equals(trimmed)) {
            return new HashSet<>(Collections.singletonList("USER"));
        }

        // 默认无角色
        return Collections.emptySet();
    }
}
