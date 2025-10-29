package com.chatbi.interceptor;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.exception.TokenValidationException;
import com.chatbi.model.UserToken;
import com.chatbi.service.UserWhitelistService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String TOKEN_HEADER = "Login-Token";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserWhitelistService userWhitelistService;

    public TokenInterceptor(UserWhitelistService userWhitelistService) {
        this.userWhitelistService = userWhitelistService;
    }

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

            // 白名单校验：仅允许白名单用户访问
            // 优先解析JSON格式的token为UserToken
            UserToken userToken = parseUserTokenFromJson(token);
            if (userToken == null || userToken.getUserId() == null) {
                logger.warn("Forbidden: invalid token format for request: {} {}", request.getMethod(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"用户不在白名单（需提供userId）\",\"code\":403}");
                return false;
            }
            
            boolean allowed = userWhitelistService.isWhitelisted(userToken.getUserId());
            if (!allowed) {
                logger.warn("Forbidden: user not in whitelist for request: {} {}", request.getMethod(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"用户不在白名单（需提供userId）\",\"code\":403}");
                return false;
            }

            // 角色校验：当注解中配置了 roleNames 时，要求用户至少具备其中一个角色
            String[] requiredRoles = enableAuth.roleNames();
            if (requiredRoles != null && requiredRoles.length > 0) {

                Set<String> userRoles = userToken.getRoleNames();
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
     * 优先从 "token" 请求头获取，然后从 "Login-Token" 请求头获取，最后从 "Authorization" 请求头获取（支持 Bearer 格式）
     * Login-Token 的内容是 UserToken 的 JSON 格式的 base64 编码
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 优先从 token 请求头获取
        String token = request.getHeader(TOKEN_HEADER);
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }
        
        return null;
    }

    /**
     * 将token解析为UserToken对象。
     * 支持两种格式：
     * 1. base64编码的JSON字符串（Login-Token格式）：先base64解码，再JSON解析
     * 2. 直接JSON字符串：直接JSON解析
     * 
     * 兼容以下字段名：
     * - userId / uid
     * - userName / username / name
     * - roleNames (数组或逗号分隔字符串) / roles
     */
    public static UserToken parseUserTokenFromJson(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = token.trim();
        String jsonString = null;
        
        // 判断是否是base64编码的JSON
        // base64编码的字符串通常不包含 { 和 }，且长度较长
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            // 可能是base64编码，尝试解码
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(trimmed);
                jsonString = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                // 验证解码后的内容是否是JSON
                if (!jsonString.trim().startsWith("{") && !jsonString.trim().startsWith("[")) {
                    // 解码后不是JSON，可能不是base64编码，使用原始字符串
                    jsonString = trimmed;
                }
            } catch (IllegalArgumentException e) {
                // base64解码失败，使用原始字符串
                jsonString = trimmed;
            }
        } else {
            // 直接是JSON字符串
            jsonString = trimmed;
        }

        try {
            return OBJECT_MAPPER.readValue(jsonString, UserToken.class);
        } catch (JsonProcessingException e) {
            // 不是合法JSON，忽略
            logger.debug("Failed to parse token as JSON: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Error parsing token: {}", e.getMessage());
            return null;
        }
    }
}
