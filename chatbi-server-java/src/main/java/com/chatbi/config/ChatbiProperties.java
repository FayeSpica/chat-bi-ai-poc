package com.chatbi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chatbi")
public class ChatbiProperties {
    // Add other chatbi-specific properties here if needed in the future
}