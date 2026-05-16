# 项目接口文档（按公共类和各 App 区分）

生成时间：2026-04-28 09:40:13

说明：接口路径由 Controller 类上的 @RequestMapping 与方法上的 Mapping 注解拼接得到；入参/返回结构以 Handler 签名中的 DTO/Envelope 为准。

## App 模块：common

### GET /api/v1/apps/{appCode}/release/app-version
- 控制器：`AppVersionConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/common/AppVersionConfigController.java`
- Handler：`public ApiResponse<Map<String, Object>> appVersion(@PathVariable String appCode, return ApiResponse.success(currentRequestId(), appVersionPolicyService.policy(appCode, platform, appVersion, buildNumber)); } private String currentRequestId()`
- 中文说明：执行 AppVersionConfigController 中的 appVersion 业务逻辑。


## App 模块：fitmystery

### DELETE /api/v1/fitmystery/account
- 控制器：`FitMysteryAccountController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/account/FitMysteryAccountController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> deleteAccount(HttpServletRequest request)`
- 中文说明：删除、退出或撤销相关资源。

### POST /api/v1/fitmystery/activity/events:batchSubmit
- 控制器：`FitMysteryActivityController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/activity/FitMysteryActivityController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> batchSubmit(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), activityService.batchSubmit(requestSupport.requireUserId(request), body)); } public FitMysteryApiEnvelope<Map<String, Object>> today(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), activityService.today(requestSupport.requireUserId(request), date)); } }`
- 中文说明：接收并处理客户端提交的数据或动作。

### GET /api/v1/fitmystery/me/today
- 控制器：`FitMysteryActivityController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/activity/FitMysteryActivityController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> today(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), activityService.today(requestSupport.requireUserId(request), date)); } }`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/fitmystery/box/draws
- 控制器：`FitMysteryBoxController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/box/FitMysteryBoxController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> draws(HttpServletRequest request, @RequestParam(defaultValue = "50") int pageSize)`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### POST /api/v1/fitmystery/box/open
- 控制器：`FitMysteryBoxController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/box/FitMysteryBoxController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> open(HttpServletRequest request, @RequestBody FitMysteryBoxService.OpenBoxRequest body)`
- 中文说明：接收并处理客户端提交的数据或动作。

