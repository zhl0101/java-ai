# 磁盘空间分析器

这是一个基于 Spring Boot 的磁盘空间分析服务，可以扫描主机磁盘各目录文件大小，并智能分析建议哪些文件可以删除。

## 功能特性

- 🔍 **目录扫描**：递归扫描目录，计算各子目录和文件大小
- 📊 **可视化展示**：提供 Web 界面展示磁盘使用情况
- 💡 **智能分析**：自动识别可清理的临时文件、日志、缓存等
- 🔒 **安全保护**：白名单机制防止误删系统关键文件
- 🎯 **优先级排序**：按高/中/低优先级推荐清理项

## 快速开始

### 1. 启动服务

```bash
cd java-ai-disk-analyzer
mvn spring-boot:run
```

服务将在 `http://localhost:8081` 启动

### 2. 访问界面

打开浏览器访问：`http://localhost:8081`

### 3. API 接口

- `GET /api/disk/analyze?path=/tmp` - 分析指定目录
- `GET /api/disk/directories?path=/tmp` - 获取子目录列表
- `GET /api/disk/large-files?path=/tmp` - 获取大文件列表
- `GET /api/disk/cleanup-suggestions?path=/tmp` - 获取清理建议
- `GET /api/disk/safe-paths` - 获取安全路径白名单

## 配置说明

在 `application.yaml` 中配置：

```yaml
disk:
  analyzer:
    # 安全路径白名单（只允许扫描这些路径）
    safe-paths:
      - /tmp
      - /var/tmp
      - /home
      - /Users
    
    # 禁止操作的路径（系统关键目录）
    blocked-paths:
      - /
      - /bin
      - /sbin
      - /etc
      - /boot
    
    # 大文件阈值（MB）
    large-file-threshold: 100
    
    # 建议清理的临时文件天数
    temp-file-days: 7
```

## 清理建议类型

1. **临时文件**：超过指定天数的 .tmp, .bak, .cache 等文件
2. **日志文件**：大型 .log, .out, .err 文件
3. **缓存目录**：cache, temp, node_modules 等
4. **构建产物**：target, build, dist 等编译输出
5. **大文件**：超过阈值的大文件，按修改时间排序

## 安全机制

- ✅ 路径白名单验证
- ✅ 系统关键目录保护
- ✅ 风险提示和警告
- ✅ 只读分析，不执行实际删除操作

## 技术栈

- Spring Boot 3.2.0
- Java 17
- Thymeleaf（前端模板）
- Lombok

## 注意事项

⚠️ **本工具仅提供分析和建议，不会自动删除任何文件。请用户确认后再手动清理。**
