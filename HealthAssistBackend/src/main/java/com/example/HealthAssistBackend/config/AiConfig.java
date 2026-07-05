package com.example.HealthAssistBackend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;

/**
 * AI configuration for Spring AI ChatClient and VectorStore.
 */
@Configuration
public class AiConfig {

    @Value("${healthassist.vector-store.path:./data/vector-store.json}")
    private String vectorStorePath;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        File vectorStoreFile = new File(vectorStorePath);
        if (vectorStoreFile.exists()) {
            store.load(vectorStoreFile);
        } else {
            vectorStoreFile.getParentFile().mkdirs();
        }

        return store;
    }
}
