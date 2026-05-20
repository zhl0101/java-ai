package com.zhl.ai.diskanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目录信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryInfo {
    
    /**
     * 目录路径
     */
    private String path;
    
    /**
     * 目录名称
     */
    private String name;
    
    /**
     * 目录大小（字节）
     */
    private long size;
    
    /**
     * 格式化后的目录大小
     */
    private String formattedSize;
    
    /**
     * 文件数量
     */
    private int fileCount;
    
    /**
     * 是否可安全清理
     */
    private boolean canCleanup;
    
    /**
     * 清理原因
     */
    private String cleanupReason;
}
