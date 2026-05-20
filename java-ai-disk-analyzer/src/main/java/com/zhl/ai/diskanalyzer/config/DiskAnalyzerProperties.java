package com.zhl.ai.diskanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 磁盘分析器配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "disk.analyzer")
public class DiskAnalyzerProperties {
    
    /**
     * 安全路径白名单
     */
    private List<String> safePaths;
    
    /**
     * 禁止操作的路径
     */
    private List<String> blockedPaths;
    
    /**
     * 大文件阈值（MB）
     */
    private int largeFileThreshold = 100;
    
    /**
     * 临时文件天数阈值
     */
    private int tempFileDays = 7;
    
    public List<String> getSafePaths() {
        return safePaths;
    }
    
    public void setSafePaths(List<String> safePaths) {
        this.safePaths = safePaths;
    }
    
    public List<String> getBlockedPaths() {
        return blockedPaths;
    }
    
    public void setBlockedPaths(List<String> blockedPaths) {
        this.blockedPaths = blockedPaths;
    }
    
    public int getLargeFileThreshold() {
        return largeFileThreshold;
    }
    
    public void setLargeFileThreshold(int largeFileThreshold) {
        this.largeFileThreshold = largeFileThreshold;
    }
    
    public int getTempFileDays() {
        return tempFileDays;
    }
    
    public void setTempFileDays(int tempFileDays) {
        this.tempFileDays = tempFileDays;
    }
}
