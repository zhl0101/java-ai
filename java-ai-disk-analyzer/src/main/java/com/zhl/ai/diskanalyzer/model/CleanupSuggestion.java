package com.zhl.ai.diskanalyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 清理建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupSuggestion {
    
    /**
     * 建议类型：FILE-文件, DIRECTORY-目录
     */
    private String type;
    
    /**
     * 路径
     */
    private String path;
    
    /**
     * 可释放空间（字节）
     */
    private long reclaimableSpace;
    
    /**
     * 格式化后的可释放空间
     */
    private String formattedReclaimableSpace;
    
    /**
     * 建议优先级：HIGH, MEDIUM, LOW
     */
    private String priority;
    
    /**
     * 建议描述
     */
    private String description;
    
    /**
     * 风险提示
     */
    private String riskWarning;
}
