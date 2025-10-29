package com.chatbi.service;

import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.model.SemanticSQL;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatbi.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public List<ChatMessage> listMessages(ChatSession session) {
        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    public ChatMessage appendUserMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("user");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content, SemanticSQL semanticSQL, String sqlQuery, java.util.Map<String, Object> executionResult, java.util.Map<String, Object> debugInfo) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        try {
            if (semanticSQL != null) {
                message.setSemanticSql(OBJECT_MAPPER.writeValueAsString(semanticSQL));
            }
            if (sqlQuery != null) {
                message.setSqlQuery(sqlQuery);
            }
            if (executionResult != null) {
                message.setExecutionResult(OBJECT_MAPPER.writeValueAsString(executionResult));
            }
            if (debugInfo != null) {
                message.setDebugInfo(OBJECT_MAPPER.writeValueAsString(debugInfo));
            }
        } catch (Exception ignored) {}
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public void appendExecutionResultToLastAssistant(ChatSession session, java.util.Map<String, Object> executionResult) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("assistant".equals(msg.getRole())) {
                try {
                    if (executionResult != null) {
                        msg.setExecutionResult(OBJECT_MAPPER.writeValueAsString(executionResult));
                    }
                } catch (Exception ignored) {}
                chatMessageRepository.save(msg);
                chatSessionService.touchUpdatedAt(session);
                break;
            }
        }
    }
}


