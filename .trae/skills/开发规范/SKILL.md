---
name: 开发规范
description: 本Skill提供拍拍伴读后端项目的完整开发规范，涵盖代码注释、API设计、分层架构、数据库操作、安全合规等方面。旨在确保团队成员在开发过程中遵循统一标准，提高代码质量、可维护性和协作效率。
---

# 拍拍伴读后端开发规范

---

## Skill 描述

### 一、Skill概述

本Skill提供拍拍伴读后端项目的完整开发规范，涵盖代码注释、API设计、分层架构、数据库操作、安全合规等方面。旨在确保团队成员在开发过程中遵循统一标准，提高代码质量、可维护性和协作效率。

---

### 二、适用场景与触发时机

#### 场景1：新功能开发

**适用条件**：
- 需要新增Controller、Service、Entity、Mapper等任意后端组件
- 需要设计新的API接口供前端调用
- 需要扩展现有业务模块的功能

**触发时机**：
- 需求评审完成后，开始编写代码前
- 技术方案设计阶段，需要确定代码结构和命名规范

**使用方式**：
- 参考第2节确定组件所属模块（如 `apps/reading/compat` 或 `sys/billing`）
- 参考第3节编写Javadoc注释
- 参考第4节设计API响应格式

---

#### 场景2：接口对接与联调

**适用条件**：
- 与iOS前端进行API联调
- 集成外部服务（AppStore订阅、阿里云OCR/TTS）
- 编写Webhook回调处理逻辑

**触发时机**：
- 前端开发完成接口对接前
- 外部服务集成方案确认后
- 需要提供API文档给对接方

**使用方式**：
- 必须添加 `@Tag` 和 `@Operation` 注解生成Swagger文档
- 使用 `ApiResponse<T>` 统一响应格式
- 确保通过 `TraceFilter` 传递 `requestId`

---

#### 场景3：Bug修复与代码优化

**适用条件**：
- 修复生产环境或测试环境发现的Bug
- 重构现有代码以提高性能或可读性
- 优化数据库查询语句

**触发时机**：
- Bug工单分配后开始修复前
- 代码审查发现问题需要改进时
- 性能监控发现瓶颈需要优化时

**使用方式**：
- 确保修复代码符合现有代码风格
- 保持注释与代码同步更新
- 遵循分层职责，不跨层处理业务逻辑

---

#### 场景4：代码审查

**适用条件**：
- Review团队成员提交的Pull Request
- 检查代码是否符合项目规范
- 确保新代码不会引入技术债务

**触发时机**：
- PR提交后进入代码审查阶段
- 版本发布前的代码质量检查
- 新人提交首次代码时

**使用方式**：
- 使用第8节快速检查清单逐项验证
- 检查注释完整性和准确性
- 验证API设计是否符合统一规范

---

#### 场景5：新人Onboarding

**适用条件**：
- 新团队成员首次接触本项目
- 需要快速了解项目结构和开发规范
- 需要学习项目特有的代码模式和工具使用

**触发时机**：
- 新人入职后进行技术培训时
- 需要快速上手开发任务前

**使用方式**：
- 依次阅读第1-2节了解项目结构
- 参考第9节代码风格规范
- 以 `ReadingAccountCompatController` 为范例学习Controller开发模式

---

### 三、使用方法

#### 步骤1：开发前准备

1. 阅读**第1节**了解技术栈（Spring Boot 3.3.5 + Java 17 + MyBatis Plus）
2. 阅读**第2节**熟悉模块目录组织，确定新代码的存放位置
3. 查找相似功能的现有代码作为参考（如新增账号相关接口参考 `ReadingAccountCompatController`）

#### 步骤2：开发过程

```
标准开发流程:
  1. 确定所属模块 → apps/reading（业务模块）或 sys/xxx（系统模块）
  2. 确定组件类型 → Controller/Service/Entity/Mapper/CrudService
  3. 编写Javadoc注释 → 遵循第3节规范
  4. 实现业务逻辑 → 遵循第5节分层职责
  5. 设计API响应 → 遵循第4节统一格式
  6. 数据库变更 → 编写Flyway迁移脚本（第6节）
  7. 添加单元测试 → 覆盖核心业务逻辑（第11节）
```

#### 步骤3：代码提交前检查

使用以下检查清单验证代码合规性：

