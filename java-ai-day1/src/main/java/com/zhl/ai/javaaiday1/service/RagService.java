package com.zhl.ai.javaaiday1.service;

import com.alibaba.fastjson.JSON;
import com.zhl.ai.javaaiday1.loader.DocumentLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO
 * @createTime 2026年05月13日 17:11
 */
@Slf4j
@Service
public class RagService {

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private DocumentLoader documentLoader;
    /**
     * 在应用启动时自动运行：将文档加载、分块、向量化后存入向量数据库
     * ✅ 这是最重要的环节，缺失则后续检索无数据可用
     */

    @PostConstruct
    public void initVectorStore(){
        log.info("========== ETL Pipeline 启动 ==========");
        log.info("1️⃣ 从数据源加载原始文档 (Extract)");
        List<Document> documents = documentLoader.loadDocuments();
        log.info("2️⃣ 将长文档切分成语义完整的文本块 (Transform)");

        // TokenTextSplitter 智能分块器，基于 token 数量切分，自动在句子边界处截断
        // chunkSize：每个分块的最大 token 数（默认 800）
        // minChunkSizeChars：最小字符数（默认 350）
        // overlap：相邻分块的重叠 token 数，防止语义割裂
        TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 10000, true);
        List<Document> chunks = splitter.apply(documents);
        log.info("共切分为 {} 个文本块", chunks.size());

        log.info("3️⃣ 将文本块向量化并存入向量数据库 (Load),内容：{}", JSON.toJSONString(chunks));
        // vectorStore.add() 内部自动调用 embeddingModel.embed() 生成向量并存储
        vectorStore.add(chunks);
        log.info("✅ ETL Pipeline 完成！向量数据库已就绪，检索随时可用");
    }

    /**
     * 从向量数据库中检索与用户问题最相关的文档片段
     * @param query 用户问题
     * @param topK 最多返回 K 个相似片段
     * @return 相关文档片段列表（未经排序的原始检索结果）
     */

    public List<Document> retrieveRelevantDocuments(String query, int topK) {
        // SimpleVectorStore.similaritySearch() 内部自动将 query 向量化,然后计算余弦相似度
        log.info(
                "检索与问题最相关的 {} 个文档片段，问题：{}", topK, query
        );
        // log.info("正在检索...{}", JSON.toJSONString(vectorStore));
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
    }

    /**
     * 获取向量数据库中的前 N 个文档片段（用于调试）
     * @param limit 最多返回的文档数量
     * @return 文档片段列表
     */
    public List<Document> getRecentDocuments(int limit) {
        // 使用空查询获取所有文档，然后限制返回数量
        // SimpleVectorStore 不支持直接获取所有文档，这里用一个通用关键词检索
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(" ")  // 空字符串查询，匹配所有文档
                        .topK(limit)
                        .build()
        );
    }

}
