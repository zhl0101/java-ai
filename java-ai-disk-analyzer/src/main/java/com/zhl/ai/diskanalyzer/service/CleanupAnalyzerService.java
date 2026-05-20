package com.zhl.ai.diskanalyzer.service;

import com.zhl.ai.diskanalyzer.model.CleanupSuggestion;
import com.zhl.ai.diskanalyzer.model.DirectoryInfo;
import com.zhl.ai.diskanalyzer.model.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 智能分析服务 - 识别可删除的文件和目录
 */
@Slf4j
@Service
public class CleanupAnalyzerService {
    
    @Value("${disk.analyzer.temp-file-days:7}")
    private int tempFileDays;
    
    @Value("${disk.analyzer.large-file-threshold:100}")
    private int largeFileThresholdMB;
    
    @Value("${disk.analyzer.thread-pool-size:4}")
    private int threadPoolSize;
    
    // 线程池用于并行处理深目录
    private ExecutorService executorService;
    
    // 常见的临时文件扩展名
    private static final Set<String> TEMP_EXTENSIONS = Set.of(
            "tmp", "temp", "bak", "backup", "old", "swp", "cache", 
            "log", "logs", "pid", "lock"
    );
    
    // 常见的可清理目录模式
    private static final List<String> CLEANUP_DIR_PATTERNS = Arrays.asList(
            "tmp", "temp", "cache", "logs", "build", "target", 
            ".git", "node_modules", "__pycache__", ".DS_Store"
    );
    
    // 系统关键目录（禁止删除）
    private static final Set<String> BLOCKED_PATHS = Set.of(
            "/", "/bin", "/sbin", "/usr", "/etc", "/boot", 
            "/proc", "/sys", "/dev", "/lib", "/lib64"
    );
    
