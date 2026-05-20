package com.zhl.ai.javaaiday1.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhl
 * @version 1.0.0
 * @Description 文档加载器，支持加载 classpath:documents/ 目录下的多个文档
 * @createTime 2026年05月12日 10:20
 */
@Component
@Slf4j
public class DocumentLoader {

    // 匹配 documents 目录下所有文件（支持 PDF/Word/Excel/PPT/TXT 等 20+ 格式）
    @Value("${document.path:classpath:documents/**}")
    private String documentPattern;

    /**
     * 加载 documents 目录下的所有文档
     */
    public List<Document> loadDocuments() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(documentPattern);
        } catch (IOException e) {
            log.error("扫描文档目录失败，pattern: {}", documentPattern, e);
            return List.of();
        }

        if (resources.length == 0) {
            log.warn("未找到任何文档，pattern: {}", documentPattern);
            return List.of();
        }

        log.info("共发现 {} 个文档文件，开始加载...", resources.length);
        List<Document> allDocuments = new ArrayList<>();

        for (Resource resource : resources) {
            // 跳过目录本身（只处理文件）
            try {
                if (!resource.isReadable()) {
                    log.debug("跳过不可读资源: {}", resource.getFilename());
                    continue;
                }
            } catch (Exception e) {
                log.debug("跳过资源: {}", resource.getFilename());
                continue;
            }

            log.info("正在加载文档: {}", resource.getFilename());
            try {
                // TikaDocumentReader 自动识别文件格式
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> docs = reader.get();
                log.info("文档 [{}] 加载完成，共 {} 个片段", resource.getFilename(), docs.size());
                for (Document document : docs) {
                    log.debug("文档片段内容预览: {}...",
                            document.getText().substring(0, Math.min(30, document.getText().length())));
                }
                allDocuments.addAll(docs);
            } catch (Exception e) {
                log.error("文档 [{}] 加载失败，已跳过", resource.getFilename(), e);
            }
        }

        log.info("所有文档加载完成，共 {} 个文档片段", allDocuments.size());
        return allDocuments;
    }

}
