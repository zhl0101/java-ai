package com.zhl.ai.javaaiday1.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO 全局异常处理和请求日志
 * @createTime 2026年05月12日 09:17
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    public Map<String, String> handleTimeout(ResourceAccessException e) {
        log.error("请求超时: {}", e.getMessage());
        return Map.of("error", "AI 服务响应超时，请稍后重试或简化问题");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneral(Exception e) {
        log.error("未知错误", e);
        return Map.of("error", "服务器内部错误");
    }
}
