package com.zhl.ai.javaaiday1.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author zhl
 * @version 1.0.0
 * @Description TODO 模拟天气服务
 * @createTime 2026年05月12日 09:11
 */
@Component
public class WeatherService {
    @Tool(description = "根据城市名称查询当前天气（模拟数据）")
    public String getWeather(@ToolParam(description = "城市名称，如 Beijing") String city) {
        // 模拟不同城市的天气
        return switch (city.toLowerCase()) {
            case "beijing" -> "北京：晴，25°C，空气质量良";
            case "shanghai" -> "上海：多云，28°C，湿度65%";
            case "guangzhou" -> "广州：雷阵雨，32°C，注意带伞";
            default -> city + "：晴，22°C，风力2级";
        };
    }
}