| 检查项 | 验证内容 | 参考章节 |
|--------|----------|----------|
| 注释规范 | 类/方法是否有Javadoc注释，描述清晰 | 第3节 |
| API文档 | Controller是否有`@Tag`和`@Operation`注解 | 3.2节 |
| 响应格式 | 是否使用`ApiResponse.success(requestId, data)` | 4.1节 |
| 用户鉴权 | 用户接口是否通过`userResolver.require(request)`鉴权 | 5.1节 |
| 事务管理 | 涉及多表操作是否标注`@Transactional` | 5.2节 |
| 数据库迁移 | 表结构变更是否创建Flyway脚本 | 6.2节 |
| 日志规范 | 是否正确使用SLF4J日志框架 | 9.1节 |
| 异常处理 | 是否使用`ResponseStatusException`抛出业务异常 | 7.1节 |

#### 步骤4：常用代码模板

**Controller模板**：
```java
/**
 * [功能领域]控制器。
 * [详细说明该控制器处理的业务场景、权限要求和设计约束]。
 */
@Tag(name = "[API分组名称]", description = "[API功能描述]")
@RestController
@RequestMapping("/api/v1/[资源路径]")
public class [Xxx]Controller {
    private final [依赖Service] service;
    
    public [Xxx]Controller([依赖Service] service) {
        this.service = service;
    }
    
    @Operation(summary = "[接口简称]", description = "[接口详细说明，包括参数含义、返回结构、业务规则]")
    @GetMapping("/[子路径]")
    public ApiResponse<[返回类型]> [方法名](HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(MDC.get(TraceFilter.REQUEST_ID_KEY), service.[业务方法](user));
    }
}
```

**Service模板**：
```java
/**
 * [业务模块]服务。
 * [说明服务职责、核心业务逻辑、调用的外部依赖和事务边界]。
 */
@Service
public class [Xxx]Service {
    private final [Mapper接口] mapper;
    private final [其他Service] otherService;
    
    public [Xxx]Service([Mapper接口] mapper, [其他Service] otherService) {
        this.mapper = mapper;
        this.otherService = otherService;
    }
    
    @Transactional
    public [返回类型] [业务方法](ReadingAuthenticatedUser user, [参数]) {
        // 业务逻辑实现
    }
}
```

**Entity模板**：
```java
/**
 * [业务领域]实体。
 * [说明实体的业务意义、数据权威性要求、软删除策略和字段约束]。
 */
@TableName("[表名]")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class [Xxx]Entity {
    @TableId
    private String id;
    private String appCode;
    private Long userId;
    // 其他字段...
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

---

### 四、核心规则速查

| 规则类别 | 核心要求 |
|----------|----------|
| **命名规范** | 类名UpperCamelCase，方法名lowerCamelCase，常量UPPER_SNAKE_CASE |
| **注释要求** | 所有类和公开方法必须有Javadoc注释 |
| **API响应** | 统一使用`ApiResponse<T>`，必须包含`requestId` |
| **用户鉴权** | 用户接口必须通过`ReadingAuthenticatedUserResolver`解析用户 |
| **事务管理** | 多表操作必须标注`@Transactional` |
| **数据库设计** | 使用`OffsetDateTime`存储时间，软删除使用`deletedAt`字段 |
| **异常处理** | 使用`ResponseStatusException`抛出业务异常 |
| **日志记录** | 使用SLF4J，关键业务节点记录INFO级别日志 |
| **安全合规** | 遵循COPPA/GDPR要求，敏感数据脱敏处理 |

---

## 1. 项目概述

- **技术栈**: Spring Boot 3.3.5 + Java 17 + MyBatis Plus 3.5.7
- **数据库**: PostgreSQL + Flyway 迁移管理
- **缓存**: Redis
- **API文档**: Springdoc OpenAPI 2.6.0
- **认证**: JWT (nimbus-jose-jwt 9.37.3)
- **基础包名**: `com.apphub.backend`

## 2. 模块结构

```
com.apphub.backend
├── apps/
│   ├── common/           # 应用通用组件
│   │   ├── AppModule.java
│   │   ├── AppModuleRegistry.java
│   │   ├── AppCompatControllerSupport.java
│   │   └── AppEntitlementAccessGuard.java
│   └── reading/         # 拍拍伴读阅读模块
│       ├── compat/      # 兼容层控制器和服务
│       ├── domain/      # 领域层(entity/mapper/service/crud)
│       ├── provider/    # 外部服务provider(OCR/TTS)
│       └── privacy/     # 隐私合规相关
├── sys/                 # 系统级模块
│   ├── app/             # 应用配置管理
│   ├── auth/            # 认证授权
│   ├── billing/         # 订阅计费
│   ├── compensation/    # 补偿兑换码
│   ├── configcenter/   # 远程配置
│   ├── entitlement/    # 权益中心
│   └── appstore/        # AppStore集成
├── common/              # 通用组件
│   ├── config/          # 配置类
│   ├── filter/          # 过滤器
│   ├── mybatis/         # MyBatis扩展
│   ├── response/        # 统一响应
│   └── util/            # 工具类
└── shared/              # 跨模块共享
    └── apple/           # Apple服务集成
