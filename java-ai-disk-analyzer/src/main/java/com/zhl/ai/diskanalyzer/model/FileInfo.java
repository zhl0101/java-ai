package com.zhl.ai.diskanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    
    /**
     * 文件路径
     */
    private String path;
    
    /**
     * 文件名称
     */
    private String name;
    
    /**
     * 文件大小（字节）
     */
    private long size;
    
    /**
     * 格式化后的文件大小
     */
    private String formattedSize;
    
    /**
     * 最后修改时间
     */
    private LocalDateTime lastModified;
    
    /**
     * 文件类型（扩展名）
     */
    private String fileType;
    
    /**
     * 是否可安全删除
     */
    private boolean canDelete;
    
    /**
     * 删除原因
     */
    private String deleteReason;
}
