package com.zhl.ai.javaaiday1.config;

import com.zhl.ai.javaaiday1.service.CalculatorToolService;
import com.zhl.ai.javaaiday1.service.TimeToolService;
import com.zhl.ai.javaaiday1.service.WeatherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO
 * @createTime 2026年05月11日 16:49
 */
@Configuration
public class ToolConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 TimeToolService timeTool,
                                 CalculatorToolService calculatorTool,
                                 WeatherService weather){
        // 将两个工具服务中的 @Tool 方法转换为 ToolCallback 数组
        ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(timeTool, calculatorTool, weather)
                .build().getToolCallbacks();
        return builder.defaultToolCallbacks(toolCallbacks).build();

    }
}
