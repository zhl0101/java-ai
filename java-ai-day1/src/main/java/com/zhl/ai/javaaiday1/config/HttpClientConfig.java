package com.zhl.ai.javaaiday1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP 客户端超时配置
 * 解决 DashScope API 调用超时问题
 *
 * 根本原因：Spring AI Alibaba 底层使用 OkHttp 发起请求，
 * 但 YAML 中的 connection-timeout / read-timeout / write-timeout 属性
 * 不是 DashScope 自动配置支持的属性，所以不会生效。
 * 需要通过自定义 RestClient.Builder 来设置 OkHttp 超时。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        // 自定义 JDK HttpClient，设置合理的超时时间
        // 注意：JdkClientHttpRequestFactory 是 Spring 6.1+ 推荐的方式，替代已废弃的 OkHttp3ClientHttpRequestFactory
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))      // 连接超时 10秒
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(180)); // 读取超时 180秒（大模型推理较慢，需足够长）

        return RestClient.builder().requestFactory(requestFactory);
    }
}