### GET /api/v1/fitmystery/box/state
- 控制器：`FitMysteryBoxController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/box/FitMysteryBoxController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> state(HttpServletRequest request)`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/fitmystery/collection/items
- 控制器：`FitMysteryBoxController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/box/FitMysteryBoxController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> collection(HttpServletRequest request, @RequestParam(defaultValue = "100") int pageSize)`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/fitmystery/config/app-store-summary
- 控制器：`FitMysteryConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/config/FitMysteryConfigController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> appStoreSummary()`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/fitmystery/config/bootstrap
- 控制器：`FitMysteryConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/config/FitMysteryConfigController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> bootstrap(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：创建或初始化业务数据。

### GET /api/v1/fitmystery/config/odds
- 控制器：`FitMysteryConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/config/FitMysteryConfigController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> odds()`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### POST /api/v1/fitmystery/purchases/verify
- 控制器：`FitMysteryPurchaseController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/purchase/FitMysteryPurchaseController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> verify(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), purchaseService.verifyAndGrant(requestSupport.requireUserId(request), body)); } }`
- 中文说明：校验入参、权限、会话或业务条件，不满足时抛出异常。

### GET /api/v1/fitmystery/reports/access
- 控制器：`FitMysteryReportController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> access(HttpServletRequest request)`
- 中文说明：执行 FitMysteryReportController 中的 access 业务逻辑。

### POST /api/v1/fitmystery/reports/generations/authorize
- 控制器：`FitMysteryReportController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> authorize(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.authorize(requestSupport.requireUserId(request), body)); } public FitMysteryApiEnvelope<Map<String, Object>> weekly(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "weekly", weekStart)); } public FitMysteryApiEnvelope<Map<String, Object>> monthly(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "monthly", month)); } public FitMysteryApiEnvelope<Map<String, Object>> history(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), Map.of( "type", type, "generationMode", "local_only", "serverStoresReportPayload", false,`
- 中文说明：执行 FitMysteryReportController 中的 authorize 业务逻辑。

### GET /api/v1/fitmystery/reports/history
- 控制器：`FitMysteryReportController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> history(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), Map.of( "type", type, "generationMode", "local_only", "serverStoresReportPayload", false, "items", java.util.List.of(), "access", reportService.access(requestSupport.requireUserId(request)) )); } }`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/fitmystery/reports/monthly
- 控制器：`FitMysteryReportController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> monthly(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "monthly", month)); } public FitMysteryApiEnvelope<Map<String, Object>> history(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), Map.of( "type", type, "generationMode", "local_only", "serverStoresReportPayload", false, "items", java.util.List.of(), "access", reportService.access(requestSupport.requireUserId(request)) )); } }`
- 中文说明：执行 FitMysteryReportController 中的 monthly 业务逻辑。

### GET /api/v1/fitmystery/reports/weekly
- 控制器：`FitMysteryReportController`
- 文件：`src/main/java/com/apphub/backend/apps/fitmystery/report/FitMysteryReportController.java`
- Handler：`public FitMysteryApiEnvelope<Map<String, Object>> weekly(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "weekly", weekStart)); } public FitMysteryApiEnvelope<Map<String, Object>> monthly(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), reportService.localReportPolicy(requestSupport.requireUserId(request), "monthly", month)); } public FitMysteryApiEnvelope<Map<String, Object>> history(HttpServletRequest request, return FitMysteryApiEnvelope.ok(requestSupport.requestId(), Map.of( "type", type, "generationMode", "local_only", "serverStoresReportPayload", false, "items", java.util.List.of(), "access", reportService.access(requestSupport.requireUserId(request)) )); } }`
- 中文说明：执行 FitMysteryReportController 中的 weekly 业务逻辑。


## App 模块：reading

### POST /api/v1/account/deletion-requests
- 控制器：`ReadingAccountCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingAccountCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.DeletionRequestResponse> delete( )`
- 中文说明：删除、退出或撤销相关资源。

### GET /api/v1/account/me/home-summary
- 控制器：`ReadingAccountCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingAccountCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.HomeSummaryView> homeSummary(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：执行 ReadingAccountCompatController 中的 homeSummary 业务逻辑。

### GET /api/v1/account/me/state
- 控制器：`ReadingAccountCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingAccountCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.AccountStateView> state(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/announcements
- 控制器：`ReadingAnnouncementCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingAnnouncementCompatController.java`
- Handler：`public ApiResponse<List<ReadingAnnouncementService.AnnouncementView>> list( )`
- 中文说明：查询列表数据。

### POST /api/v1/webhooks/app-store/notifications
- 控制器：`ReadingAppStoreWebhookCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/appstore/controller/ReadingAppStoreWebhookCompatController.java`
- Handler：`public ApiResponse<AppStoreNotificationAcceptedView> notifications(@RequestBody Map<String, Object> payload)`
- 中文说明：执行 ReadingAppStoreWebhookCompatController 中的 notifications 业务逻辑。

### POST /api/v1
- 控制器：`ReadingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.EntitlementRefreshView> refreshEntitlement(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：更新、刷新或重建业务状态。

### GET /api/v1/billing/entitlement
- 控制器：`ReadingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.AccountEntitlementView> entitlement(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：执行 ReadingBillingCompatController 中的 entitlement 业务逻辑。

### POST /api/v1/subscriptions/app-store/purchases/intake
- 控制器：`ReadingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.IntakeReceipt> purchaseIntake( )`
- 中文说明：执行 ReadingBillingCompatController 中的 purchaseIntake 业务逻辑。

### POST /api/v1/subscriptions/app-store/restores/intake
- 控制器：`ReadingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.IntakeReceipt> restoreIntake( )`
- 中文说明：接收并处理客户端提交的数据或动作。

### GET /api/v1/subscriptions/status
- 控制器：`ReadingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.SubscriptionStatusView> subscriptionStatus(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：执行 ReadingBillingCompatController 中的 subscriptionStatus 业务逻辑。

### POST /api/v1/subscriptions/transactions/verify
- 控制器：`ReadingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/billing/controller/ReadingBillingCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.IntakeReceipt> verify( )`
- 中文说明：校验入参、权限、会话或业务条件，不满足时抛出异常。

### GET /api/v1/children
- 控制器：`ReadingChildProfileCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingChildProfileCompatController.java`
- Handler：`public ApiResponse<List<ReadingCompatService.ChildView>> children(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：执行 ReadingChildProfileCompatController 中的 children 业务逻辑。

### POST /api/v1/children
- 控制器：`ReadingChildProfileCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingChildProfileCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.CreateChildReceipt> create( )`
- 中文说明：创建或初始化业务数据。

### PATCH /api/v1/children/{childId}
- 控制器：`ReadingChildProfileCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingChildProfileCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.CreateChildReceipt> update( )`
- 中文说明：更新、刷新或重建业务状态。

### POST /api/v1/account/deletion/confirm
- 控制器：`ReadingDeletionVerificationCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingDeletionVerificationCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.DeletionRequestResponse> confirm( HttpServletRequest request )`
- 中文说明：执行 ReadingDeletionVerificationCompatController 中的 confirm 业务逻辑。

### POST /api/v1/account/deletion/request-code
- 控制器：`ReadingDeletionVerificationCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingDeletionVerificationCompatController.java`
- Handler：`public ApiResponse<SysEmailVerificationService.EmailVerificationTicketView> requestCode( HttpServletRequest request )`
- 中文说明：执行 ReadingDeletionVerificationCompatController 中的 requestCode 业务逻辑。

### POST /api/v1/account/device-event
- 控制器：`ReadingDeviceCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingDeviceCompatController.java`
- Handler：`public ApiResponse<ReadingDeviceEventService.DeviceEventReceipt> event( HttpServletRequest request )`
- 中文说明：执行 ReadingDeviceCompatController 中的 event 业务逻辑。

### POST /api/v1/feedback
- 控制器：`ReadingFeedbackCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingFeedbackCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.FeedbackSubmissionReceipt> submit( )`
- 中文说明：接收并处理客户端提交的数据或动作。

### GET /api/v1/learning/daily-task
- 控制器：`ReadingLearningCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingLearningCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.DailyLearningTaskFeedView> feed( )`
- 中文说明：执行 ReadingLearningCompatController 中的 feed 业务逻辑。

### POST /api/v1/learning/daily-task/{taskId}/complete
- 控制器：`ReadingLearningCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingLearningCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.DailyLearningTaskCompletionView> complete( )`
- 中文说明：执行 ReadingLearningCompatController 中的 complete 业务逻辑。

### POST /api/v1/ocr/extract
- 控制器：`ReadingOcrCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingOcrCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.OcrExtractReceipt> extract( )`
- 中文说明：执行 ReadingOcrCompatController 中的 extract 业务逻辑。

### GET /api/v1/preferences/me
- 控制器：`ReadingPreferenceCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPreferenceCompatController.java`
- Handler：`public ApiResponse<ReadingPreferenceService.PreferenceView> me(HttpServletRequest request)`
- 中文说明：执行 ReadingPreferenceCompatController 中的 me 业务逻辑。

### PATCH /api/v1/preferences/me
- 控制器：`ReadingPreferenceCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPreferenceCompatController.java`
- Handler：`public ApiResponse<ReadingPreferenceService.PreferenceView> patch( HttpServletRequest request )`
- 中文说明：执行 ReadingPreferenceCompatController 中的 patch 业务逻辑。

### GET /api/v1/bootstrap/config
- 控制器：`ReadingPublicCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPublicCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.BootstrapConfigView> bootstrap()`
- 中文说明：创建或初始化业务数据。

### GET /api/v1/legal/docs
- 控制器：`ReadingPublicCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPublicCompatController.java`
- Handler：`public ApiResponse<List<ReadingCompatService.LegalDocView>> legalDocs()`
- 中文说明：执行 ReadingPublicCompatController 中的 legalDocs 业务逻辑。

### GET /api/v1/plans
- 控制器：`ReadingPublicCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingPublicCompatController.java`
- Handler：`public ApiResponse<List<ReadingCompatService.PlanView>> plans()`
- 中文说明：执行 ReadingPublicCompatController 中的 plans 业务逻辑。

### POST /api/v1/review-cards
- 控制器：`ReadingReviewCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingReviewCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.CreateReviewCardReceipt> create( )`
- 中文说明：创建或初始化业务数据。

### GET /api/v1/review-cards/today
- 控制器：`ReadingReviewCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingReviewCompatController.java`
- Handler：`public ApiResponse<List<ReadingCompatService.ReviewCardView>> today(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### POST /api/v1/review-events
- 控制器：`ReadingReviewCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingReviewCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.ReviewEventReceipt> event( )`
- 中文说明：执行 ReadingReviewCompatController 中的 event 业务逻辑。

### POST /api/v1/tts/speak
- 控制器：`ReadingTtsCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingTtsCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.CloudSpeechReceipt> speak( )`
- 中文说明：执行 ReadingTtsCompatController 中的 speak 业务逻辑。

### GET /api/v1/usage/children/{childId}/summary
- 控制器：`ReadingUsageCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingUsageCompatController.java`
- Handler：`public ApiResponse<ReadingUsageService.ChildUsageSummaryView> childSummary( HttpServletRequest request )`
- 中文说明：执行 ReadingUsageCompatController 中的 childSummary 业务逻辑。

### GET /api/v1/usage/family/summary
- 控制器：`ReadingUsageCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingUsageCompatController.java`
- Handler：`public ApiResponse<ReadingUsageService.FamilyUsageSummaryView> familySummary(HttpServletRequest request)`
- 中文说明：执行 ReadingUsageCompatController 中的 familySummary 业务逻辑。

### POST /api/v1/usage/retention/cleanup
- 控制器：`ReadingUsageCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingUsageCompatController.java`
- Handler：`public ApiResponse<ReadingUsageService.UsageRetentionCleanupReceipt> cleanupRetention(HttpServletRequest request)`
- 中文说明：执行 ReadingUsageCompatController 中的 cleanupRetention 业务逻辑。

### POST /api/v1/usage/session/end
- 控制器：`ReadingUsageCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingUsageCompatController.java`
- Handler：`public ApiResponse<ReadingUsageService.UsageSessionEndReceipt> end( HttpServletRequest request )`
- 中文说明：执行 ReadingUsageCompatController 中的 end 业务逻辑。

### POST /api/v1/usage/session/start
- 控制器：`ReadingUsageCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingUsageCompatController.java`
- Handler：`public ApiResponse<ReadingUsageService.UsageSessionStartReceipt> start( HttpServletRequest request )`
- 中文说明：执行 ReadingUsageCompatController 中的 start 业务逻辑。

### POST /api/v1/admin/reading/weekly-report/access-cache/refresh
- 控制器：`ReadingWeeklyReportAdminController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingWeeklyReportAdminController.java`
- Handler：`public ApiResponse<WeeklyReportAccessRefreshReceipt> refreshAccessCache( HttpServletRequest request )`
- 中文说明：更新、刷新或重建业务状态。

### GET /api/v1/reports/weekly/current
- 控制器：`ReadingWeeklyReportCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingWeeklyReportCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.WeeklyParentReportView> current( )`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/reports/weekly/history
- 控制器：`ReadingWeeklyReportCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/reading/compat/controller/ReadingWeeklyReportCompatController.java`
- Handler：`public ApiResponse<ReadingCompatService.WeeklyReportHistoryView> history( )`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。


## App 模块：saving

### DELETE /v1/account
- 控制器：`SavingAccountController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingAccountController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> deleteCurrentAccount(HttpServletRequest request)`
- 中文说明：删除、退出或撤销相关资源。

### POST /v1/appstore/notifications
- 控制器：`SavingAppStoreCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/appstore/controller/SavingAppStoreCompatController.java`
- Handler：`public ApiResponse<AppStoreNotificationAcceptedView> notifications(@Valid @RequestBody SavingNotificationRequest request)`
- 中文说明：执行 SavingAppStoreCompatController 中的 notifications 业务逻辑。

### GET /v1/entitlements
- 控制器：`SavingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/billing/controller/SavingBillingCompatController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> entitlements(HttpServletRequest request)`
- 中文说明：执行 SavingBillingCompatController 中的 entitlements 业务逻辑。

### POST /v1/entitlements/refresh
- 控制器：`SavingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/billing/controller/SavingBillingCompatController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> refreshEntitlements(HttpServletRequest request)`
- 中文说明：更新、刷新或重建业务状态。

### POST /v1/purchases/restore
- 控制器：`SavingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/billing/controller/SavingBillingCompatController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> restore(@Valid @RequestBody SavingPurchaseRestoreRequest request, HttpServletRequest httpServletRequest)`
- 中文说明：接收并处理客户端提交的数据或动作。

### POST /v1/purchases/verify
- 控制器：`SavingBillingCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/billing/controller/SavingBillingCompatController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> verify(@Valid @RequestBody SavingPurchaseVerifyRequest request, HttpServletRequest httpServletRequest)`
- 中文说明：校验入参、权限、会话或业务条件，不满足时抛出异常。

### GET /v1/config/app-review-materials
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> appReviewMaterials()`
- 中文说明：执行 SavingConfigController 中的 appReviewMaterials 业务逻辑。

### GET /v1/config/app-review-notes
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> appReviewNotes()`
- 中文说明：执行 SavingConfigController 中的 appReviewNotes 业务逻辑。

### GET /v1/config/app-store-connect-field-mapping
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> appStoreConnectFieldMapping()`
- 中文说明：执行 SavingConfigController 中的 appStoreConnectFieldMapping 业务逻辑。

### GET /v1/config/app-store-privacy-labels
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> appStorePrivacyLabels()`
- 中文说明：执行 SavingConfigController 中的 appStorePrivacyLabels 业务逻辑。

### GET /v1/config/app-store-submission-checklist
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> appStoreSubmissionChecklist()`
- 中文说明：执行 SavingConfigController 中的 appStoreSubmissionChecklist 业务逻辑。

### GET /v1/config/app-version
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> appVersion(@RequestParam(defaultValue = "ios") String platform, return SavingApiEnvelope.ok(requestSupport.requestId(), appVersionPolicyService.policy(SavingAppModule.APP_CODE, platform, appVersion, buildNumber)); } /** 涓枃璇存槑锛歰nboarding銆佺┖鐘舵€佷笌鐣欏瓨鏂囨缁熶竴 DB 鍖栵紝鏂囨涓嶅緱鎵胯鏀剁泭銆?*/ public SavingApiEnvelope<Map<String, Object>> onboarding(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 appVersion 业务逻辑。

### GET /v1/config/categories
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> categories(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 categories 业务逻辑。

### GET /v1/config/entitlement-matrix
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> entitlementMatrix(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 entitlementMatrix 业务逻辑。

### GET /v1/config/feature-flags
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> featureFlags(@RequestParam(defaultValue = "ios") String platform, return SavingApiEnvelope.ok(requestSupport.requestId(), configService.featureFlags(platform, appVersion)); } /** 涓枃璇存槑锛氱増鏈崌绾ф彁绀哄鐢ㄥ App 閫氱敤绛栫暐锛涘鎴风鍙睍绀烘櫘閫?App Store 璺宠浆锛屼笉鍋氬寘鍒嗗彂鎴栧己璇卞銆?*/ public SavingApiEnvelope<Map<String, Object>> appVersion(@RequestParam(defaultValue = "ios") String platform, return SavingApiEnvelope.ok(requestSupport.requestId(), appVersionPolicyService.policy(SavingAppModule.APP_CODE, platform, appVersion, buildNumber)); } /** 涓枃璇存槑锛歰nboarding銆佺┖鐘舵€佷笌鐣欏瓨鏂囨缁熶竴 DB 鍖栵紝鏂囨涓嶅緱鎵胯鏀剁泭銆?*/ public SavingApiEnvelope<Map<String, Object>> onboarding(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 featureFlags 业务逻辑。

### GET /v1/config/legal-document-consistency-policy
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> legalDocumentConsistencyPolicy()`
- 中文说明：执行 SavingConfigController 中的 legalDocumentConsistencyPolicy 业务逻辑。

### GET /v1/config/onboarding
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> onboarding(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 onboarding 业务逻辑。

### GET /v1/config/paywall
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> paywall(@RequestParam(defaultValue = "default") String trigger, return SavingApiEnvelope.ok(requestSupport.requestId(), configService.paywall(trigger, platform, locale)); } /** 涓枃璇存槑锛氳褰曞垎绫?catalog 浠庢暟鎹簱閰嶇疆璇诲彇锛屽鎴风鍙繚瀛?code锛岄伩鍏嶅墠绔‖缂栫爜鍒嗙被銆?*/ public SavingApiEnvelope<Map<String, Object>> categories(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 paywall 业务逻辑。

### GET /v1/config/report-access
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> reportAccess()`
- 中文说明：执行 SavingConfigController 中的 reportAccess 业务逻辑。

### GET /v1/config/report-history-policy
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> reportHistoryPolicy(@RequestParam(defaultValue = "zh-Hans") String locale)`
- 中文说明：执行 SavingConfigController 中的 reportHistoryPolicy 业务逻辑。

### GET /v1/config/report-history-pro-policy
- 控制器：`SavingConfigController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingConfigController.java`
- Handler：`public SavingApiEnvelope<Map<String, Object>> reportHistoryProPolicy()`
- 中文说明：执行 SavingConfigController 中的 reportHistoryProPolicy 业务逻辑。

### GET /v1/dashboard/overview
- 控制器：`SavingDashboardController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingDashboardController.java`
- Handler：`public void overview()`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /v1/records
- 控制器：`SavingFinancialRecordController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- Handler：`public void list()`
- 中文说明：查询列表数据。

### DELETE /v1/records/{recordType}/{recordId}
- 控制器：`SavingFinancialRecordController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- Handler：`public void delete()`
- 中文说明：删除、退出或撤销相关资源。

### POST /v1/records/expenses
- 控制器：`SavingFinancialRecordController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- Handler：`public void createExpense()`
- 中文说明：创建或初始化业务数据。

### PUT /v1/records/expenses/{recordId}
- 控制器：`SavingFinancialRecordController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- Handler：`public void updateExpense()`
- 中文说明：更新、刷新或重建业务状态。

### POST /v1/records/savings
- 控制器：`SavingFinancialRecordController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- Handler：`public void createSaving()`
- 中文说明：创建或初始化业务数据。

### PUT /v1/records/savings/{recordId}
- 控制器：`SavingFinancialRecordController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingFinancialRecordController.java`
- Handler：`public void updateSaving()`
- 中文说明：更新、刷新或重建业务状态。

### POST /v1/reports/monthly
- 控制器：`SavingReportController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingReportController.java`
- Handler：`public void monthly()`
- 中文说明：执行 SavingReportController 中的 monthly 业务逻辑。

### POST /v1/reports/weekly
- 控制器：`SavingReportController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/controller/SavingReportController.java`
- Handler：`public void weekly()`
- 中文说明：执行 SavingReportController 中的 weekly 业务逻辑。

### POST /v1/users/bootstrap
- 控制器：`SavingUserBootstrapCompatController`
- 文件：`src/main/java/com/apphub/backend/apps/saving/user/controller/SavingUserBootstrapCompatController.java`
- Handler：`public ApiResponse<SavingBootstrapResponse> bootstrap(@Valid @RequestBody SavingBootstrapRequest request)`
- 中文说明：创建或初始化业务数据。


## 系统公共模块：app

### GET /api/v1/system/apple/ops-gates
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<List<AppAppleOpsGateView>> appleOpsGates()`
- 中文说明：执行 SystemController 中的 appleOpsGates 业务逻辑。

### GET /api/v1/system/apps
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<List<AppDefinition>> apps()`
- 中文说明：查询列表数据。

### GET /api/v1/system/apps/{appCode}
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<AppDefinition> app(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`
- 中文说明：查询指定对象、配置、列表或当前上下文数据。

### GET /api/v1/system/apps/{appCode}/apple/ops-gate
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<AppAppleOpsGateView> appleOpsGate(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`
- 中文说明：执行 SystemController 中的 appleOpsGate 业务逻辑。

### GET /api/v1/system/apps/{appCode}/apple/readiness
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<AppAppleReadinessView> appleReadiness(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`
- 中文说明：执行 SystemController 中的 appleReadiness 业务逻辑。

### GET /api/v1/system/apps/{appCode}/apple/token-storage
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<AppAppleTokenStorageView> appleTokenStorage(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`
- 中文说明：执行 SystemController 中的 appleTokenStorage 业务逻辑。

### GET /api/v1/system/apps/{appCode}/billing/entitlements/observability
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<EntitlementObservabilityView> entitlementObservability(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`
- 中文说明：执行 SystemController 中的 entitlementObservability 业务逻辑。

### GET /api/v1/system/healthz
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<Map<String, Object>> healthz()`
- 中文说明：执行 SystemController 中的 healthz 业务逻辑。

### GET /api/v1/system/public-surface
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<SystemPublicSurfaceView> publicSurface()`
- 中文说明：执行 SystemController 中的 publicSurface 业务逻辑。

### GET /api/v1/system/release-gate
- 控制器：`SystemController`
- 文件：`src/main/java/com/apphub/backend/sys/app/controller/SystemController.java`
- Handler：`public ApiResponse<SystemReleaseGateView> releaseGate()`
- 中文说明：执行 SystemController 中的 releaseGate 业务逻辑。


## 系统公共模块：appstore

### POST /api/v1/system/appstore/apps/{appCode}/notifications
- 控制器：`SysAppStoreController`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/controller/SysAppStoreController.java`
- Handler：`public ApiResponse<AppStoreNotificationAcceptedView> notifications( )`
- 中文说明：执行 SysAppStoreController 中的 notifications 业务逻辑。

### GET /api/v1/system/appstore/apps/{appCode}/notifications/observability
- 控制器：`SysAppStoreController`
- 文件：`src/main/java/com/apphub/backend/sys/appstore/controller/SysAppStoreController.java`
- Handler：`public ApiResponse<AppStoreNotificationObservabilityView> notificationObservability(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode)`
- 中文说明：执行 SysAppStoreController 中的 notificationObservability 业务逻辑。


## 系统公共模块：auth

### POST /api/v1/system/auth/apps/{appCode}/apple/exchange
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<AppleExchangePreviewView> exchangeApple( )`
- 中文说明：签发、交换或创建认证会话。

### POST /api/v1/system/auth/apps/{appCode}/apple/refresh
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<AppleSessionRefreshView> refreshApple( )`
- 中文说明：更新、刷新或重建业务状态。

### POST /api/v1/system/auth/apps/{appCode}/apple/revoke
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<AppleRevokeResultView> revokeApple( )`
- 中文说明：删除、退出或撤销相关资源。

### POST /api/v1/system/auth/apps/{appCode}/logout
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<LogoutResultView> logoutForApp( )`
- 中文说明：删除、退出或撤销相关资源。

### GET /api/v1/system/auth/apps/{appCode}/me
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<AuthenticatedSessionView> meForApp( )`
- 中文说明：执行 SysAuthController 中的 meForApp 业务逻辑。

### POST /api/v1/system/auth/apps/{appCode}/sessions/demo
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<DemoSessionCreatedView> createDemoSession( )`
- 中文说明：创建或初始化业务数据。

### POST /api/v1/system/auth/logout
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<LogoutResultView> logout(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：删除、退出或撤销相关资源。

### GET /api/v1/system/auth/me
- 控制器：`SysAuthController`
- 文件：`src/main/java/com/apphub/backend/sys/auth/controller/SysAuthController.java`
- Handler：`public ApiResponse<AuthenticatedSessionView> me(@Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：执行 SysAuthController 中的 me 业务逻辑。


## 系统公共模块：billing

### GET /api/v1/system/billing/apps/{appCode}/entitlements
- 控制器：`SysBillingController`
- 文件：`src/main/java/com/apphub/backend/sys/billing/controller/SysBillingController.java`
- Handler：`public ApiResponse<EntitlementOverviewView> entitlements(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：执行 SysBillingController 中的 entitlements 业务逻辑。

### POST /api/v1/system/billing/apps/{appCode}/entitlements/refresh
- 控制器：`SysBillingController`
- 文件：`src/main/java/com/apphub/backend/sys/billing/controller/SysBillingController.java`
- Handler：`public ApiResponse<EntitlementRefreshResultView> refreshEntitlements(@Parameter(description = "搴旂敤缂栫爜锛屼緥濡?paipai_readingcompanion 鎴?saving") @PathVariable String appCode, @Parameter(hidden = true) HttpServletRequest request)`
- 中文说明：更新、刷新或重建业务状态。

### POST /api/v1/system/billing/apps/{appCode}/purchases/restore
- 控制器：`SysBillingController`
- 文件：`src/main/java/com/apphub/backend/sys/billing/controller/SysBillingController.java`
- Handler：`public ApiResponse<PurchaseRestoreAcceptedView> restore( )`
- 中文说明：接收并处理客户端提交的数据或动作。

### POST /api/v1/system/billing/apps/{appCode}/purchases/verify
- 控制器：`SysBillingController`
- 文件：`src/main/java/com/apphub/backend/sys/billing/controller/SysBillingController.java`
- Handler：`public ApiResponse<PurchaseIntakeAcceptedView> verify( )`
- 中文说明：校验入参、权限、会话或业务条件，不满足时抛出异常。


## 系统公共模块：configcenter

### GET /api/v1/system/config/{appCode}/{namespaceCode}
- 控制器：`SysConfigCenterController`
- 文件：`src/main/java/com/apphub/backend/sys/configcenter/controller/SysConfigCenterController.java`
- Handler：`public ApiResponse<RemoteConfigNamespaceView> namespace( )`
- 中文说明：执行 SysConfigCenterController 中的 namespace 业务逻辑。


## 系统公共模块：powersync

### POST /api/v1/powersync/{appCode}/bootstrap
- 控制器：`SysPowerSyncController`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncController.java`
- Handler：`public ApiResponse<PowerSyncBootstrapView> bootstrap( HttpServletRequest request )`
- 中文说明：创建或初始化业务数据。

### POST /api/v1/powersync/{appCode}/rebuild
- 控制器：`SysPowerSyncController`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncController.java`
- Handler：`public ApiResponse<PowerSyncRebuildView> rebuild( HttpServletRequest request )`
- 中文说明：更新、刷新或重建业务状态。

### POST /api/v1/powersync/{appCode}/token
- 控制器：`SysPowerSyncController`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncController.java`
- Handler：`public ApiResponse<PowerSyncTokenView> token( HttpServletRequest request )`
- 中文说明：组装、转换或映射数据结构。

### POST /api/v1/powersync/{appCode}/upload
- 控制器：`SysPowerSyncUploadController`
- 文件：`src/main/java/com/apphub/backend/sys/powersync/controller/SysPowerSyncUploadController.java`
- Handler：`public ApiResponse<PowerSyncUploadResult> upload( HttpServletRequest request )`
- 中文说明：接收并处理客户端提交的数据或动作。

