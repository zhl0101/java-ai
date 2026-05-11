package com.zhl.ai.javaaiday1.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO 时间工具服务
 * @createTime 2026年05月11日 16:36
 */
@Component
public class TimeToolService {

    @Tool(description = "获取当前的系统时间，精确到秒，返回格式为 yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
