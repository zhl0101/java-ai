package com.zhl.ai.diskanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 磁盘分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskAnalysisResult {
    
    /**
     * 根路径
     */
    private String rootPath;
    
    /**
     * 总大小（字节）
     */
    private long totalSize;
    
    /**
     * 格式化后的总大小
     */
    private String formattedTotalSize;
    
    /**
     * 文件总数
     */
    private int fileCount;
    
    /**
     * 目录总数
     */
    private int directoryCount;
    
    /**
     * 子目录信息列表
     */
    private List<DirectoryInfo> subDirectories;
    
    /**
     * 大文件列表
     */
    private List<FileInfo> largeFiles;
    
    /**
     * 清理建议
     */
    private List<CleanupSuggestion> suggestions;
}
