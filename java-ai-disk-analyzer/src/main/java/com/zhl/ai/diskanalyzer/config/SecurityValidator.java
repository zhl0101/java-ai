package com.zhl.ai.diskanalyzer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 安全配置验证器
 */
@Slf4j
@Component
public class SecurityValidator {
    
    @Autowired
    private DiskAnalyzerProperties properties;
    
    @PostConstruct
    public void validateConfiguration() {
        log.info("=== 磁盘分析器安全配置验证 ===");
        
        // 验证安全路径配置
        List<String> safePaths = properties.getSafePaths();
        if (safePaths == null || safePaths.isEmpty()) {
            log.warn("警告：未配置安全路径白名单，将使用默认配置");
        } else {
            log.info("已配置的安全路径: {}", safePaths);
        }
        
        // 验证阻止路径配置
        List<String> blockedPaths = properties.getBlockedPaths();
        if (blockedPaths == null || blockedPaths.isEmpty()) {
            log.warn("警告：未配置阻止路径列表，系统将使用内置的系统关键目录保护");
        } else {
            log.info("已配置的阻止路径: {}", blockedPaths);
        }
        
        // 验证阈值配置
        int largeFileThreshold = properties.getLargeFileThreshold();
        if (largeFileThreshold <= 0) {
            log.warn("警告：大文件阈值配置无效({} MB)，将使用默认值 100 MB", largeFileThreshold);
        } else {
            log.info("大文件阈值: {} MB", largeFileThreshold);
        }
        
        int tempFileDays = properties.getTempFileDays();
        if (tempFileDays <= 0) {
            log.warn("警告：临时文件天数阈值配置无效({} 天)，将使用默认值 7 天", tempFileDays);
        } else {
            log.info("临时文件天数阈值: {} 天", tempFileDays);
        }
        
        log.info("=== 安全配置验证完成 ===");
    }
    
    /**
     * 验证路径是否安全
     */
    public boolean isPathSafe(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        
        // 检查是否为阻止路径
        List<String> blockedPaths = properties.getBlockedPaths();
        if (blockedPaths != null) {
            for (String blocked : blockedPaths) {
                if (path.equals(blocked) || path.startsWith(blocked + "/")) {
                    log.warn("尝试访问被阻止的路径: {}", path);
                    return false;
                }
            }
        }
        
        // 检查是否在安全路径白名单中
        List<String> safePaths = properties.getSafePaths();
        if (safePaths != null && !safePaths.isEmpty()) {
            boolean isSafe = safePaths.stream().anyMatch(safePath -> 
                    path.startsWith(safePath)
            );
            
            if (!isSafe) {
                log.warn("尝试访问不在白名单中的路径: {}", path);
                return false;
            }
        }
        
        return true;
    }
    
    public DiskAnalyzerProperties getProperties() {
        return properties;
    }
}
