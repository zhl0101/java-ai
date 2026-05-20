package com.zhl.ai.javaaiday1.controller;

import com.zhl.ai.javaaiday1.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final RagService ragService;

    // 通过构造函数注入 ChatClient
    /*public AiDemoController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder.defaultSystem("你是一个资深开发专家")
                .build();
        this.chatMemory = chatMemory;
    }*/

    // 直接注入已经配置好工具的 ChatClient Bean
    public AiDemoController(ChatClient chatClientTool, ChatMemory chatMemory,
                            RagService ragService) {
        this.chatClient = chatClientTool;
        this.chatMemory = chatMemory;
        this.ragService = ragService;
    }

    // 使用工具接口
    @GetMapping("/ai/function")
    public String chatWithFunction (@RequestParam String msg){
        return chatClient.prompt()
                .user(msg)
                .call()
                .content();
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

    /**
     * RAG 知识库问答接口（核心）
     * 工作流程：检索文档 → 构建上下文 → 调用大模型
     */

    @GetMapping("/ai/rag")
    public String ragChat(@RequestParam String question) {
        // 1️⃣ 检索阶段：从向量数据库中找到与问题最相似的 3 个文档片段
        List<Document> relevantDocs = ragService.retrieveRelevantDocuments(question, 3);

        if (relevantDocs.isEmpty()) {
            return "未在知识库中找到相关信息，请先上传文档或调整提问方式。";
        }

        // 2️⃣ 增强阶段：将检索到的文档片段拼接成上下文（附上来源引用）
        String context = relevantDocs.stream()
                .map(doc -> "【参考资料】\n" + doc.getText())
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = """
                你是一个基于企业知识库的智能问答助手。
                请在回答时：
                1. 严格基于以下【参考资料】提供的内容作为事实依据，不要额外编造知识库中不存在的细节。
                2. 引用参考资料时，标注「根据知识库记载...」。
                3. 如果参考资料不足以回答问题，请明确说明「根据现有知识库无法确定」，然后结合你自己的知识给出辅助性参考。
                4. 回答深度适中、语言干练，优先保障准确性。

                【参考资料】
                %s
                """.formatted(context);

        // 3️⃣ 生成阶段：调用大模型，结合问题与上下文生成最终答案
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    /**
     * 查看当前向量数据库中的文档片段（调试接口）
     */
    @GetMapping("/ai/rag/stats")
    public String ragStats(@RequestParam(required = false, defaultValue = "5") int limit) {
        // 从知识库中获取前 N 个文档片段
        List<Document> documents = ragService.getRecentDocuments(limit);
        
        if (documents.isEmpty()) {
            return "RAG 服务已启动，但向量数据库中暂无文档。请先上传文档或检查初始化流程。";
        }
        
        // 构建返回信息
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("RAG 服务状态：正常运行\n"));
        sb.append(String.format("返回文档数量：%d / %d\n", documents.size(), limit));
        sb.append("=".repeat(50)).append("\n\n");
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            sb.append(String.format("【文档 %d】\n", i + 1));
            sb.append(String.format("内容预览：%s...\n", 
                    doc.getText().substring(0, Math.min(200, doc.getText().length()))));
            sb.append(String.format("元数据：%s\n", doc.getMetadata()));
            sb.append("-".repeat(50)).append("\n\n");
        }
        
        return sb.toString();
    }

}
