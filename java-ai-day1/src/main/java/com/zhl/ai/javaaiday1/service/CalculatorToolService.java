package com.zhl.ai.javaaiday1.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO 计算器工具服务（支持加减乘除）
 * @createTime 2026年05月11日 16:37
 */
@Component
public class CalculatorToolService {

    @Tool(description = "执行基本的算术运算，支持加法、减法、乘法和除法")
    public double calculate(@ToolParam(description = "第一个数字") double a,
                            @ToolParam(description = "运算符，可选值: add, subtract, multiply, divide") String operation,
                            @ToolParam(description = "第二个数字") double b){
        switch ( operation){
            case "add":
                return a + b;
            case "subtract":
                return a - b;
            case "multiply":
                return a * b;
            case "divide":
                if (b == 0) throw new IllegalArgumentException("除数不能为零");
                return a / b;
            default:
                throw new IllegalArgumentException("不支持的运算: " + operation);
        }

    }

}
