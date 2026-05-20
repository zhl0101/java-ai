package com.zhl.ai.diskanalyzer.service;

import com.zhl.ai.diskanalyzer.model.DirectoryInfo;
import com.zhl.ai.diskanalyzer.model.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 磁盘扫描服务
 */
@Slf4j
@Service
public class DiskScannerService {
    
    @Value("${disk.analyzer.large-file-threshold:100}")
    private int largeFileThresholdMB;
    
    @Value("${disk.analyzer.thread-pool-size:4}")
    private int threadPoolSize;
    
    // 线程池用于并行扫描子目录
    private ExecutorService executorService;
    
    /**
     * 扫描目录，获取目录结构和文件大小信息
     */
    public DirectoryInfo scanDirectory(String path) throws IOException {
        Path rootPath = Paths.get(path);
        
        if (!Files.exists(rootPath)) {
            throw new IllegalArgumentException("路径不存在: " + path);
        }
        
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("路径不是目录: " + path);
        }
        
        log.info("开始扫描目录: {}", path);
        long startTime = System.currentTimeMillis();
        
        AtomicLong totalSize = new AtomicLong(0);
        AtomicInteger fileCount = new AtomicInteger(0);
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (Files.isReadable(file)) {
                            totalSize.addAndGet(attrs.size());
                            fileCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.debug("跳过无法访问的文件: {}", file);
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
                    log.debug("访问文件失败: {} - {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("扫描目录时发生错误: {}", path, e);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("扫描完成: {} (耗时: {}ms, 文件数: {}, 大小: {})", 
                path, elapsed, fileCount.get(), formatFileSize(totalSize.get()));
        
        String formattedSize = formatFileSize(totalSize.get());
        
        return DirectoryInfo.builder()
                .path(path)
                .name(rootPath.getFileName().toString())
                .size(totalSize.get())
                .formattedSize(formattedSize)
                .fileCount(fileCount.get())
                .build();
    }
    
    /**
     * 获取子目录列表及其大小（使用多线程并行扫描）
     */
    public List<DirectoryInfo> getSubDirectories(String path) throws IOException {
        Path rootPath = Paths.get(path);
        List<DirectoryInfo> subDirs = new CopyOnWriteArrayList<>();
        
        // 初始化线程池
        executorService = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName("dir-scanner-" + thread.getId());
            return thread;
        });
        
        try {
            // 先获取所有一级子目录
            List<Path> firstLevelDirs = new ArrayList<>();
            try (var stream = Files.list(rootPath)) {
                stream.filter(Files::isDirectory)
                      .filter(Files::isReadable)
                      .forEach(firstLevelDirs::add);
            }
            
            log.info("发现 {} 个子目录，开始并行扫描...", firstLevelDirs.size());
            long startTime = System.currentTimeMillis();
            
            // 并行扫描每个子目录
            List<java.util.concurrent.Future<DirectoryInfo>> futures = new ArrayList<>();
            for (Path dir : firstLevelDirs) {
                futures.add(executorService.submit(() -> {
                    try {
                        DirectoryInfo dirInfo = calculateDirectorySize(dir);
                        log.debug("扫描完成: {} (大小: {})", dir.getFileName(), dirInfo.getFormattedSize());
                        return dirInfo;
                    } catch (IOException e) {
                        log.debug("跳过无法访问的目录: {}", dir);
                        return null;
                    }
                }));
            }
            
            // 收集结果
            for (int i = 0; i < futures.size(); i++) {
                try {
                    DirectoryInfo info = futures.get(i).get(2, TimeUnit.MINUTES);
                    if (info != null) {
                        subDirs.add(info);
                    }
                } catch (InterruptedException e) {
                    log.warn("扫描任务被中断: 目录{}", i + 1);
                    Thread.currentThread().interrupt();
                    break;
                } catch (java.util.concurrent.ExecutionException e) {
                    log.error("扫描任务执行失败: 目录{} - {}", i + 1, e.getCause().getMessage());
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("扫描任务超时: 目录{}", i + 1);
                }
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("所有子目录扫描完成，耗时: {}ms", elapsed);
            
        } finally {
            // 优雅关闭线程池
            shutdownExecutor();
        }
        
        // 按大小排序（从大到小）
        subDirs.sort((a, b) -> Long.compare(b.getSize(), a.getSize()));
        
        return subDirs;
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
     * 获取大文件列表
     */
    public List<FileInfo> getLargeFiles(String path) throws IOException {
        Path rootPath = Paths.get(path);
        long thresholdBytes = (long) largeFileThresholdMB * 1024 * 1024;
        List<FileInfo> largeFiles = new CopyOnWriteArrayList<>();
        
        log.info("开始扫描大文件（阈值: {} MB）...", largeFileThresholdMB);
        long startTime = System.currentTimeMillis();
        
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (Files.isReadable(file) && attrs.size() > thresholdBytes) {
                            FileInfo fileInfo = createFileInfo(file, attrs.size());
                            largeFiles.add(fileInfo);
                            log.debug("发现大文件: {} ({})", file.getFileName(), formatFileSize(attrs.size()));
                        }
                    } catch (IOException e) {
                        log.debug("跳过无法访问的文件: {}", file);
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
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("扫描大文件时发生错误: {}", path, e);
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("大文件扫描完成，发现 {} 个大文件，耗时: {}ms", largeFiles.size(), elapsed);
        
        // 按大小排序（从大到小）
        largeFiles.sort((a, b) -> Long.compare(b.getSize(), a.getSize()));
        
        return largeFiles;
    }
    
    /**
     * 计算目录大小
     */
    private DirectoryInfo calculateDirectorySize(Path directory) throws IOException {
        AtomicLong totalSize = new AtomicLong(0);
        AtomicInteger fileCount = new AtomicInteger(0);
        
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (Files.isReadable(file)) {
                            totalSize.addAndGet(attrs.size());
                            fileCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.debug("跳过无法访问的文件: {}", file);
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
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("计算目录大小时部分文件无法访问: {}", directory);
        }
        
        return DirectoryInfo.builder()
                .path(directory.toString())
                .name(directory.getFileName().toString())
                .size(totalSize.get())
                .formattedSize(formatFileSize(totalSize.get()))
                .fileCount(fileCount.get())
                .build();
    }
    
    /**
     * 创建文件信息对象
     */
    private FileInfo createFileInfo(Path file, long size) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        LocalDateTime lastModified = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), 
                ZoneId.systemDefault()
        );
        
        String fileName = file.getFileName().toString();
        String fileType = getFileExtension(fileName);
        
        return FileInfo.builder()
                .path(file.toString())
                .name(fileName)
                .size(size)
                .formattedSize(formatFileSize(size))
                .lastModified(lastModified)
                .fileType(fileType)
                .build();
    }
    
    /**
     * 格式化文件大小
     */
    public String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }
}
