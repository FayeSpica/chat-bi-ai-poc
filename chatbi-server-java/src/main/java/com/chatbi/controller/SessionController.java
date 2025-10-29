package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.model.UserToken;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import com.chatbi.service.UserWhitelistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private UserWhitelistService userWhitelistService;

    private String requireUserId(String loginToken) {
        UserToken parsed = TokenInterceptor.parseUserTokenFromJson(loginToken);
        if (parsed == null || parsed.getUserId() == null) {
            throw new IllegalArgumentException("缺少有效的用户标识");
        }
        return parsed.getUserId();
    }

    @GetMapping
    @EnableAuth
    public ResponseEntity<List<ChatSession>> listSessions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        String userId = requireUserId(loginToken);
        return ResponseEntity.ok(chatSessionService.listSessions(userId));
    }

    @PostMapping
    @EnableAuth
    public ResponseEntity<ChatSession> createSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @RequestBody(required = false) Map<String, String> body) {
        String userId = requireUserId(loginToken);
        String title = body != null ? body.getOrDefault("title", null) : null;
        ChatSession created = chatSessionService.createSession(userId, title);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{id}")
    @EnableAuth
    public ResponseEntity<?> renameSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        String userId = requireUserId(loginToken);
        String title = body.get("title");
        Optional<ChatSession> updated = chatSessionService.renameSession(id, userId, title);
        return updated.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "会话不存在")));
    }

    @DeleteMapping("/{id}")
    @EnableAuth
    public ResponseEntity<?> deleteSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id) {
        String userId = requireUserId(loginToken);
        String role = userWhitelistService.getUserRole(userId);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "只有ADMIN可以删除会话"));
        }
        boolean ok = chatSessionService.archiveSession(id, userId);
        if (ok) {
            return ResponseEntity.ok(Map.of("message", "会话已删除"));
        } else {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
    }

    @GetMapping("/{id}/messages")
    @EnableAuth
    public ResponseEntity<?> listMessages(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id) {
        String userId = requireUserId(loginToken);
        Optional<ChatSession> optional = chatSessionService.getByIdForUser(id, userId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
        List<ChatMessage> messages = chatMessageService.listMessages(optional.get());
        return ResponseEntity.ok(messages);
    }
}