    /**
     * 分析并生成清理建议
     */
    public List<CleanupSuggestion> analyzeCleanupSuggestions(String path) throws IOException {
        // 初始化线程池
        executorService = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("disk-analyzer-" + thread.getId());
            return thread;
        });
        
        List<CleanupSuggestion> suggestions = new CopyOnWriteArrayList<>();
        List<java.util.concurrent.Future<List<CleanupSuggestion>>> futures = new ArrayList<>();
        
        try {
            // 并行执行各个分析任务
            futures.add(executorService.submit(() -> analyzeTempFiles(path)));
            futures.add(executorService.submit(() -> analyzeLargeFiles(path)));
            futures.add(executorService.submit(() -> analyzeLogFiles(path)));
            futures.add(executorService.submit(() -> analyzeCacheDirectories(path)));
            futures.add(executorService.submit(() -> analyzeBuildArtifacts(path)));
            
            // 收集所有结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    List<CleanupSuggestion> result = futures.get(i).get(5, TimeUnit.MINUTES);
                    if (result != null) {
                        suggestions.addAll(result);
                    }
                } catch (InterruptedException e) {
                    log.warn("分析任务被中断: 任务{}", i + 1);
                    Thread.currentThread().interrupt(); // 恢复中断状态
                    break; // 停止等待其他任务
                } catch (java.util.concurrent.ExecutionException e) {
                    log.error("分析任务执行失败: 任务{} - {}", i + 1, e.getCause().getMessage());
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("分析任务超时: 任务{}", i + 1);
                }
            }
        } finally {
            // 优雅关闭线程池
            shutdownExecutor();
        }
        
        // 按优先级排序
        suggestions.sort((a, b) -> {
            int priorityOrder = getPriorityOrder(a.getPriority()) - getPriorityOrder(b.getPriority());
            if (priorityOrder != 0) {
                return priorityOrder;
            }
            return Long.compare(b.getReclaimableSpace(), a.getReclaimableSpace());
        });
        
        log.info("清理分析完成，共生成 {} 条建议", suggestions.size());
        return suggestions;
    }
    
    /**
     * 安全关闭线程池
     */
    private void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池未在30秒内正常关闭，强制关闭");
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("线程池强制关闭失败");
                    }
                }
            } catch (InterruptedException e) {
                log.warn("等待线程池关闭时被中断");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 分析临时文件
     */
    private List<CleanupSuggestion> analyzeTempFiles(String path) throws IOException {
        List<CleanupSuggestion> suggestions = new CopyOnWriteArrayList<>();
        Path rootPath = Paths.get(path);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(tempFileDays);
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (!Files.isReadable(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        String ext = getFileExtension(file.getFileName().toString());
                        if (!TEMP_EXTENSIONS.contains(ext.toLowerCase())) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        LocalDateTime lastModified = LocalDateTime.ofInstant(
                                attrs.lastModifiedTime().toInstant(), 
                                ZoneId.systemDefault()
                        );
                        
                        if (!lastModified.isBefore(cutoffDate)) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        String dir = file.getParent().toString();
                        if (isBlockedPath(dir)) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        long size = attrs.size();
                        if (size > 0) {
                            DiskScannerService scanner = new DiskScannerService();
                            suggestions.add(CleanupSuggestion.builder()
                                    .type("FILE")
                                    .path(file.toString())
                                    .reclaimableSpace(size)
                                    .formattedReclaimableSpace(scanner.formatFileSize(size))
                                    .priority("HIGH")
                                    .description(String.format("临时文件：超过%d天未修改", tempFileDays))
                                    .riskWarning("请确认这些临时文件不再需要后再删除")
                                    .build());
                        }
                    } catch (Exception e) {
                        log.debug("处理文件时出错，跳过: {} - {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!Files.isReadable(dir)) {
                        log.debug("跳过无权限的目录: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("访问文件失败，跳过: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("分析临时文件时发生错误: {}", e.getMessage(), e);
            throw e;
        }
        
        return suggestions;
    }
    
    /**
     * 分析大文件
     */
    private List<CleanupSuggestion> analyzeLargeFiles(String path) throws IOException {
        List<CleanupSuggestion> suggestions = new CopyOnWriteArrayList<>();
        Path rootPath = Paths.get(path);
        long thresholdBytes = (long) largeFileThresholdMB * 1024 * 1024;
        DiskScannerService scanner = new DiskScannerService();
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (!Files.isReadable(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        long size = attrs.size();
                        if (size <= thresholdBytes || isBlockedPath(file.toString())) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        LocalDateTime lastModified = LocalDateTime.ofInstant(
                                attrs.lastModifiedTime().toInstant(), 
                                ZoneId.systemDefault()
                        );
                        
                        long daysSinceModified = ChronoUnit.DAYS.between(lastModified, LocalDateTime.now());
                        String priority = daysSinceModified > 30 ? "HIGH" : "MEDIUM";
                        
                        suggestions.add(CleanupSuggestion.builder()
                                .type("FILE")
                                .path(file.toString())
                                .reclaimableSpace(size)
                                .formattedReclaimableSpace(scanner.formatFileSize(size))
                                .priority(priority)
                                .description(String.format("大文件：%.2f MB，最后修改于%d天前", 
                                        size / (1024.0 * 1024), daysSinceModified))
                                .riskWarning("删除前请确认文件内容已备份或不再需要")
                                .build());
                    } catch (Exception e) {
                        log.debug("处理文件时出错，跳过: {} - {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!Files.isReadable(dir)) {
                        log.debug("跳过无权限的目录: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("访问文件失败，跳过: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("分析大文件时发生错误: {}", e.getMessage(), e);
            throw e;
        }
        
        return suggestions;
    }
    
    /**
     * 分析日志文件
     */
    private List<CleanupSuggestion> analyzeLogFiles(String path) throws IOException {
        List<CleanupSuggestion> suggestions = new CopyOnWriteArrayList<>();
        Path rootPath = Paths.get(path);
        DiskScannerService scanner = new DiskScannerService();
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                private final java.util.Map<String, List<Path>> logFilesByDir = new ConcurrentHashMap<>();
                
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (!Files.isReadable(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        String name = file.getFileName().toString().toLowerCase();
                        if (!(name.endsWith(".log") || name.endsWith(".out") || name.endsWith(".err"))) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        String dir = file.getParent().toString();
                        if (isBlockedPath(dir)) {
                            return FileVisitResult.CONTINUE;
                        }
                        
                        logFilesByDir.computeIfAbsent(dir, k -> new CopyOnWriteArrayList<>()).add(file);
                    } catch (Exception e) {
                        log.debug("处理文件时出错，跳过: {} - {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!Files.isReadable(dir)) {
                        log.debug("跳过无权限的目录: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (exc != null) {
                        log.debug("访问目录后处理失败: {} - {}", dir, exc.getMessage());
                        return FileVisitResult.CONTINUE;
                    }
                    
                    try {
                        List<Path> files = logFilesByDir.get(dir.toString());
                        if (files != null && !files.isEmpty()) {
                            long totalSize = files.stream()
                                    .mapToLong(f -> {
                                        try {
                                            return Files.size(f);
                                        } catch (IOException e) {
                                            return 0;
                                        }
                                    })
                                    .sum();
                            
                            if (totalSize > 10 * 1024 * 1024) { // 大于10MB
                                suggestions.add(CleanupSuggestion.builder()
                                        .type("DIRECTORY")
                                        .path(dir.toString())
                                        .reclaimableSpace(totalSize)
                                        .formattedReclaimableSpace(scanner.formatFileSize(totalSize))
                                        .priority("MEDIUM")
                                        .description(String.format("日志文件目录：%d个日志文件", files.size()))
                                        .riskWarning("建议先压缩归档重要日志，再删除旧日志")
                                        .build());
                            }
                        }
                    } catch (Exception e) {
                        log.debug("处理目录时出错，跳过: {} - {}", dir, e.getMessage());
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("访问文件失败，跳过: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("分析日志文件时发生错误: {}", e.getMessage(), e);
            throw e;
        }
        
        return suggestions;
    }
    
    /**
     * 分析缓存目录
     */
    private List<CleanupSuggestion> analyzeCacheDirectories(String path) throws IOException {
        List<CleanupSuggestion> suggestions = new CopyOnWriteArrayList<>();
        Path rootPath = Paths.get(path);
        DiskScannerService scanner = new DiskScannerService();
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!Files.isReadable(dir)) {
                        log.debug("跳过无权限的目录: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    String name = dir.getFileName().toString().toLowerCase();
                    if (CLEANUP_DIR_PATTERNS.contains(name) && !isBlockedPath(dir.toString())) {
                        // 使用线程池异步计算目录大小
                        if (executorService != null) {
                            executorService.submit(() -> {
                                try {
                                    long size = calculateDirectorySize(dir);
                                    if (size > 0) {
                                        suggestions.add(CleanupSuggestion.builder()
                                                .type("DIRECTORY")
                                                .path(dir.toString())
                                                .reclaimableSpace(size)
                                                .formattedReclaimableSpace(scanner.formatFileSize(size))
                                                .priority("HIGH")
                                                .description("缓存目录：可以安全清理")
                                                .riskWarning("清理后应用可能会重新生成缓存")
                                                .build());
                                    }
                                } catch (Exception e) {
                                    log.debug("计算目录大小失败，跳过: {} - {}", dir, e.getMessage());
                                }
                            });
                        }
                        // 继续遍历子目录，因为可能还有其他缓存目录
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("访问文件失败，跳过: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("分析缓存目录时发生错误: {}", e.getMessage(), e);
            throw e;
        }
        
        return suggestions;
    }
    
    /**
     * 分析构建产物
     */
    private List<CleanupSuggestion> analyzeBuildArtifacts(String path) throws IOException {
        List<CleanupSuggestion> suggestions = new CopyOnWriteArrayList<>();
        Path rootPath = Paths.get(path);
        DiskScannerService scanner = new DiskScannerService();
        
        List<String> buildPatterns = Arrays.asList("target", "build", "dist", ".next", "out");
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!Files.isReadable(dir)) {
                        log.debug("跳过无权限的目录: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    String name = dir.getFileName().toString().toLowerCase();
                    if (buildPatterns.contains(name) && !isBlockedPath(dir.toString())) {
                        // 使用线程池异步计算目录大小
                        if (executorService != null) {
                            executorService.submit(() -> {
                                try {
                                    long size = calculateDirectorySize(dir);
                                    if (size > 0) {
                                        suggestions.add(CleanupSuggestion.builder()
                                                .type("DIRECTORY")
                                                .path(dir.toString())
                                                .reclaimableSpace(size)
                                                .formattedReclaimableSpace(scanner.formatFileSize(size))
                                                .priority("MEDIUM")
                                                .description("构建产物目录：可以重新编译生成")
                                                .riskWarning("删除后需要重新构建项目")
                                                .build());
                                    }
                                } catch (Exception e) {
                                    log.debug("计算目录大小失败，跳过: {} - {}", dir, e.getMessage());
                                }
                            });
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("访问文件失败，跳过: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("分析构建产物时发生错误: {}", e.getMessage(), e);
            throw e;
        }
        
        return suggestions;
    }
    
    /**
     * 计算目录大小
     */
    private long calculateDirectorySize(Path directory) throws IOException {
        final java.util.concurrent.atomic.AtomicLong totalSize = new java.util.concurrent.atomic.AtomicLong(0);
        
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (Files.isReadable(file)) {
                            totalSize.addAndGet(attrs.size());
                        }
                    } catch (Exception e) {
                        log.debug("计算文件大小时出错，跳过: {} - {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!Files.isReadable(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("访问文件失败，跳过: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("计算目录大小失败: {}", directory, e);
        }
        
        return totalSize.get();
    }
    
    /**
     * 检查是否为被阻止的路径
     */
    private boolean isBlockedPath(String path) {
        return BLOCKED_PATHS.stream().anyMatch(path::startsWith);
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * 获取优先级排序值
     */
    private int getPriorityOrder(String priority) {
        switch (priority) {
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            case "LOW": return 3;
            default: return 4;
        }
    }
}
