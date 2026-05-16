# 代码类与方法中文说明（按公共类和各 App 区分）

生成时间：2026-04-28 09:35:22

说明：本文件覆盖 src/main/java 下识别到的 public class/record/interface/enum，并列出 public 方法签名。私有辅助方法较多，通常属于类内部实现细节，未逐项展开。

## App 模块：common

### AppCompatControllerSupport
- 类型：`class`
- 包名：`com.apphub.backend.apps.common`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppCompatControllerSupport.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public AppDefinition requireAppDefinition(String appCode)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public String requireSessionToken(HttpServletRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public AuthenticatedSessionView requireCurrentSession(String sessionToken)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public AuthenticatedSessionView requireCurrentSessionForApp(String appCode, HttpServletRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public void ensureSessionBelongsToApp(String expectedAppCode, String actualAppCode)`：执行 AppCompatControllerSupport 中的 ensureSessionBelongsToApp 业务逻辑。

### AppModule
- 类型：`interface`
- 包名：`com.apphub.backend.apps.common`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppModule.java`
- 中文说明：App 模块定义：声明应用编码、名称、启用状态和模块能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppModuleRegistry
- 类型：`class`
- 包名：`com.apphub.backend.apps.common`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppModuleRegistry.java`
- 中文说明：注册表/路由类：收集多个实现并按 appCode 或类型进行查找分发。
- 公开方法：
  - `public List<AppModule> activeModules()`：判断能力支持或启用状态。
  - `public Optional<AppModule> get(String appCode)`：查询指定对象、配置、列表或当前上下文数据。
  - `public AppModule require(String appCode)`：校验入参、权限、会话或业务条件，不满足时抛出异常。

### AppPowerSyncAdapter
- 类型：`interface`
- 包名：`com.apphub.backend.apps.common`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppPowerSyncAdapter.java`
- 中文说明：适配器：把通用系统能力转换成具体 app 的实现逻辑。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppVersionConfigController
- 类型：`class`
- 包名：`com.apphub.backend.apps.common`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppVersionConfigController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppVersionPolicyService
- 类型：`class`
- 包名：`com.apphub.backend.apps.common`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppVersionPolicyService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> policy(String appCode, String platform, String currentVersion, String currentBuild)`：执行 AppVersionPolicyService 中的 policy 业务逻辑。

### Decision
- 类型：`record`
- 包名：`com.apphub.backend.apps.common.entitlement`
- 文件：`src/main/java/com/apphub/backend/apps/common/entitlement/AppEntitlementAccessGuard.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public static List<String> activeEntitlementCodes(EntitlementOverviewView overview)`：判断能力支持或启用状态。


## App 模块：fitmystery

### FitMysteryAccountController
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.account`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/account/FitMysteryAccountController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public FitMysteryApiEnvelope<Map<String, Object>> deleteAccount(HttpServletRequest request)`：删除、退出或撤销相关资源。

### FitMysteryAccountService
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.account`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/account/FitMysteryAccountService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> deleteAppData(Long userId)`：删除、退出或撤销相关资源。

### FitMysteryActivityController
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.activity`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/activity/FitMysteryActivityController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryActivityService
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.activity`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/activity/FitMysteryActivityService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> batchSubmit(Long userId, ActivityBatchSubmitRequest request)`：执行 FitMysteryActivityService 中的 batchSubmit 业务逻辑。
  - `public Map<String, Object> today(Long userId, LocalDate date)`：组装、转换或映射数据结构。

### FitMysteryApiEnvelope
- 类型：`record`
- 包名：`com.apphub.backend.apps.fitmystery.api`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/api/FitMysteryApiEnvelope.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public static <T> FitMysteryApiEnvelope<T> ok(String requestId, T data)`：执行 FitMysteryApiEnvelope 中的 ok 业务逻辑。

### FitMysteryBoxController
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.box`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/box/FitMysteryBoxController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public FitMysteryApiEnvelope<Map<String, Object>> state(HttpServletRequest request)`：查询指定对象、配置、列表或当前上下文数据。
  - `public FitMysteryApiEnvelope<Map<String, Object>> open(HttpServletRequest request, @RequestBody FitMysteryBoxService.OpenBoxRequest body)`：接收并处理客户端提交的数据或动作。
  - `public FitMysteryApiEnvelope<Map<String, Object>> draws(HttpServletRequest request, @RequestParam(defaultValue = "50") int pageSize)`：查询指定对象、配置、列表或当前上下文数据。
  - `public FitMysteryApiEnvelope<Map<String, Object>> collection(HttpServletRequest request, @RequestParam(defaultValue = "100") int pageSize)`：查询指定对象、配置、列表或当前上下文数据。

### FitMysteryBoxService
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.box`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/box/FitMysteryBoxService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> state(Long userId)`：查询指定对象、配置、列表或当前上下文数据。
  - `public Map<String, Object> open(Long userId, OpenBoxRequest request)`：接收并处理客户端提交的数据或动作。
  - `public Map<String, Object> collection(Long userId, int limit)`：查询指定对象、配置、列表或当前上下文数据。
  - `public Map<String, Object> history(Long userId, int limit)`：查询指定对象、配置、列表或当前上下文数据。

### FitMysteryRequestSupport
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.common`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/common/FitMysteryRequestSupport.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public Long requireUserId(HttpServletRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public String requestId()`：执行 FitMysteryRequestSupport 中的 requestId 业务逻辑。

### FitMysteryConfigController
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.config`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/config/FitMysteryConfigController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public FitMysteryApiEnvelope<Map<String, Object>> bootstrap(@RequestParam(defaultValue = "zh-Hans") String locale)`：创建或初始化业务数据。
  - `public FitMysteryApiEnvelope<Map<String, Object>> appStoreSummary()`：查询指定对象、配置、列表或当前上下文数据。
  - `public FitMysteryApiEnvelope<Map<String, Object>> odds()`：查询指定对象、配置、列表或当前上下文数据。

### FitMysteryConfigService
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.config`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/config/FitMysteryConfigService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> bootstrap(String locale)`：创建或初始化业务数据。
  - `public Map<String, Object> pointsPolicy()`：执行 FitMysteryConfigService 中的 pointsPolicy 业务逻辑。
  - `public Map<String, Object> boxPolicy()`：执行 FitMysteryConfigService 中的 boxPolicy 业务逻辑。
  - `public Map<String, Object> productPolicy()`：执行 FitMysteryConfigService 中的 productPolicy 业务逻辑。
  - `public Map<String, Object> reportPolicy()`：执行 FitMysteryConfigService 中的 reportPolicy 业务逻辑。
  - `public Map<String, Object> appStoreSummary()`：查询指定对象、配置、列表或当前上下文数据。
  - `public Map<String, Object> oddsDisclosure()`：查询指定对象、配置、列表或当前上下文数据。
  - `public Map<String, Object> namespace(String namespaceCode)`：执行 FitMysteryConfigService 中的 namespace 业务逻辑。

### FitActivityEventEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/entity/FitActivityEventEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitBlindBoxDrawEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/entity/FitBlindBoxDrawEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitBlindBoxItemEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/entity/FitBlindBoxItemEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryAccountMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.fitmystery.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/mapper/FitMysteryAccountMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryActivityMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.fitmystery.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/mapper/FitMysteryActivityMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryBoxMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.fitmystery.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/mapper/FitMysteryBoxMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryPurchaseMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.fitmystery.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/mapper/FitMysteryPurchaseMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryReportMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.fitmystery.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/domain/mapper/FitMysteryReportMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryAppModule
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/FitMysteryAppModule.java`
- 中文说明：App 模块定义：声明应用编码、名称、启用状态和模块能力。
- 公开方法：
  - `public String appCode()`：执行 FitMysteryAppModule 中的 appCode 业务逻辑。
  - `public String internalDomain()`：执行 FitMysteryAppModule 中的 internalDomain 业务逻辑。
  - `public String tablePrefix()`：执行 FitMysteryAppModule 中的 tablePrefix 业务逻辑。
  - `public String apiPrefix()`：执行 FitMysteryAppModule 中的 apiPrefix 业务逻辑。
  - `public Optional<AppDefinition> definition()`：执行 FitMysteryAppModule 中的 definition 业务逻辑。

### FitMysteryModuleMarker
- 类型：`interface`
- 包名：`com.apphub.backend.apps.fitmystery`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/FitMysteryModuleMarker.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryPurchaseController
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.purchase`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/purchase/FitMysteryPurchaseController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### FitMysteryPurchaseService
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.purchase`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/purchase/FitMysteryPurchaseService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> verifyAndGrant(Long userId, PurchaseVerifyRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public Map<String, Object> grantConsumableFromVerifiedNotification(Long userId, String productId, String transactionId, String originalTransactionId)`：执行 FitMysteryPurchaseService 中的 grantConsumableFromVerifiedNotification 业务逻辑。

### FitMysteryReportController
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.report`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public FitMysteryApiEnvelope<Map<String, Object>> access(HttpServletRequest request)`：执行 FitMysteryReportController 中的 access 业务逻辑。

### FitMysteryReportService
- 类型：`class`
- 包名：`com.apphub.backend.apps.fitmystery.report`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> access(Long userId)`：执行 FitMysteryReportService 中的 access 业务逻辑。
  - `public Map<String, Object> authorize(Long userId, AuthorizeReportGenerationRequest request)`：执行 FitMysteryReportService 中的 authorize 业务逻辑。
  - `public Map<String, Object> localReportPolicy(Long userId, String type, String anchor)`：执行 FitMysteryReportService 中的 localReportPolicy 业务逻辑。


## App 模块：reading

### ReadingAnnouncementService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.announcement.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/announcement/service/ReadingAnnouncementService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public List<AnnouncementView> listRecent(int windowDays)`：执行 ReadingAnnouncementService 中的 listRecent 业务逻辑。
  - `public List<AnnouncementView> listRecent(int windowDays, String scene, String locale, String appVersion, String planCode)`：执行 ReadingAnnouncementService 中的 listRecent 业务逻辑。

### ReadingAppStoreWebhookCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.appstore.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/appstore/controller/ReadingAppStoreWebhookCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<AppStoreNotificationAcceptedView> notifications(@RequestBody Map<String, Object> payload)`：执行 ReadingAppStoreWebhookCompatController 中的 notifications 业务逻辑。

### ReadingBillingCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.billing.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<ReadingCompatService.AccountEntitlementView> entitlement(@Parameter(hidden = true) HttpServletRequest request)`：执行 ReadingBillingCompatController 中的 entitlement 业务逻辑。
  - `public ApiResponse<ReadingCompatService.EntitlementRefreshView> refreshEntitlement(@Parameter(hidden = true) HttpServletRequest request)`：更新、刷新或重建业务状态。
  - `public ApiResponse<ReadingCompatService.SubscriptionStatusView> subscriptionStatus(@Parameter(hidden = true) HttpServletRequest request)`：执行 ReadingBillingCompatController 中的 subscriptionStatus 业务逻辑。

### ReadingAuthenticatedUser
- 类型：`record`
- 包名：`com.apphub.backend.apps.reading.common`
- 文件：`src/main/java/com/apphub/backend/apps/reading/common/ReadingAuthenticatedUser.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public Long userId()`：执行 ReadingAuthenticatedUser 中的 userId 业务逻辑。

### ReadingAuthenticatedUserResolver
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.common`
- 文件：`src/main/java/com/apphub/backend/apps/reading/common/ReadingAuthenticatedUserResolver.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public ReadingAuthenticatedUser require(HttpServletRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public ReadingAuthenticatedUser resolveOptional(HttpServletRequest request)`：执行 ReadingAuthenticatedUserResolver 中的 resolveOptional 业务逻辑。

### ReadingAccountCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingAccountCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<ReadingCompatService.AccountStateView> state(@Parameter(hidden = true) HttpServletRequest request)`：查询指定对象、配置、列表或当前上下文数据。
  - `public ApiResponse<ReadingCompatService.HomeSummaryView> homeSummary(@Parameter(hidden = true) HttpServletRequest request)`：执行 ReadingAccountCompatController 中的 homeSummary 业务逻辑。

### ReadingAnnouncementCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingAnnouncementCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingChildProfileCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingChildProfileCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<List<ReadingCompatService.ChildView>> children(@Parameter(hidden = true) HttpServletRequest request)`：执行 ReadingChildProfileCompatController 中的 children 业务逻辑。

### ReadingDeletionVerificationCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingDeletionVerificationCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingDeviceCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingDeviceCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingFeedbackCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingFeedbackCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingLearningCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingLearningCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingOcrCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingOcrCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingPreferenceCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPreferenceCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<ReadingPreferenceService.PreferenceView> me(HttpServletRequest request)`：执行 ReadingPreferenceCompatController 中的 me 业务逻辑。

### ReadingPublicCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPublicCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<ReadingCompatService.BootstrapConfigView> bootstrap()`：创建或初始化业务数据。
  - `public ApiResponse<List<ReadingCompatService.PlanView>> plans()`：执行 ReadingPublicCompatController 中的 plans 业务逻辑。
  - `public ApiResponse<List<ReadingCompatService.LegalDocView>> legalDocs()`：执行 ReadingPublicCompatController 中的 legalDocs 业务逻辑。

### ReadingReviewCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingReviewCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<List<ReadingCompatService.ReviewCardView>> today(@Parameter(hidden = true) HttpServletRequest request)`：组装、转换或映射数据结构。

### ReadingTtsCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingTtsCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUsageCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingUsageCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<ReadingUsageService.FamilyUsageSummaryView> familySummary(HttpServletRequest request)`：执行 ReadingUsageCompatController 中的 familySummary 业务逻辑。
  - `public ApiResponse<ReadingUsageService.UsageRetentionCleanupReceipt> cleanupRetention(HttpServletRequest request)`：执行 ReadingUsageCompatController 中的 cleanupRetention 业务逻辑。

### ReadingWeeklyReportAdminController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingWeeklyReportAdminController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingWeeklyReportCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.controller`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingWeeklyReportCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingCloudUsageService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingCloudUsageService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public CloudUsageDecision ensureQuota(Long userId, String serviceType)`：执行 ReadingCloudUsageService 中的 ensureQuota 业务逻辑。
  - `public CloudUsageDecision consume(Long userId, String serviceType)`：执行 ReadingCloudUsageService 中的 consume 业务逻辑。

### ReadingCompatService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingCompatService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public BootstrapConfigView bootstrap()`：创建或初始化业务数据。
  - `public List<PlanView> plans()`：执行 ReadingCompatService 中的 plans 业务逻辑。
  - `public List<LegalDocView> legalDocs()`：执行 ReadingCompatService 中的 legalDocs 业务逻辑。
  - `public AccountStateView accountState(ReadingAuthenticatedUser user)`：执行 ReadingCompatService 中的 accountState 业务逻辑。
  - `public HomeSummaryView homeSummary(ReadingAuthenticatedUser user)`：执行 ReadingCompatService 中的 homeSummary 业务逻辑。
  - `public CreateChildReceipt createChild(ReadingAuthenticatedUser user, ChildMutationRequest request)`：创建或初始化业务数据。
  - `public CreateChildReceipt updateChild(ReadingAuthenticatedUser user, String childId, ChildMutationRequest request)`：更新、刷新或重建业务状态。
  - `public List<ChildView> children(ReadingAuthenticatedUser user)`：执行 ReadingCompatService 中的 children 业务逻辑。
  - `public DailyLearningTaskFeedView dailyTaskFeed(ReadingAuthenticatedUser user, String childId)`：执行 ReadingCompatService 中的 dailyTaskFeed 业务逻辑。
  - `public DailyLearningTaskCompletionView completeDailyTask(ReadingAuthenticatedUser user, String taskId, DailyTaskCompleteRequest request)`：执行 ReadingCompatService 中的 completeDailyTask 业务逻辑。
  - `public List<ReviewCardView> todayReviewCards(ReadingAuthenticatedUser user)`：组装、转换或映射数据结构。
  - `public CreateReviewCardReceipt createReviewCard(ReadingAuthenticatedUser user, CreateReviewCardRequest request)`：创建或初始化业务数据。
  - `public ReviewEventReceipt recordReviewEvent(ReadingAuthenticatedUser user, ReviewEventRequest request)`：接收并处理客户端提交的数据或动作。
  - `public WeeklyParentReportView weeklyReport(ReadingAuthenticatedUser user, String childId, String scope)`：执行 ReadingCompatService 中的 weeklyReport 业务逻辑。
  - `public WeeklyReportHistoryView weeklyHistory(ReadingAuthenticatedUser user, String childId, String scope)`：执行 ReadingCompatService 中的 weeklyHistory 业务逻辑。
  - `public FeedbackSubmissionReceipt submitFeedback(ReadingAuthenticatedUser userOrNull, FeedbackSubmitRequest request)`：接收并处理客户端提交的数据或动作。
  - `public OcrExtractReceipt buildCloudOcrQuotaBlocked(CloudUsageDecision decision)`：组装、转换或映射数据结构。
  - `public OcrExtractReceipt buildCloudOcrUnavailable(ReadingAuthenticatedUser user, OcrExtractRequest request, CloudUsageDecision decision)`：组装、转换或映射数据结构。
  - `public CloudSpeechReceipt buildCloudSpeechQuotaBlocked(CloudUsageDecision decision, CloudSpeechRequest request)`：组装、转换或映射数据结构。
  - `public CloudSpeechReceipt buildCloudSpeechUnavailable(CloudUsageDecision decision, CloudSpeechRequest request)`：组装、转换或映射数据结构。
  - `public OcrExtractReceipt buildCloudOcrResult(OcrProviderResult providerResult, CloudUsageDecision decision)`：组装、转换或映射数据结构。
  - `public CloudSpeechReceipt buildCloudSpeechResult(TtsProviderResult providerResult, CloudUsageDecision decision)`：组装、转换或映射数据结构。
  - `public SysEmailVerificationService.EmailVerificationTicketView requestDeletionCode(ReadingAuthenticatedUser user, String emailOverride)`：执行 ReadingCompatService 中的 requestDeletionCode 业务逻辑。
  - `public DeletionRequestResponse deleteAccount(ReadingAuthenticatedUser user, DeletionRequest request)`：删除、退出或撤销相关资源。
  - `public SubscriptionStatusView subscriptionStatus(ReadingAuthenticatedUser user)`：执行 ReadingCompatService 中的 subscriptionStatus 业务逻辑。
  - `public EntitlementRefreshView refreshEntitlement(ReadingAuthenticatedUser user)`：更新、刷新或重建业务状态。
  - `public IntakeReceipt intakeReceipt(ReadingAuthenticatedUser user, Long intakeId, String sourceType, String status, String verificationStatus, String productId)`：执行 ReadingCompatService 中的 intakeReceipt 业务逻辑。
  - `public AccountStateView accountState(Long userId, String provider)`：执行 ReadingCompatService 中的 accountState 业务逻辑。
  - `public boolean hasActivity()`：执行 ReadingCompatService 中的 hasActivity 业务逻辑。

### ReadingDeviceEventService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingDeviceEventService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public DeviceEventReceipt record(ReadingAuthenticatedUser userOrNull, DeviceEventRequest request)`：接收并处理客户端提交的数据或动作。

### ReadingPreferenceService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingPreferenceService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public PreferenceView get(ReadingAuthenticatedUser user)`：查询指定对象、配置、列表或当前上下文数据。
  - `public PreferenceView update(ReadingAuthenticatedUser user, PreferencePatchRequest request)`：更新、刷新或重建业务状态。

### ReadingUsagePolicyService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingUsagePolicyService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public UsagePolicyView currentPolicy()`：查询指定对象、配置、列表或当前上下文数据。

### ReadingUsageService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingUsageService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public UsageSessionStartReceipt startSession(ReadingAuthenticatedUser user, UsageSessionStartRequest request)`：执行 ReadingUsageService 中的 startSession 业务逻辑。
  - `public UsageSessionEndReceipt endSession(ReadingAuthenticatedUser user, UsageSessionEndRequest request)`：执行 ReadingUsageService 中的 endSession 业务逻辑。
  - `public ChildUsageSummaryView childSummary(ReadingAuthenticatedUser user, String childId)`：执行 ReadingUsageService 中的 childSummary 业务逻辑。
  - `public FamilyUsageSummaryView familySummary(ReadingAuthenticatedUser user)`：执行 ReadingUsageService 中的 familySummary 业务逻辑。
  - `public UsageRetentionCleanupReceipt cleanupRetentionForUser(ReadingAuthenticatedUser user)`：执行 ReadingUsageService 中的 cleanupRetentionForUser 业务逻辑。

### ReadingWeeklyReportAccessConfigService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingWeeklyReportAccessConfigService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public WeeklyReportAccessConfig current()`：查询指定对象、配置、列表或当前上下文数据。
  - `public WeeklyReportAccessConfig refresh()`：更新、刷新或重建业务状态。
  - `public PlanWeeklyReportAccess plan(String planCode)`：执行 ReadingWeeklyReportAccessConfigService 中的 plan 业务逻辑。
  - `public String tierFor(String planCode)`：执行 ReadingWeeklyReportAccessConfigService 中的 tierFor 业务逻辑。
  - `public boolean moduleEnabled(String planCode, String moduleCode)`：执行 ReadingWeeklyReportAccessConfigService 中的 moduleEnabled 业务逻辑。
  - `public ModuleAccess module(String planCode, String moduleCode)`：执行 ReadingWeeklyReportAccessConfigService 中的 module 业务逻辑。
  - `public int maxItems(String planCode, String moduleCode, int fallback)`：执行 ReadingWeeklyReportAccessConfigService 中的 maxItems 业务逻辑。
  - `public int historyWeeksFor(String planCode)`：查询指定对象、配置、列表或当前上下文数据。
  - `public List<String> planCodes()`：执行 ReadingWeeklyReportAccessConfigService 中的 planCodes 业务逻辑。

### ReadingWeeklyReportSnapshotService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.compat.service`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/service/ReadingWeeklyReportSnapshotService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingAnnouncementEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingAnnouncementEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingChildProfileEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingChildProfileEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingChildUsageDailyEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingChildUsageDailyEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingCloudServiceUsageEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingCloudServiceUsageEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingDailyTaskCompletionEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingDailyTaskCompletionEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingFeedbackTicketEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingFeedbackTicketEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingOcrAuditEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingOcrAuditEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingReviewCardEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingReviewCardEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingReviewEventEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingReviewEventEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingReviewEventV2Entity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingReviewEventV2Entity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUsageSessionEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingUsageSessionEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUsageSessionV2Entity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingUsageSessionV2Entity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUserPreferenceEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingUserPreferenceEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingWeeklyReportSnapshotEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/entity/ReadingWeeklyReportSnapshotEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingAnnouncementMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingAnnouncementMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingChildProfileMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingChildProfileMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingChildUsageDailyMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingChildUsageDailyMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingCloudServiceUsageMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingCloudServiceUsageMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingDailyTaskCompletionMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingDailyTaskCompletionMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingFeedbackTicketMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingFeedbackTicketMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingOcrAuditMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingOcrAuditMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingReviewCardMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingReviewCardMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingReviewEventMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingReviewEventMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingReviewEventV2Mapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingReviewEventV2Mapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUsageSessionMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingUsageSessionMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUsageSessionV2Mapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingUsageSessionV2Mapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingUserPreferenceMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingUserPreferenceMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingWeeklyReportSnapshotMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.reading.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/reading/domain/mapper/ReadingWeeklyReportSnapshotMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingPowerSyncAdapter
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.powersync`
- 文件：`src/main/java/com/apphub/backend/apps/reading/powersync/ReadingPowerSyncAdapter.java`
- 中文说明：适配器：把通用系统能力转换成具体 app 的实现逻辑。
- 公开方法：
  - `public ReadingAppModule appModule()`：执行 ReadingPowerSyncAdapter 中的 appModule 业务逻辑。
  - `public List<SyncEntitySpec> entities()`：执行 ReadingPowerSyncAdapter 中的 entities 业务逻辑。
  - `public void validateSyncAccess(Long userId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public PowerSyncUploadResult applyBatch(Long userId, String installationId, List<PowerSyncChangeItem> changes)`：接收并处理客户端提交的数据或动作。

### ReadingPowerSyncEntityType
- 类型：`enum`
- 包名：`com.apphub.backend.apps.reading.powersync`
- 文件：`src/main/java/com/apphub/backend/apps/reading/powersync/ReadingPowerSyncEntityType.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public String code()`：执行 ReadingPowerSyncEntityType 中的 code 业务逻辑。
  - `public static ReadingPowerSyncEntityType fromCode(String value)`：执行 ReadingPowerSyncEntityType 中的 fromCode 业务逻辑。

### ReadingPowerSyncMapper
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.powersync`
- 文件：`src/main/java/com/apphub/backend/apps/reading/powersync/ReadingPowerSyncMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ReadingPowerSyncValidator
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.powersync`
- 文件：`src/main/java/com/apphub/backend/apps/reading/powersync/ReadingPowerSyncValidator.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public void requireCloudSyncEnabled(Long userId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public void validateChildCreateAllowed(Long userId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public void validateReviewCardCreateAllowed(Long userId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public ReadingChildProfileEntity requireOwnedChild(Long userId, String childId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public ReadingChildProfileEntity requireActiveChild(Long userId, String childId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public ReadingReviewCardEntity requireOwnedCard(Long userId, String cardId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public ReadingReviewCardEntity requireActiveCard(Long userId, String cardId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public String reasonCode()`：执行 ReadingPowerSyncValidator 中的 reasonCode 业务逻辑。

### ReadingBailianOcrProvider
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.provider`
- 文件：`src/main/java/com/apphub/backend/apps/reading/provider/ReadingBailianOcrProvider.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public OcrProviderResult extract(String imageBase64, String mimeType, String promptOverride)`：执行 ReadingBailianOcrProvider 中的 extract 业务逻辑。

### ReadingBailianTtsProvider
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.provider`
- 文件：`src/main/java/com/apphub/backend/apps/reading/provider/ReadingBailianTtsProvider.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public TtsProviderResult synthesize(String text, String languageCode, Float rateOverride)`：执行 ReadingBailianTtsProvider 中的 synthesize 业务逻辑。
  - `public void onOpen(WebSocket webSocket)`：执行 ReadingBailianTtsProvider 中的 onOpen 业务逻辑。
  - `public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)`：执行 ReadingBailianTtsProvider 中的 onText 业务逻辑。
  - `public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last)`：执行 ReadingBailianTtsProvider 中的 onBinary 业务逻辑。
  - `public void onError(WebSocket webSocket, Throwable error)`：执行 ReadingBailianTtsProvider 中的 onError 业务逻辑。

### ReadingCloudProviderConfigService
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading.provider`
- 文件：`src/main/java/com/apphub/backend/apps/reading/provider/ReadingCloudProviderConfigService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public CloudOcrConfig ocr()`：执行 ReadingCloudProviderConfigService 中的 ocr 业务逻辑。
  - `public CloudTtsConfig tts()`：执行 ReadingCloudProviderConfigService 中的 tts 业务逻辑。

### ReadingAppModule
- 类型：`class`
- 包名：`com.apphub.backend.apps.reading`
- 文件：`src/main/java/com/apphub/backend/apps/reading/ReadingAppModule.java`
- 中文说明：App 模块定义：声明应用编码、名称、启用状态和模块能力。
- 公开方法：
  - `public String appCode()`：执行 ReadingAppModule 中的 appCode 业务逻辑。
  - `public String internalDomain()`：执行 ReadingAppModule 中的 internalDomain 业务逻辑。
  - `public String tablePrefix()`：执行 ReadingAppModule 中的 tablePrefix 业务逻辑。
  - `public String apiPrefix()`：执行 ReadingAppModule 中的 apiPrefix 业务逻辑。
  - `public Optional<AppDefinition> definition()`：执行 ReadingAppModule 中的 definition 业务逻辑。


## App 模块：saving

### SavingApiEnvelope
- 类型：`record`
- 包名：`com.apphub.backend.apps.saving.api`
- 文件：`src/main/java/com/apphub/backend/apps/saving/api/SavingApiEnvelope.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public static <T> SavingApiEnvelope<T> ok(String requestId, T data)`：执行 SavingApiEnvelope 中的 ok 业务逻辑。

### SavingAppStoreCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.appstore.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/appstore/controller/SavingAppStoreCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<AppStoreNotificationAcceptedView> notifications(@Valid @RequestBody SavingNotificationRequest request)`：执行 SavingAppStoreCompatController 中的 notifications 业务逻辑。

### SavingBillingCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.billing.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/billing/controller/SavingBillingCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public SavingApiEnvelope<Map<String, Object>> entitlements(HttpServletRequest request)`：执行 SavingBillingCompatController 中的 entitlements 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> refreshEntitlements(HttpServletRequest request)`：更新、刷新或重建业务状态。
  - `public SavingApiEnvelope<Map<String, Object>> verify(@Valid @RequestBody SavingPurchaseVerifyRequest request, HttpServletRequest httpServletRequest)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public SavingApiEnvelope<Map<String, Object>> restore(@Valid @RequestBody SavingPurchaseRestoreRequest request, HttpServletRequest httpServletRequest)`：执行 SavingBillingCompatController 中的 restore 业务逻辑。

### SavingAccountController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingAccountController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public SavingApiEnvelope<Map<String, Object>> deleteCurrentAccount(HttpServletRequest request)`：删除、退出或撤销相关资源。

### SavingConfigController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public SavingApiEnvelope<Map<String, Object>> categories(@RequestParam(defaultValue = "zh-Hans") String locale)`：执行 SavingConfigController 中的 categories 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> entitlementMatrix(@RequestParam(defaultValue = "zh-Hans") String locale)`：执行 SavingConfigController 中的 entitlementMatrix 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> reportAccess()`：执行 SavingConfigController 中的 reportAccess 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> reportHistoryPolicy(@RequestParam(defaultValue = "zh-Hans") String locale)`：执行 SavingConfigController 中的 reportHistoryPolicy 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> onboarding(@RequestParam(defaultValue = "zh-Hans") String locale)`：执行 SavingConfigController 中的 onboarding 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> appReviewNotes()`：执行 SavingConfigController 中的 appReviewNotes 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> appStorePrivacyLabels()`：执行 SavingConfigController 中的 appStorePrivacyLabels 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> appReviewMaterials()`：执行 SavingConfigController 中的 appReviewMaterials 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> appStoreConnectFieldMapping()`：执行 SavingConfigController 中的 appStoreConnectFieldMapping 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> appStoreSubmissionChecklist()`：执行 SavingConfigController 中的 appStoreSubmissionChecklist 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> legalDocumentConsistencyPolicy()`：执行 SavingConfigController 中的 legalDocumentConsistencyPolicy 业务逻辑。
  - `public SavingApiEnvelope<Map<String, Object>> reportHistoryProPolicy()`：执行 SavingConfigController 中的 reportHistoryProPolicy 业务逻辑。

### SavingDashboardController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingDashboardController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public void overview()`：查询指定对象、配置、列表或当前上下文数据。

### SavingFinancialRecordController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public void list()`：查询列表数据。
  - `public void createExpense()`：创建或初始化业务数据。
  - `public void createSaving()`：创建或初始化业务数据。
  - `public void updateExpense()`：更新、刷新或重建业务状态。
  - `public void updateSaving()`：更新、刷新或重建业务状态。
  - `public void delete()`：删除、退出或撤销相关资源。

### SavingReportController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingReportController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public void weekly()`：执行 SavingReportController 中的 weekly 业务逻辑。
  - `public void monthly()`：执行 SavingReportController 中的 monthly 业务逻辑。

### SavingExpenseRecordEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/saving/domain/entity/SavingExpenseRecordEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SavingSavingRecordEntity
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.domain.entity`
- 文件：`src/main/java/com/apphub/backend/apps/saving/domain/entity/SavingSavingRecordEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SavingFinanceMapper
- 类型：`interface`
- 包名：`com.apphub.backend.apps.saving.domain.mapper`
- 文件：`src/main/java/com/apphub/backend/apps/saving/domain/mapper/SavingFinanceMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SavingAppModule
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving`
- 文件：`src/main/java/com/apphub/backend/apps/saving/SavingAppModule.java`
- 中文说明：App 模块定义：声明应用编码、名称、启用状态和模块能力。
- 公开方法：
  - `public String appCode()`：执行 SavingAppModule 中的 appCode 业务逻辑。
  - `public String internalDomain()`：执行 SavingAppModule 中的 internalDomain 业务逻辑。
  - `public String tablePrefix()`：执行 SavingAppModule 中的 tablePrefix 业务逻辑。
  - `public String apiPrefix()`：执行 SavingAppModule 中的 apiPrefix 业务逻辑。
  - `public Optional<AppDefinition> definition()`：执行 SavingAppModule 中的 definition 业务逻辑。

### SavingAccountDeletionService
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.service`
- 文件：`src/main/java/com/apphub/backend/apps/saving/service/SavingAccountDeletionService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> deleteCurrentSavingAccount(Long userId)`：删除、退出或撤销相关资源。

### SavingConfigService
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.service`
- 文件：`src/main/java/com/apphub/backend/apps/saving/service/SavingConfigService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> paywall(String trigger, String platform, String locale)`：执行 SavingConfigService 中的 paywall 业务逻辑。
  - `public Map<String, Object> recordCategories(String locale)`：接收并处理客户端提交的数据或动作。
  - `public Map<String, Object> entitlementMatrix(String locale)`：执行 SavingConfigService 中的 entitlementMatrix 业务逻辑。
  - `public Map<String, Object> featureFlags(String platform, String appVersion)`：执行 SavingConfigService 中的 featureFlags 业务逻辑。
  - `public Map<String, Object> reportAccess()`：执行 SavingConfigService 中的 reportAccess 业务逻辑。
  - `public Map<String, Object> reportHistoryPolicy(String locale)`：执行 SavingConfigService 中的 reportHistoryPolicy 业务逻辑。
  - `public Map<String, Object> onboarding(String locale)`：执行 SavingConfigService 中的 onboarding 业务逻辑。
  - `public Map<String, Object> appReviewNotes()`：执行 SavingConfigService 中的 appReviewNotes 业务逻辑。
  - `public Map<String, Object> appStorePrivacyLabels()`：执行 SavingConfigService 中的 appStorePrivacyLabels 业务逻辑。
  - `public Map<String, Object> appReviewMaterials()`：执行 SavingConfigService 中的 appReviewMaterials 业务逻辑。
  - `public Map<String, Object> appStoreConnectFieldMapping()`：执行 SavingConfigService 中的 appStoreConnectFieldMapping 业务逻辑。
  - `public Map<String, Object> appStoreSubmissionChecklist()`：执行 SavingConfigService 中的 appStoreSubmissionChecklist 业务逻辑。
  - `public Map<String, Object> legalDocumentConsistencyPolicy()`：执行 SavingConfigService 中的 legalDocumentConsistencyPolicy 业务逻辑。
  - `public Map<String, Object> reportHistoryProPolicy()`：执行 SavingConfigService 中的 reportHistoryProPolicy 业务逻辑。
  - `public Object copy(String key, Object defaultValue)`：执行 SavingConfigService 中的 copy 业务逻辑。
  - `public Map<String, Object> namespace(String namespaceCode)`：执行 SavingConfigService 中的 namespace 业务逻辑。

### SavingEntitlementService
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.service`
- 文件：`src/main/java/com/apphub/backend/apps/saving/service/SavingEntitlementService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public String currentPlanCode(Long userId)`：查询指定对象、配置、列表或当前上下文数据。
  - `public boolean isPaidActive(Long userId)`：执行 SavingEntitlementService 中的 isPaidActive 业务逻辑。

### SavingFinanceService
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.service`
- 文件：`src/main/java/com/apphub/backend/apps/saving/service/SavingFinanceService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public Map<String, Object> createExpense(Long userId, ExpenseUpsertRequest request)`：创建或初始化业务数据。
  - `public Map<String, Object> createSaving(Long userId, SavingUpsertRequest request)`：创建或初始化业务数据。
  - `public Map<String, Object> updateExpense(Long userId, String recordId, ExpenseUpsertRequest request)`：更新、刷新或重建业务状态。
  - `public Map<String, Object> updateSaving(Long userId, String recordId, SavingUpsertRequest request)`：更新、刷新或重建业务状态。
  - `public Map<String, Object> delete(Long userId, String recordType, String recordId)`：删除、退出或撤销相关资源。
  - `public Map<String, Object> list(Long userId, String recordType, int pageSize, OffsetDateTime startAt, OffsetDateTime endAt)`：查询列表数据。
  - `public Map<String, Object> dashboard(Long userId, String locale, String timezone, int recentLimit)`：执行 SavingFinanceService 中的 dashboard 业务逻辑。
  - `public Map<String, Object> report(Long userId, String reportType, ReportRequest request)`：执行 SavingFinanceService 中的 report 业务逻辑。
  - `public Map<String, Object> report(Long userId, String reportType, ReportRequest request, String planCode)`：执行 SavingFinanceService 中的 report 业务逻辑。

### SavingRequestSupport
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.service`
- 文件：`src/main/java/com/apphub/backend/apps/saving/service/SavingRequestSupport.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public Long requireUserId(HttpServletRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public String requestId()`：执行 SavingRequestSupport 中的 requestId 业务逻辑。

### SavingUserBootstrapCompatController
- 类型：`class`
- 包名：`com.apphub.backend.apps.saving.user.controller`
- 文件：`src/main/java/com/apphub/backend/apps/saving/user/controller/SavingUserBootstrapCompatController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<SavingBootstrapResponse> bootstrap(@Valid @RequestBody SavingBootstrapRequest request)`：创建或初始化业务数据。


## 公共基础模块

### MybatisPlusConfig
- 类型：`class`
- 包名：`com.apphub.backend.common.config`
- 文件：`src/main/java/com/apphub/backend/common/config/MybatisPlusConfig.java`
- 中文说明：配置类：注册 Spring Bean、绑定配置属性或声明框架配置。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### OpenApiConfig
- 类型：`class`
- 包名：`com.apphub.backend.common.config`
- 文件：`src/main/java/com/apphub/backend/common/config/OpenApiConfig.java`
- 中文说明：配置类：注册 Spring Bean、绑定配置属性或声明框架配置。
- 公开方法：
  - `public OpenAPI openAPI()`：接收并处理客户端提交的数据或动作。

### OpsTokenFilter
- 类型：`class`
- 包名：`com.apphub.backend.common.filter`
- 文件：`src/main/java/com/apphub/backend/common/filter/OpsTokenFilter.java`
- 中文说明：Servlet 过滤器：处理请求链路中的鉴权、追踪或通用拦截逻辑。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### TraceFilter
- 类型：`class`
- 包名：`com.apphub.backend.common.filter`
- 文件：`src/main/java/com/apphub/backend/common/filter/TraceFilter.java`
- 中文说明：Servlet 过滤器：处理请求链路中的鉴权、追踪或通用拦截逻辑。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### ApiResponse
- 类型：`record`
- 包名：`com.apphub.backend.common.response`
- 文件：`src/main/java/com/apphub/backend/common/response/ApiResponse.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：
  - `public static <T> ApiResponse<T> success(String requestId, T data)`：执行 ApiResponse 中的 success 业务逻辑。

### Sha256HashService
- 类型：`class`
- 包名：`com.apphub.backend.common.util`
- 文件：`src/main/java/com/apphub/backend/common/util/Sha256HashService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public String hash(String rawValue)`：执行 Sha256HashService 中的 hash 业务逻辑。


## root

### BackendApplication
- 类型：`class`
- 包名：`com.apphub.backend`
- 文件：`src/main/java/com/apphub/backend/BackendApplication.java`
- 中文说明：Spring Boot 启动入口。
- 公开方法：
  - `public static void main(String[] args)`：执行 BackendApplication 中的 main 业务逻辑。


## 共享能力模块

### AppleJwtTokenFactory
- 类型：`class`
- 包名：`com.apphub.backend.shared.apple`
- 文件：`src/main/java/com/apphub/backend/shared/apple/AppleJwtTokenFactory.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public String createSignInClientSecret(String teamId, String clientId, String keyId, String privateKeyPem, String audience)`：创建或初始化业务数据。
  - `public String createAppStoreServerApiToken(String issuerId, String bundleId, String keyId, String privateKeyPem)`：创建或初始化业务数据。
  - `public Map<String, Object> inspectClaims(String jwt)`：执行 AppleJwtTokenFactory 中的 inspectClaims 业务逻辑。

### CachedRemoteJwkProvider
- 类型：`class`
- 包名：`com.apphub.backend.shared.apple`
- 文件：`src/main/java/com/apphub/backend/shared/apple/CachedRemoteJwkProvider.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public JwkResolveResult resolve(String jwksUrl, String keyId)`：执行 CachedRemoteJwkProvider 中的 resolve 业务逻辑。


## 系统公共模块：app

### AppCatalogProperties
- 类型：`class`
- 包名：`com.apphub.backend.sys.app.config`
- 文件：`src/main/java/com/apphub/backend/sys/app/config/AppCatalogProperties.java`
- 中文说明：配置类：注册 Spring Bean、绑定配置属性或声明框架配置。
- 公开方法：
  - `public List<String> getSupported()`：执行 AppCatalogProperties 中的 getSupported 业务逻辑。
  - `public void setSupported(List<String> supported)`：执行 AppCatalogProperties 中的 setSupported 业务逻辑。
  - `public Map<String, String> getDefinitions()`：执行 AppCatalogProperties 中的 getDefinitions 业务逻辑。
  - `public void setDefinitions(Map<String, String> definitions)`：执行 AppCatalogProperties 中的 setDefinitions 业务逻辑。

### SystemController
- 类型：`class`
- 包名：`com.apphub.backend.sys.app.controller`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<Map<String, Object>> healthz()`：执行 SystemController 中的 healthz 业务逻辑。
  - `public ApiResponse<List<AppDefinition>> apps()`：查询列表数据。
  - `public ApiResponse<AppDefinition> app(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`：查询指定对象、配置、列表或当前上下文数据。
  - `public ApiResponse<AppAppleReadinessView> appleReadiness(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`：执行 SystemController 中的 appleReadiness 业务逻辑。
  - `public ApiResponse<AppAppleTokenStorageView> appleTokenStorage(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`：执行 SystemController 中的 appleTokenStorage 业务逻辑。
  - `public ApiResponse<AppAppleOpsGateView> appleOpsGate(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`：执行 SystemController 中的 appleOpsGate 业务逻辑。
  - `public ApiResponse<List<AppAppleOpsGateView>> appleOpsGates()`：执行 SystemController 中的 appleOpsGates 业务逻辑。
  - `public ApiResponse<SystemReleaseGateView> releaseGate()`：执行 SystemController 中的 releaseGate 业务逻辑。
  - `public ApiResponse<SystemPublicSurfaceView> publicSurface()`：执行 SystemController 中的 publicSurface 业务逻辑。
  - `public ApiResponse<EntitlementObservabilityView> entitlementObservability(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`：执行 SystemController 中的 entitlementObservability 业务逻辑。

### AppAppleOpsGateView
- 类型：`record`
- 包名：`com.apphub.backend.sys.app.model`
- 文件：`src/main/java/com/apphub/backend/sys/app/model/AppAppleOpsGateView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppAppleReadinessView
- 类型：`record`
- 包名：`com.apphub.backend.sys.app.model`
- 文件：`src/main/java/com/apphub/backend/sys/app/model/AppAppleReadinessView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppAppleTokenStorageView
- 类型：`record`
- 包名：`com.apphub.backend.sys.app.model`
- 文件：`src/main/java/com/apphub/backend/sys/app/model/AppAppleTokenStorageView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppDefinition
- 类型：`record`
- 包名：`com.apphub.backend.sys.app.model`
- 文件：`src/main/java/com/apphub/backend/sys/app/model/AppDefinition.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SystemPublicSurfaceView
- 类型：`record`
- 包名：`com.apphub.backend.sys.app.model`
- 文件：`src/main/java/com/apphub/backend/sys/app/model/SystemPublicSurfaceView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SystemReleaseGateView
- 类型：`record`
- 包名：`com.apphub.backend.sys.app.model`
- 文件：`src/main/java/com/apphub/backend/sys/app/model/SystemReleaseGateView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppAppleReadinessService
- 类型：`class`
- 包名：`com.apphub.backend.sys.app.service`
- 文件：`src/main/java/com/apphub/backend/sys/app/service/AppAppleReadinessService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public AppAppleReadinessView inspect(AppDefinition definition)`：执行 AppAppleReadinessService 中的 inspect 业务逻辑。

### AppDefinitionService
- 类型：`class`
- 包名：`com.apphub.backend.sys.app.service`
- 文件：`src/main/java/com/apphub/backend/sys/app/service/AppDefinitionService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public void afterPropertiesSet()`：执行 AppDefinitionService 中的 afterPropertiesSet 业务逻辑。
  - `public List<AppDefinition> list()`：查询列表数据。
  - `public Optional<AppDefinition> get(String appCode)`：查询指定对象、配置、列表或当前上下文数据。

### SystemProductionConfigurationGuard
- 类型：`class`
- 包名：`com.apphub.backend.sys.app.service`
- 文件：`src/main/java/com/apphub/backend/sys/app/service/SystemProductionConfigurationGuard.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public void afterPropertiesSet()`：执行 SystemProductionConfigurationGuard 中的 afterPropertiesSet 业务逻辑。
  - `public void validateOrThrow()`：校验入参、权限、会话或业务条件，不满足时抛出异常。


## 系统公共模块：appstore

### SysAppStoreController
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.controller`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/controller/SysAppStoreController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<AppStoreNotificationObservabilityView> notificationObservability(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`：执行 SysAppStoreController 中的 notificationObservability 业务逻辑。

### SysAppStoreNotificationEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.entity`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/entity/SysAppStoreNotificationEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysAppStoreNotificationMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.appstore.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/mapper/SysAppStoreNotificationMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppStoreNotificationAcceptedView
- 类型：`record`
- 包名：`com.apphub.backend.sys.appstore.model`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/model/AppStoreNotificationAcceptedView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppStoreNotificationIngestRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.appstore.model`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/model/AppStoreNotificationIngestRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppStoreNotificationObservabilityView
- 类型：`record`
- 包名：`com.apphub.backend.sys.appstore.model`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/model/AppStoreNotificationObservabilityView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppStoreJwsVerificationService
- 类型：`interface`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/AppStoreJwsVerificationService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppStoreServerApiClient
- 类型：`interface`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/AppStoreServerApiClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public boolean isReadyForServerApi()`：执行 AppStoreServerApiClient 中的 isReadyForServerApi 业务逻辑。
  - `public boolean isVerified()`：执行 AppStoreServerApiClient 中的 isVerified 业务逻辑。

### AppStoreSignedJwsVerifier
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/AppStoreSignedJwsVerifier.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public VerifiedJws verify(String compactJws, String fieldName)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public String detailStatus()`：执行 AppStoreSignedJwsVerifier 中的 detailStatus 业务逻辑。
  - `public String note()`：执行 AppStoreSignedJwsVerifier 中的 note 业务逻辑。
  - `public Map<String, String> diagnostics()`：执行 AppStoreSignedJwsVerifier 中的 diagnostics 业务逻辑。

### LiveAppStoreServerApiClient
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/LiveAppStoreServerApiClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public LookupResult lookup(LookupCommand command, AppStoreConfiguration configuration)`：执行 LiveAppStoreServerApiClient 中的 lookup 业务逻辑。

### PlaceholderAppStoreJwsVerificationService
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/PlaceholderAppStoreJwsVerificationService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public TransactionVerificationResult verifyTransaction(String signedTransactionInfo, TransactionExpectation expectation)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public NotificationVerificationResult verifyNotification(String signedPayload)`：校验入参、权限、会话或业务条件，不满足时抛出异常。

### SignedAppStoreJwsVerificationService
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/SignedAppStoreJwsVerificationService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public TransactionVerificationResult verifyTransaction(String signedTransactionInfo, TransactionExpectation expectation)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public NotificationVerificationResult verifyNotification(String signedPayload)`：校验入参、权限、会话或业务条件，不满足时抛出异常。

### SysAppStoreNotificationService
- 类型：`class`
- 包名：`com.apphub.backend.sys.appstore.service`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/service/SysAppStoreNotificationService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public AppStoreNotificationObservabilityView describeObservability(String appCode)`：执行 SysAppStoreNotificationService 中的 describeObservability 业务逻辑。
  - `public AppStoreNotificationAcceptedView ingest(String appCode, AppStoreNotificationIngestRequest request)`：执行 SysAppStoreNotificationService 中的 ingest 业务逻辑。


## 系统公共模块：auth

### SysAuthController
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.controller`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<AuthenticatedSessionView> me(@Parameter(hidden = true) HttpServletRequest request)`：执行 SysAuthController 中的 me 业务逻辑。
  - `public ApiResponse<LogoutResultView> logout(@Parameter(hidden = true) HttpServletRequest request)`：删除、退出或撤销相关资源。

### SysAuthProviderTokenEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.entity`
- 文件：`src/main/java/com/apphub/backend/sys/auth/entity/SysAuthProviderTokenEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysAuthSessionEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.entity`
- 文件：`src/main/java/com/apphub/backend/sys/auth/entity/SysAuthSessionEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：
  - `public Long getId()`：执行 SysAuthSessionEntity 中的 getId 业务逻辑。
  - `public void setId(Long id)`：执行 SysAuthSessionEntity 中的 setId 业务逻辑。
  - `public String getAppCode()`：执行 SysAuthSessionEntity 中的 getAppCode 业务逻辑。
  - `public void setAppCode(String appCode)`：执行 SysAuthSessionEntity 中的 setAppCode 业务逻辑。
  - `public Long getUserId()`：执行 SysAuthSessionEntity 中的 getUserId 业务逻辑。
  - `public void setUserId(Long userId)`：执行 SysAuthSessionEntity 中的 setUserId 业务逻辑。
  - `public String getSessionTokenHash()`：执行 SysAuthSessionEntity 中的 getSessionTokenHash 业务逻辑。
  - `public void setSessionTokenHash(String sessionTokenHash)`：执行 SysAuthSessionEntity 中的 setSessionTokenHash 业务逻辑。
  - `public String getSessionSource()`：执行 SysAuthSessionEntity 中的 getSessionSource 业务逻辑。
  - `public void setSessionSource(String sessionSource)`：执行 SysAuthSessionEntity 中的 setSessionSource 业务逻辑。
  - `public String getDeviceId()`：执行 SysAuthSessionEntity 中的 getDeviceId 业务逻辑。
  - `public void setDeviceId(String deviceId)`：执行 SysAuthSessionEntity 中的 setDeviceId 业务逻辑。
  - `public String getClientPlatform()`：执行 SysAuthSessionEntity 中的 getClientPlatform 业务逻辑。
  - `public void setClientPlatform(String clientPlatform)`：执行 SysAuthSessionEntity 中的 setClientPlatform 业务逻辑。
  - `public String getClientVersion()`：执行 SysAuthSessionEntity 中的 getClientVersion 业务逻辑。
  - `public void setClientVersion(String clientVersion)`：执行 SysAuthSessionEntity 中的 setClientVersion 业务逻辑。
  - `public String getStatus()`：执行 SysAuthSessionEntity 中的 getStatus 业务逻辑。
  - `public void setStatus(String status)`：执行 SysAuthSessionEntity 中的 setStatus 业务逻辑。
  - `public OffsetDateTime getExpiresAt()`：执行 SysAuthSessionEntity 中的 getExpiresAt 业务逻辑。
  - `public void setExpiresAt(OffsetDateTime expiresAt)`：执行 SysAuthSessionEntity 中的 setExpiresAt 业务逻辑。
  - `public OffsetDateTime getRevokedAt()`：执行 SysAuthSessionEntity 中的 getRevokedAt 业务逻辑。
  - `public void setRevokedAt(OffsetDateTime revokedAt)`：执行 SysAuthSessionEntity 中的 setRevokedAt 业务逻辑。
  - `public OffsetDateTime getLastSeenAt()`：执行 SysAuthSessionEntity 中的 getLastSeenAt 业务逻辑。
  - `public void setLastSeenAt(OffsetDateTime lastSeenAt)`：执行 SysAuthSessionEntity 中的 setLastSeenAt 业务逻辑。
  - `public OffsetDateTime getCreatedAt()`：执行 SysAuthSessionEntity 中的 getCreatedAt 业务逻辑。
  - `public void setCreatedAt(OffsetDateTime createdAt)`：执行 SysAuthSessionEntity 中的 setCreatedAt 业务逻辑。
  - `public OffsetDateTime getUpdatedAt()`：执行 SysAuthSessionEntity 中的 getUpdatedAt 业务逻辑。
  - `public void setUpdatedAt(OffsetDateTime updatedAt)`：执行 SysAuthSessionEntity 中的 setUpdatedAt 业务逻辑。

### SysEmailVerificationTicketEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.entity`
- 文件：`src/main/java/com/apphub/backend/sys/auth/entity/SysEmailVerificationTicketEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysUserDeviceEventEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.entity`
- 文件：`src/main/java/com/apphub/backend/sys/auth/entity/SysUserDeviceEventEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysUserEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.entity`
- 文件：`src/main/java/com/apphub/backend/sys/auth/entity/SysUserEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：
  - `public Long getId()`：执行 SysUserEntity 中的 getId 业务逻辑。
  - `public void setId(Long id)`：执行 SysUserEntity 中的 setId 业务逻辑。
  - `public String getAppCode()`：执行 SysUserEntity 中的 getAppCode 业务逻辑。
  - `public void setAppCode(String appCode)`：执行 SysUserEntity 中的 setAppCode 业务逻辑。
  - `public String getUserType()`：执行 SysUserEntity 中的 getUserType 业务逻辑。
  - `public void setUserType(String userType)`：执行 SysUserEntity 中的 setUserType 业务逻辑。
  - `public String getDisplayName()`：执行 SysUserEntity 中的 getDisplayName 业务逻辑。
  - `public void setDisplayName(String displayName)`：执行 SysUserEntity 中的 setDisplayName 业务逻辑。
  - `public String getStatus()`：执行 SysUserEntity 中的 getStatus 业务逻辑。
  - `public void setStatus(String status)`：执行 SysUserEntity 中的 setStatus 业务逻辑。
  - `public OffsetDateTime getCreatedAt()`：执行 SysUserEntity 中的 getCreatedAt 业务逻辑。
  - `public void setCreatedAt(OffsetDateTime createdAt)`：执行 SysUserEntity 中的 setCreatedAt 业务逻辑。
  - `public OffsetDateTime getUpdatedAt()`：执行 SysUserEntity 中的 getUpdatedAt 业务逻辑。
  - `public void setUpdatedAt(OffsetDateTime updatedAt)`：执行 SysUserEntity 中的 setUpdatedAt 业务逻辑。

### SysUserIdentityEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.entity`
- 文件：`src/main/java/com/apphub/backend/sys/auth/entity/SysUserIdentityEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysAuthProviderTokenMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/auth/mapper/SysAuthProviderTokenMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysAuthSessionMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/auth/mapper/SysAuthSessionMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysEmailVerificationTicketMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/auth/mapper/SysEmailVerificationTicketMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysUserDeviceEventMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/auth/mapper/SysUserDeviceEventMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysUserIdentityMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/auth/mapper/SysUserIdentityMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysUserMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/auth/mapper/SysUserMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppleExchangePreviewView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/AppleExchangePreviewView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppleExchangeRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/AppleExchangeRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppleRevokeResultView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/AppleRevokeResultView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppleSessionRefreshView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/AppleSessionRefreshView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AuthenticatedSessionView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/AuthenticatedSessionView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AuthSessionIssuedView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/AuthSessionIssuedView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### CurrentUserView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/CurrentUserView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### DecodedAppleIdentityTokenView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/DecodedAppleIdentityTokenView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### DemoSessionCreatedView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/DemoSessionCreatedView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### DemoSessionCreateRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/DemoSessionCreateRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：
  - `public static DemoSessionCreateRequest empty()`：执行 DemoSessionCreateRequest 中的 empty 业务逻辑。

### LogoutResultView
- 类型：`record`
- 包名：`com.apphub.backend.sys.auth.model`
- 文件：`src/main/java/com/apphub/backend/sys/auth/model/LogoutResultView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### AppleAuthorizationCodeExchangeClient
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleAuthorizationCodeExchangeClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public boolean isReadyForExchange()`：执行 AppleAuthorizationCodeExchangeClient 中的 isReadyForExchange 业务逻辑。
  - `public boolean isReadyForIdentityVerification()`：执行 AppleAuthorizationCodeExchangeClient 中的 isReadyForIdentityVerification 业务逻辑。
  - `public boolean isSuccessful()`：执行 AppleAuthorizationCodeExchangeClient 中的 isSuccessful 业务逻辑。

### AppleCredentialEncryptionService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleCredentialEncryptionService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public boolean isReady()`：执行 AppleCredentialEncryptionService 中的 isReady 业务逻辑。
  - `public EncryptionEnvelope encrypt(String plaintext)`：执行 AppleCredentialEncryptionService 中的 encrypt 业务逻辑。
  - `public String decrypt(String nonceBase64, String ciphertextBase64)`：执行 AppleCredentialEncryptionService 中的 decrypt 业务逻辑。

### AppleIdentityTokenDecoder
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleIdentityTokenDecoder.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public DecodedAppleIdentityToken decode(String identityToken)`：执行 AppleIdentityTokenDecoder 中的 decode 业务逻辑。

### AppleIdentityTokenVerifier
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleIdentityTokenVerifier.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public boolean allowsSessionIssue()`：执行 AppleIdentityTokenVerifier 中的 allowsSessionIssue 业务逻辑。

### AppleRefreshTokenVaultService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleRefreshTokenVaultService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public boolean isEncryptionReady()`：执行 AppleRefreshTokenVaultService 中的 isEncryptionReady 业务逻辑。
  - `public CaptureResult capture(SysAuthProviderTokenEntity token, String refreshToken, OffsetDateTime now)`：执行 AppleRefreshTokenVaultService 中的 capture 业务逻辑。
  - `public ResolvedTokenResult resolve(SysAuthProviderTokenEntity token, OffsetDateTime now)`：执行 AppleRefreshTokenVaultService 中的 resolve 业务逻辑。
  - `public void purge(SysAuthProviderTokenEntity token)`：执行 AppleRefreshTokenVaultService 中的 purge 业务逻辑。
  - `public boolean hasEncryptedRefreshToken(SysAuthProviderTokenEntity token)`：执行 AppleRefreshTokenVaultService 中的 hasEncryptedRefreshToken 业务逻辑。
  - `public boolean hasPlaintextRefreshToken(SysAuthProviderTokenEntity token)`：执行 AppleRefreshTokenVaultService 中的 hasPlaintextRefreshToken 业务逻辑。

### AppleTokenRefreshClient
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleTokenRefreshClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public boolean isSuccessful()`：执行 AppleTokenRefreshClient 中的 isSuccessful 业务逻辑。

### AppleTokenRevocationClient
- 类型：`interface`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/AppleTokenRevocationClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public boolean isReadyForRevoke()`：执行 AppleTokenRevocationClient 中的 isReadyForRevoke 业务逻辑。
  - `public boolean isSuccessful()`：执行 AppleTokenRevocationClient 中的 isSuccessful 业务逻辑。

### PublicAuthAccessPolicyService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/PublicAuthAccessPolicyService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public boolean demoSessionsEnabled(AppDefinition appDefinition)`：签发、交换或创建认证会话。
  - `public boolean bootstrapSessionsEnabled(AppDefinition appDefinition)`：创建或初始化业务数据。

### ReadyForIntegrationAppleAuthorizationCodeExchangeClient
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/ReadyForIntegrationAppleAuthorizationCodeExchangeClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public ExchangeResult exchange(ExchangeCommand command, AppleAuthConfiguration configuration)`：签发、交换或创建认证会话。

### ReadyForIntegrationAppleIdentityTokenVerifier
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/ReadyForIntegrationAppleIdentityTokenVerifier.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public VerificationResult verify(String identityToken, VerificationCommand command)`：校验入参、权限、会话或业务条件，不满足时抛出异常。

### ReadyForIntegrationAppleTokenRefreshClient
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/ReadyForIntegrationAppleTokenRefreshClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public RefreshResult refresh(RefreshCommand command, AppleAuthorizationCodeExchangeClient.AppleAuthConfiguration configuration)`：更新、刷新或重建业务状态。

### ReadyForIntegrationAppleTokenRevocationClient
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/ReadyForIntegrationAppleTokenRevocationClient.java`
- 中文说明：外部能力封装：负责对接第三方服务、云服务或远端 API。
- 公开方法：
  - `public RevocationResult revoke(RevokeCommand command, AppleRevokeConfiguration configuration)`：删除、退出或撤销相关资源。

### SessionTokenHashService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/SessionTokenHashService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public String hash(String rawToken)`：执行 SessionTokenHashService 中的 hash 业务逻辑。

### SessionTokenResolver
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/SessionTokenResolver.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：
  - `public Optional<String> resolve(HttpServletRequest request)`：执行 SessionTokenResolver 中的 resolve 业务逻辑。

### SysAppleAuthService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/SysAppleAuthService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public AppleExchangePreviewView exchange(AppDefinition appDefinition, AppleExchangeRequest request)`：签发、交换或创建认证会话。
  - `public Optional<AppleSessionRefreshView> refresh(AppDefinition appDefinition, String rawSessionToken)`：更新、刷新或重建业务状态。
  - `public Optional<AppleRevokeResultView> revoke(AppDefinition appDefinition, String rawSessionToken)`：删除、退出或撤销相关资源。

### SysAuthSessionService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/SysAuthSessionService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public DemoSessionCreatedView createDemoSession(String appCode, DemoSessionCreateRequest request)`：创建或初始化业务数据。
  - `public Optional<AuthenticatedSessionView> findCurrentSession(String rawSessionToken)`：执行 SysAuthSessionService 中的 findCurrentSession 业务逻辑。
  - `public Optional<LogoutResultView> logout(String rawSessionToken)`：删除、退出或撤销相关资源。

### SysEmailVerificationService
- 类型：`class`
- 包名：`com.apphub.backend.sys.auth.service`
- 文件：`src/main/java/com/apphub/backend/sys/auth/service/SysEmailVerificationService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public EmailVerificationTicketView requestCode(String appCode, String email, String sceneCode, String requestIp, Map<String, Object> payload)`：执行 SysEmailVerificationService 中的 requestCode 业务逻辑。
  - `public ConsumedVerificationTicket consumeCode(String appCode, String email, String sceneCode, String code)`：执行 SysEmailVerificationService 中的 consumeCode 业务逻辑。


## 系统公共模块：billing

### SysBillingController
- 类型：`class`
- 包名：`com.apphub.backend.sys.billing.controller`
- 文件：`src/main/java/com/apphub/backend/sys/billing/controller/SysBillingController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：
  - `public ApiResponse<EntitlementOverviewView> entitlements(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request)`：执行 SysBillingController 中的 entitlements 业务逻辑。
  - `public ApiResponse<EntitlementRefreshResultView> refreshEntitlements(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request)`：更新、刷新或重建业务状态。

### SysEntitlementSnapshotEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.billing.entity`
- 文件：`src/main/java/com/apphub/backend/sys/billing/entity/SysEntitlementSnapshotEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysPurchaseTransactionEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.billing.entity`
- 文件：`src/main/java/com/apphub/backend/sys/billing/entity/SysPurchaseTransactionEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysEntitlementSnapshotMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.billing.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/billing/mapper/SysEntitlementSnapshotMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysPurchaseTransactionMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.billing.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/billing/mapper/SysPurchaseTransactionMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### EntitlementItemView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/EntitlementItemView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### EntitlementObservabilityView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/EntitlementObservabilityView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### EntitlementOverviewView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/EntitlementOverviewView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### EntitlementRefreshItemView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/EntitlementRefreshItemView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### EntitlementRefreshResultView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/EntitlementRefreshResultView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PurchaseIntakeAcceptedView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/PurchaseIntakeAcceptedView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PurchaseRestoreAcceptedView
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/PurchaseRestoreAcceptedView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PurchaseRestoreItemRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/PurchaseRestoreItemRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PurchaseRestoreRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/PurchaseRestoreRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PurchaseVerifyRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.billing.model`
- 文件：`src/main/java/com/apphub/backend/sys/billing/model/PurchaseVerifyRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysBillingService
- 类型：`class`
- 包名：`com.apphub.backend.sys.billing.service`
- 文件：`src/main/java/com/apphub/backend/sys/billing/service/SysBillingService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public PurchaseIntakeAcceptedView verify(String appCode, Long userId, PurchaseVerifyRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public PurchaseRestoreAcceptedView restore(String appCode, Long userId, PurchaseRestoreRequest request)`：执行 SysBillingService 中的 restore 业务逻辑。
  - `public EntitlementOverviewView getEntitlements(String appCode, Long userId)`：执行 SysBillingService 中的 getEntitlements 业务逻辑。
  - `public EntitlementObservabilityView describeEntitlementObservability(String appCode)`：执行 SysBillingService 中的 describeEntitlementObservability 业务逻辑。
  - `public EntitlementRefreshResultView refreshEntitlements(String appCode, Long userId)`：更新、刷新或重建业务状态。


## 系统公共模块：configcenter

### SysConfigCenterController
- 类型：`class`
- 包名：`com.apphub.backend.sys.configcenter.controller`
- 文件：`src/main/java/com/apphub/backend/sys/configcenter/controller/SysConfigCenterController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysRemoteConfigEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.configcenter.entity`
- 文件：`src/main/java/com/apphub/backend/sys/configcenter/entity/SysRemoteConfigEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：
  - `public Long getId()`：执行 SysRemoteConfigEntity 中的 getId 业务逻辑。
  - `public void setId(Long id)`：执行 SysRemoteConfigEntity 中的 setId 业务逻辑。
  - `public String getAppCode()`：执行 SysRemoteConfigEntity 中的 getAppCode 业务逻辑。
  - `public void setAppCode(String appCode)`：执行 SysRemoteConfigEntity 中的 setAppCode 业务逻辑。
  - `public String getNamespaceCode()`：执行 SysRemoteConfigEntity 中的 getNamespaceCode 业务逻辑。
  - `public void setNamespaceCode(String namespaceCode)`：执行 SysRemoteConfigEntity 中的 setNamespaceCode 业务逻辑。
  - `public String getConfigKey()`：执行 SysRemoteConfigEntity 中的 getConfigKey 业务逻辑。
  - `public void setConfigKey(String configKey)`：执行 SysRemoteConfigEntity 中的 setConfigKey 业务逻辑。
  - `public String getConfigValueJson()`：执行 SysRemoteConfigEntity 中的 getConfigValueJson 业务逻辑。
  - `public void setConfigValueJson(String configValueJson)`：执行 SysRemoteConfigEntity 中的 setConfigValueJson 业务逻辑。
  - `public String getStatus()`：执行 SysRemoteConfigEntity 中的 getStatus 业务逻辑。
  - `public void setStatus(String status)`：执行 SysRemoteConfigEntity 中的 setStatus 业务逻辑。
  - `public OffsetDateTime getCreatedAt()`：执行 SysRemoteConfigEntity 中的 getCreatedAt 业务逻辑。
  - `public void setCreatedAt(OffsetDateTime createdAt)`：执行 SysRemoteConfigEntity 中的 setCreatedAt 业务逻辑。
  - `public OffsetDateTime getUpdatedAt()`：执行 SysRemoteConfigEntity 中的 getUpdatedAt 业务逻辑。
  - `public void setUpdatedAt(OffsetDateTime updatedAt)`：执行 SysRemoteConfigEntity 中的 setUpdatedAt 业务逻辑。

### SysRemoteConfigMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.configcenter.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/configcenter/mapper/SysRemoteConfigMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### RemoteConfigNamespaceView
- 类型：`record`
- 包名：`com.apphub.backend.sys.configcenter.model`
- 文件：`src/main/java/com/apphub/backend/sys/configcenter/model/RemoteConfigNamespaceView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysRemoteConfigService
- 类型：`class`
- 包名：`com.apphub.backend.sys.configcenter.service`
- 文件：`src/main/java/com/apphub/backend/sys/configcenter/service/SysRemoteConfigService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public RemoteConfigNamespaceView loadNamespace(String appCode, String namespaceCode)`：执行 SysRemoteConfigService 中的 loadNamespace 业务逻辑。


## 系统公共模块：powersync

### SysPowerSyncController
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.controller`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysPowerSyncUploadController
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.controller`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncUploadController.java`
- 中文说明：HTTP 控制器：负责接收外部请求、做参数/权限入口校验，并调用 Service 返回统一响应。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysSyncAuditLogEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.entity`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/entity/SysSyncAuditLogEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysSyncInstallationEntity
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.entity`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/entity/SysSyncInstallationEntity.java`
- 中文说明：数据库实体类：映射数据库表字段，是持久化层的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysSyncAuditLogMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.powersync.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/mapper/SysSyncAuditLogMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysSyncInstallationMapper
- 类型：`interface`
- 包名：`com.apphub.backend.sys.powersync.mapper`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/mapper/SysSyncInstallationMapper.java`
- 中文说明：MyBatis-Plus 数据访问接口：负责对应数据表的 CRUD 或自定义查询。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncAcceptedItem
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncAcceptedItem.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncBootstrapRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncBootstrapRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncBootstrapView
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncBootstrapView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncChangeItem
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncChangeItem.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncRebuildRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncRebuildRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncRebuildView
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncRebuildView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncRejectedItem
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncRejectedItem.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncTokenClaimsView
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncTokenClaimsView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncTokenRequest
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncTokenRequest.java`
- 中文说明：请求 DTO：描述接口入参结构，通常配合校验注解使用。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncTokenView
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncTokenView.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncUploadEnvelope
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncUploadEnvelope.java`
- 中文说明：普通 Java 类型：为所属模块提供模型、工具或领域能力。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncUploadResult
- 类型：`record`
- 包名：`com.apphub.backend.sys.powersync.model`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/model/PowerSyncUploadResult.java`
- 中文说明：响应/视图 DTO：描述接口或服务返回给调用方的数据结构。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysPowerSyncService
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.service`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/service/SysPowerSyncService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public PowerSyncBootstrapView bootstrap(String appCode, PowerSyncBootstrapRequest request, HttpServletRequest servletRequest, String requestId)`：创建或初始化业务数据。
  - `public PowerSyncTokenView issueToken(String appCode, PowerSyncTokenRequest request, HttpServletRequest servletRequest, String requestId)`：签发、交换或创建认证会话。
  - `public PowerSyncRebuildView requestRebuild(String appCode, PowerSyncRebuildRequest request, HttpServletRequest servletRequest, String requestId)`：执行 SysPowerSyncService 中的 requestRebuild 业务逻辑。

### SysPowerSyncSessionService
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.service`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/service/SysPowerSyncSessionService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public PowerSyncSessionContext require(String appCode, HttpServletRequest request)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public Long userId()`：执行 SysPowerSyncSessionService 中的 userId 业务逻辑。
  - `public Long sessionId()`：执行 SysPowerSyncSessionService 中的 sessionId 业务逻辑。

### SysPowerSyncUploadService
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.service`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/service/SysPowerSyncUploadService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public PowerSyncUploadResult upload(String appCode, PowerSyncUploadEnvelope envelope, HttpServletRequest request, String requestId)`：接收并处理客户端提交的数据或动作。

### SysSyncAuditService
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.service`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/service/SysSyncAuditService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### SysSyncInstallationService
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.service`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/service/SysSyncInstallationService.java`
- 中文说明：业务服务类：承载核心业务流程、数据读写编排、外部服务调用或规则判断。
- 公开方法：
  - `public SysSyncInstallationEntity upsertBootstrap(String appCode, Long userId, PowerSyncBootstrapRequest request)`：执行 SysSyncInstallationService 中的 upsertBootstrap 业务逻辑。
  - `public SysSyncInstallationEntity requireOwned(String appCode, Long userId, String installationId)`：校验入参、权限、会话或业务条件，不满足时抛出异常。
  - `public SysSyncInstallationEntity requestRebuild(String appCode, Long userId, String installationId, String reason)`：执行 SysSyncInstallationService 中的 requestRebuild 业务逻辑。
  - `public void markPushProcessed(String appCode, Long userId, String installationId, int acceptedCount, int rejectedCount)`：执行 SysSyncInstallationService 中的 markPushProcessed 业务逻辑。
  - `public void markFailure(String appCode, Long userId, String installationId, String errorCode, String errorMessage)`：执行 SysSyncInstallationService 中的 markFailure 业务逻辑。

### PowerSyncAppAdapter
- 类型：`interface`
- 包名：`com.apphub.backend.sys.powersync.support`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/support/PowerSyncAppAdapter.java`
- 中文说明：适配器：把通用系统能力转换成具体 app 的实现逻辑。
- 公开方法：无显式 public 方法或主要依赖 Lombok/record 自动生成。

### PowerSyncAppAdapterRegistry
- 类型：`class`
- 包名：`com.apphub.backend.sys.powersync.support`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/support/PowerSyncAppAdapterRegistry.java`
- 中文说明：注册表/路由类：收集多个实现并按 appCode 或类型进行查找分发。
- 公开方法：
  - `public List<PowerSyncAppAdapter> activeAdapters()`：判断能力支持或启用状态。
  - `public Optional<PowerSyncAppAdapter> get(String appCode)`：查询指定对象、配置、列表或当前上下文数据。
  - `public PowerSyncAppAdapter require(String appCode)`：校验入参、权限、会话或业务条件，不满足时抛出异常。

