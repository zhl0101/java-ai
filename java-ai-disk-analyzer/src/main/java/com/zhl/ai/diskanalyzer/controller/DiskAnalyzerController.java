package com.zhl.ai.diskanalyzer.controller;

import com.zhl.ai.diskanalyzer.config.SecurityValidator;
import com.zhl.ai.diskanalyzer.model.CleanupSuggestion;
import com.zhl.ai.diskanalyzer.model.DirectoryInfo;
import com.zhl.ai.diskanalyzer.model.DiskAnalysisResult;
import com.zhl.ai.diskanalyzer.model.FileInfo;
import com.zhl.ai.diskanalyzer.service.CleanupAnalyzerService;
import com.zhl.ai.diskanalyzer.service.DiskScannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 磁盘分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/disk")
@CrossOrigin(origins = "*")
public class DiskAnalyzerController {
    
    @Autowired
    private DiskScannerService diskScannerService;
    
    @Autowired
    private CleanupAnalyzerService cleanupAnalyzerService;
    
    @Autowired
    private SecurityValidator securityValidator;
    
    /**
     * 分析指定目录
     */
    @GetMapping("/analyze")
    public ResponseEntity<DiskAnalysisResult> analyzeDirectory(
            @RequestParam(defaultValue = "/tmp") String path) {
        try {
            log.info("收到磁盘分析请求，路径: {}", path);
            
            // 验证路径安全性
            if (!securityValidator.isPathSafe(path)) {
                return ResponseEntity.badRequest().build();
            }
            
            // 扫描目录
            DirectoryInfo rootInfo = diskScannerService.scanDirectory(path);
            List<DirectoryInfo> subDirs = diskScannerService.getSubDirectories(path);
            List<FileInfo> largeFiles = diskScannerService.getLargeFiles(path);
            
            // 分析清理建议
            List<CleanupSuggestion> suggestions = cleanupAnalyzerService.analyzeCleanupSuggestions(path);
            
            // 构建结果
            DiskAnalysisResult result = DiskAnalysisResult.builder()
                    .rootPath(path)
                    .totalSize(rootInfo.getSize())
                    .formattedTotalSize(rootInfo.getFormattedSize())
                    .fileCount(rootInfo.getFileCount())
                    .directoryCount(subDirs.size())
                    .subDirectories(subDirs)
                    .largeFiles(largeFiles)
                    .suggestions(suggestions)
                    .build();
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            log.error("磁盘分析失败", e);
            return ResponseEntity.internalServerError().build();
        } catch (IllegalArgumentException e) {
            log.warn("无效的路径参数: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取目录结构
     */
    @GetMapping("/directories")
    public ResponseEntity<List<DirectoryInfo>> getDirectories(
            @RequestParam(defaultValue = "/tmp") String path) {
        try {
            if (!securityValidator.isPathSafe(path)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<DirectoryInfo> directories = diskScannerService.getSubDirectories(path);
            return ResponseEntity.ok(directories);
            
        } catch (IOException e) {
            log.error("获取目录列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取大文件列表
     */
    @GetMapping("/large-files")
    public ResponseEntity<List<FileInfo>> getLargeFiles(
            @RequestParam(defaultValue = "/tmp") String path) {
        try {
            if (!securityValidator.isPathSafe(path)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<FileInfo> largeFiles = diskScannerService.getLargeFiles(path);
            return ResponseEntity.ok(largeFiles);
            
        } catch (IOException e) {
            log.error("获取大文件列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取清理建议
     */
    @GetMapping("/cleanup-suggestions")
    public ResponseEntity<List<CleanupSuggestion>> getCleanupSuggestions(
            @RequestParam(defaultValue = "/tmp") String path) {
        try {
            if (!securityValidator.isPathSafe(path)) {
                return ResponseEntity.badRequest().build();
            }
            
            List<CleanupSuggestion> suggestions = cleanupAnalyzerService.analyzeCleanupSuggestions(path);
            return ResponseEntity.ok(suggestions);
            
        } catch (IOException e) {
            log.error("获取清理建议失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取安全路径列表
     */
    @GetMapping("/safe-paths")
    public ResponseEntity<List<String>> getSafePaths() {
        return ResponseEntity.ok(securityValidator.getProperties().getSafePaths());
    }
}
