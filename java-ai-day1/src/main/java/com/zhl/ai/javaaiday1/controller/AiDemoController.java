package com.zhl.ai.javaaiday1.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO
 * @createTime 2026年05月08日 10:23
 */
@RestController
public class AiDemoController {

    private final ChatClient chatClient;
    // 在 Controller 中注入 ChatMemory
    private final ChatMemory chatMemory;

    // 通过构造函数注入 ChatClient
    /*public AiDemoController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder.defaultSystem("你是一个资深开发专家")
                .build();
        this.chatMemory = chatMemory;
    }*/

    // 直接注入已经配置好工具的 ChatClient Bean
    public AiDemoController(ChatClient chatClientTool, ChatMemory chatMemory) {
        this.chatClient = chatClientTool;
        this.chatMemory = chatMemory;
    }

    // 同步接口
    @GetMapping("/ai/chat")
    public String chat(@RequestParam String msg) {
        return chatClient.prompt()
                .user(msg)
                .call()
                .content();
    }

    // 流式接口
    @GetMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String msg){
        return chatClient.prompt()
                .user(msg)
                .stream()
                .content();
    }

    //使用系统提示词（强制角色）
    @GetMapping("/ai/java-expert")
    public String javaExpert(@RequestParam String question) {
        SystemMessage systemMessage = new SystemMessage("你是一位资深Java开发专家");
        UserMessage userMessage = new UserMessage(question);
        Prompt prompt = new Prompt(systemMessage, userMessage);
        return chatClient.prompt(prompt).call().content();
    }

    // 流式接口 + 系统提示词组合（进阶）
    @GetMapping(value = "/ai/stream-expert", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamExpert(@RequestParam String question) {
        SystemMessage systemMsg = new SystemMessage("你是一位资深Java开发专家");
        UserMessage userMsg = new UserMessage(question);
        Prompt prompt = new Prompt(systemMsg, userMsg);
        return chatClient.prompt(prompt).stream().content();
    }

    // 使用提示模板（动态组装 prompt）
    @GetMapping("/ai/translate")
    public String translate(@RequestParam String text,
                            @RequestParam(defaultValue = "中文") String targetLanguage) {
        String templateText = """
            请将以下文本翻译成{language}，并且只输出翻译结果，不要添加任何解释。
            文本：{text}
            """;
        PromptTemplate template = new PromptTemplate(templateText);
        Map<String, Object> params = Map.of("language", targetLanguage, "text", text);
        Prompt prompt = template.create(params);
        return chatClient.prompt(prompt).call().content();
    }

    // 使用提示模板（动态组装 prompt） 诗词
    @GetMapping("/ai/poet")
    public String poet(@RequestParam String text) {
        String templateText = """
            请根据以下文本的内容以李白的风格，编写一首诗词，并且只输出翻译结果，不要添加任何解释。
            文本：{text}
            """;
        PromptTemplate template = new PromptTemplate(templateText);
        Map<String, Object> params = Map.of("text", text);
        Prompt prompt = template.create(params);
        return chatClient.prompt(prompt).call().content();
    }

    // 带记忆的流式接口
    @GetMapping(value = "/ai/chat/stream/memory", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatWithMemory(@RequestParam String msg,
                                             @RequestParam(defaultValue = "default") String sessionId){

        // 创建 advisor，每个 session 独立记忆
        MessageChatMemoryAdvisor memoryAdvisor =  MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId).build();
        return chatClient.prompt()
                .user(msg)
                .advisors(memoryAdvisor)   // 添加记忆顾问
                .stream()
                .content();

    }

}
