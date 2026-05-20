package com.zhl.ai.javaaiday1.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO 向量数据库配置
 * @createTime 2026年05月13日 17:05
 */
@Configuration
public class VectorStoreConfig {
    /**
     * SimpleVectorStore 是 Spring AI 提供的内存向量数据库实现。
     * ✅ 优点：零依赖、无需部署、最适合开发和测试
     * ⚠️ 缺点：应用重启后数据丢失，生产环境可替换为 PGVector / Milvus / Elasticsearch
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
