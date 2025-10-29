package com.chatbi.service;

import com.chatbi.model.ChatSession;
import com.chatbi.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    public List<ChatSession> listSessions(String userId) {
        return chatSessionRepository.findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(userId);
    }

    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setArchived(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        return chatSessionRepository.save(session);
    }

    public Optional<ChatSession> getByIdForUser(Long id, String userId) {
        return chatSessionRepository.findByIdAndUserId(id, userId);
    }

    public Optional<ChatSession> getById(Long id) {
        return chatSessionRepository.findById(id);
    }

    public Optional<ChatSession> renameSession(Long id, String userId, String title) {
        Optional<ChatSession> optional = chatSessionRepository.findByIdAndUserId(id, userId);
        optional.ifPresent(s -> {
            s.setTitle(title);
            s.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(s);
        });
        return optional;
    }

    public boolean archiveSession(Long id, String userId) {
        Optional<ChatSession> optional = chatSessionRepository.findByIdAndUserId(id, userId);
        if (optional.isPresent()) {
            ChatSession s = optional.get();
            s.setArchived(true);
            s.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(s);
            return true;
        }
        return false;
    }

    public void touchUpdatedAt(ChatSession session) {
        session.setUpdatedAt(OffsetDateTime.now());
        chatSessionRepository.save(session);
    }
}


