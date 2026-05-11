package com.zhl.ai.javaaiday1.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO 配置类
 * @createTime 2026年05月11日 15:33
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory() {
        // 使用内存存储对话历史，可以设置最大历史条数
        return  MessageWindowChatMemory.builder().maxMessages(30).build();
    }
}