```

## 3. 代码注释规范

### 3.1 类注释

```java
/**
 * [模块名] 简短的类描述。
 * 补充说明类的职责、业务意义或重要约束。
 */
```

**示例**:
```java
/**
 * reading 孩子档案实体。
 * 孩子档案属于付费权益约束内的核心内容，必须以后端记录为准，避免客户端本地绕过数量限制。
 */
@TableName("reading_child_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingChildProfileEntity {
```

### 3.2 Controller注释

使用 `@Tag` 和 `@Operation` 描述API:

```java
/**
 * 接口所属领域的简要描述。
 * 补充说明该控制器处理的主要业务场景。
 */
@Tag(name = "接口分组名称", description = "接口功能描述。")
@RestController
@RequestMapping("/api/v1/xxx")
public class XxxController {

    @Operation(summary = "接口简称", description = "接口详细描述，包括请求参数含义、返回值结构、业务规则等。")
    @GetMapping("/path")
    public ApiResponse<ReturnType> methodName(...) {
```

**示例**:
```java
/**
 * 拍拍伴读账号兼容控制器。
 * 所有账号状态、首页汇总和删除账号动作都走后端鉴权，满足 App Store 删除账号和权益权威性要求。
 */
@Tag(name = "拍拍伴读账号", description = "拍拍伴读账号状态、首页汇总和删除账号接口。")
@RestController
@RequestMapping("/api/v1/account")
public class ReadingAccountCompatController {
```

### 3.3 Service注释

```java
/**
 * [模块] 服务名称。
 * 补充说明服务的职责、使用的外部依赖或业务规则。
 */
@Service
public class XxxService {
```

### 3.4 Entity注释

```java
/**
 * [业务含义] 实体描述。
 * 说明实体的业务意义、重要约束或权威性要求。
 */
@TableName("table_name")
@Data
public class XxxEntity {
```

### 3.5 方法内部注释

- 保持简洁，一行注释用于说明复杂逻辑
- 使用行注释 `//` 而非块注释
- 避免显而易见的注释

## 4. API设计规范

### 4.1 统一响应格式

使用 `ApiResponse<T>` record:

```java
public record ApiResponse<T>(
    boolean success,
    String requestId,
    T data,
    String message
)
```

**成功响应**:
```java
return ApiResponse.success(currentRequestId(), data);
```

**失败响应**:
```java
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "错误信息");
```

### 4.2 请求ID传递

在Controller层始终通过 `TraceFilter` 注入的 `requestId` 返回:

```java
private String currentRequestId() {
    return MDC.get(TraceFilter.REQUEST_ID_KEY);
}
```

### 4.3 Swagger文档要求

所有对外API必须包含 `@Operation` 注解，说明:
- `summary`: 接口简称
- `description`: 详细描述，包括请求参数含义、返回值结构、业务规则

```java
@Operation(summary = "查询账号状态", description = "查询当前 App 账号权益、孩子数量和每日额度状态。")
@GetMapping("/me/state")
public ApiResponse<xxx> state(...) {
```

### 4.4 参数校验

使用 `@Valid` 和 Jakarta Validation 注解:

```java
@PostMapping("/quota/usage")
public ApiResponse<ReturnType> recordQuotaUsage(
    @Valid @RequestBody RequestType body,
    HttpServletRequest request
) {
```

## 5. 分层职责

### 5.1 Controller层

- 处理HTTP请求/响应
- 参数校验
- 调用Service层
- 不包含业务逻辑

### 5.2 Service层

- 业务逻辑处理
- 事务管理 (`@Transactional`)
- 调用多个Mapper或外部服务

### 5.3 Mapper层 (MyBatis Plus)

- 数据访问
- 使用 `LambdaQueryWrapper` 和 `LambdaUpdateWrapper`
- 禁止在Mapper中写业务逻辑

### 5.4 Entity层

- 数据结构
- 使用 `@TableName` 指定数据库表名
- 使用Lombok注解生成getter/setter/constructor

## 6. 数据库规范

### 6.1 表命名

- 使用snake_case: `reading_child_profile`
- 必须有 `created_at`, `updated_at` 时间戳字段
- 软删除使用 `deleted_at` 字段

### 6.2 Flyway迁移

- 迁移文件放在 `src/main/resources/db/first_version/`
- 命名格式: `V{version}__{description}.sql`
- 示例: `V1__init.sql`

### 6.3 字段类型

- 使用 `OffsetDateTime` 处理时间（带时区）
- UUID作为主键类型: `varchar(36)`
- 枚举使用string存储

## 7. 异常处理

### 7.1 业务异常

使用 `ResponseStatusException`:

```java
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在");
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数校验失败");
```

### 7.2 认证/授权异常

通过 `AppEntitlementAccessGuard` 处理:

```java
private final AppEntitlementAccessGuard entitlementGuard;

public void someMethod() {
    entitlementGuard.requireValidEntitlement(user, appCode);
}
```

## 8. 配置管理

### 8.1 多环境配置

- `application-dev.yml` - 开发环境
- `application-test.yml` - 测试环境
- `application-prod.yml` - 生产环境

### 8.2 敏感配置

敏感信息通过环境变量或专用配置中心管理，禁止硬编码。

## 9. 日志规范

### 9.1 日志框架

使用SLF4J + Logback:

```java
private static final Logger log = LoggerFactory.getLogger(XxxService.class);
```

### 9.2 日志级别

- `ERROR`: 异常情况
- `WARN`: 潜在问题（如降级处理）
- `INFO`: 重要业务节点
- `DEBUG`: 调试信息

### 9.3 MDC使用

使用 `MDC.put()` 记录关键上下文（如userId, requestId）便于追踪。

## 10. 代码风格

### 10.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | `ReadingChildProfileEntity` |
| 方法名 | lowerCamelCase | `getUserById` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 包名 | lowercase | `com.apphub.backend` |

### 10.2 Import规范

- 按顺序排列: java > javax > spring > mybatis > lombok > 其他
- 使用Lombok减少import
- 避免通配符import

### 10.3 代码格式化

- 使用4空格缩进
- 行长度不超过120字符
- 运算符前后空格
- 控制语句括号风格: K&R

## 11. 测试要求

### 11.1 单元测试

- Service层必须有单元测试
- 使用 `@SpringBootTest` 或纯JUnit
- 测试命名: `shouldXxxWhenXxx()`

### 11.2 测试覆盖

关键业务逻辑需覆盖:
- 正常流程
- 异常流程
- 边界条件

## 12. Git提交规范

### 12.1 Commit Message格式

```
[类型] 简短描述

详细说明（可选）

Closes: #issue号
```

### 12.2 类型标识

- `[Feature]`: 新功能
- `[Fix]`: Bug修复
- `[Refactor]`: 重构
- `[Docs]`: 文档更新
- `[Style]`: 代码格式调整

## 13. 安全规范

### 13.1 认证要求

- 所有用户接口必须鉴权
- 使用 `ReadingAuthenticatedUserResolver` 解析用户
- Ops接口使用独立鉴权链

### 13.2 数据保护

- 敏感数据脱敏后返回
- 用户删除请求必须审计
- 遵循COPPA/GDPR合规要求

## 14. 常用工具类

### 14.1 认证用户解析

```java
ReadingAuthenticatedUser user = userResolver.require(request);
String userId = user.userId();
```

### 14.2 统一响应构建

```java
ApiResponse.success(requestId, data)
ApiResponse.success(requestId, new SomeView(...))
```

### 14.3 请求ID获取

```java
String requestId = MDC.get(TraceFilter.REQUEST_ID_KEY);
```