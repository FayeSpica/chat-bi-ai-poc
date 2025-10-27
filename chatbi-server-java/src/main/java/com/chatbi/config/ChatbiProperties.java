package com.chatbi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chatbi")
public class ChatbiProperties {
    private Ollama ollama = new Ollama();

    public static class Ollama {
        private String baseUrl = "http://192.168.31.230:11434";
        private String model = "qwen2.5:7b";
        private int timeout = 120;

        // Getters and Setters
        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }

    // Getters and Setters
    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }
}