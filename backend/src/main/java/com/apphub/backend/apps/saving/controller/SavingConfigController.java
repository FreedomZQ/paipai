package com.apphub.backend.apps.saving.controller;

import com.apphub.backend.apps.common.AppVersionPolicyService;
import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.apps.saving.api.SavingApiEnvelope;
import com.apphub.backend.apps.saving.service.SavingConfigService;
import com.apphub.backend.apps.saving.service.SavingRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/config")
@Tag(name = "省钱星球配置", description = "saving 启动、付费页、分类、报告策略和 App Store 审核材料配置接口。")
public class SavingConfigController {
    private final SavingConfigService configService;
    private final SavingRequestSupport requestSupport;
    private final AppVersionPolicyService appVersionPolicyService;

    public SavingConfigController(SavingConfigService configService,
                                  SavingRequestSupport requestSupport,
                                  AppVersionPolicyService appVersionPolicyService) {
        this.configService = configService;
        this.requestSupport = requestSupport;
        this.appVersionPolicyService = appVersionPolicyService;
    }

    @Operation(summary = "查询付费页配置", description = "按触发场景、平台和语言查询 saving 付费页文案、价格展示和转化策略。")
    @GetMapping("/paywall")
    public SavingApiEnvelope<Map<String, Object>> paywall(@Parameter(description = "触发场景。示例：advanced_report", example = "advanced_report") @RequestParam(defaultValue = "default") String trigger,
                                                          @Parameter(description = "客户端平台。示例：ios", example = "ios") @RequestParam(defaultValue = "ios") String platform,
                                                          @Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(defaultValue = "zh-Hans") String locale) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.paywall(trigger, platform, locale));
    }

    /** 中文说明：记录分类 catalog 从数据库配置读取，客户端只保存 code，避免前端硬编码分类。 */
    @Operation(summary = "查询记录分类", description = "按语言查询支出/存钱分类配置；客户端只保存分类 code。")
    @GetMapping("/categories")
    public SavingApiEnvelope<Map<String, Object>> categories(@Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(defaultValue = "zh-Hans") String locale) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.recordCategories(locale));
    }

    /** 中文说明：免费/付费权益对比页配置化，保证展示、计费权益和审核材料口径一致。 */
    @Operation(summary = "查询权益对比矩阵", description = "查询免费版与 Pro 版权益对比展示配置。")
    @GetMapping("/entitlement-matrix")
    public SavingApiEnvelope<Map<String, Object>> entitlementMatrix(@Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(defaultValue = "zh-Hans") String locale) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.entitlementMatrix(locale));
    }

    /** 中文说明：报告访问矩阵控制免费/Pro 模块展示，便于后续扩展报告版本而不重新发版。 */
    @Operation(summary = "查询报告访问矩阵", description = "查询基础报告和 Pro 高级报告模块的展示/锁定策略。")
    @GetMapping("/report-access")
    public SavingApiEnvelope<Map<String, Object>> reportAccess() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.reportAccess());
    }

    /** 中文说明：历史报告入口策略 DB 化；快照仍只保存在 App 本机，后端只返回展示/留存策略。 */
    @Operation(summary = "查询历史报告策略", description = "查询历史报告入口、留存和本机快照展示策略；后端不保存报告快照。")
    @GetMapping("/report-history-policy")
    public SavingApiEnvelope<Map<String, Object>> reportHistoryPolicy(@Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(defaultValue = "zh-Hans") String locale) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.reportHistoryPolicy(locale));
    }

    /** 中文说明：功能开关支持首发灰度和紧急降级，客户端必须保留本地兜底。 */
    @Operation(summary = "查询功能开关", description = "按平台和版本查询首发灰度、降级和实验开关。")
    @GetMapping("/feature-flags")
    public SavingApiEnvelope<Map<String, Object>> featureFlags(@Parameter(description = "客户端平台。示例：ios", example = "ios") @RequestParam(defaultValue = "ios") String platform,
                                                               @Parameter(description = "客户端版本号。示例：1.0.0", example = "1.0.0") @RequestParam(defaultValue = "unknown") String appVersion) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.featureFlags(platform, appVersion));
    }

    /** 中文说明：版本升级提示复用多 App 通用策略；客户端只展示普通 App Store 跳转，不做包分发或强诱导。 */
    @Operation(summary = "查询版本升级策略", description = "查询 App Store 升级提示策略，不做安装包分发。")
    @GetMapping("/app-version")
    public SavingApiEnvelope<Map<String, Object>> appVersion(@Parameter(description = "客户端平台。示例：ios", example = "ios") @RequestParam(defaultValue = "ios") String platform,
                                                             @Parameter(description = "客户端版本号。示例：1.0.0", example = "1.0.0") @RequestParam(defaultValue = "unknown") String appVersion,
                                                             @Parameter(description = "客户端构建号。示例：100", example = "100") @RequestParam(defaultValue = "unknown") String buildNumber) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), appVersionPolicyService.policy(SavingAppModule.APP_CODE, platform, appVersion, buildNumber));
    }

    /** 中文说明：onboarding、空状态与留存文案统一 DB 化，文案不得承诺收益。 */
    @Operation(summary = "查询新手引导配置", description = "按语言查询 onboarding、空状态和留存文案配置。")
    @GetMapping("/onboarding")
    public SavingApiEnvelope<Map<String, Object>> onboarding(@Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(defaultValue = "zh-Hans") String locale) {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.onboarding(locale));
    }

    /** 中文说明：App Review Notes 仅供发布检查使用，禁止写入密钥/密码。 */
    @Operation(summary = "查询审核备注", description = "查询 App Review Notes 生成配置，禁止包含密钥/密码。")
    @GetMapping("/app-review-notes")
    public SavingApiEnvelope<Map<String, Object>> appReviewNotes() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.appReviewNotes());
    }

    /** 中文说明：App Store 隐私标签建议从 DB 配置生成，便于个人开发者低成本准备审核材料。 */
    @Operation(summary = "查询隐私标签建议", description = "查询 App Store 隐私标签建议配置。")
    @GetMapping("/app-store-privacy-labels")
    public SavingApiEnvelope<Map<String, Object>> appStorePrivacyLabels() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.appStorePrivacyLabels());
    }

    /** 中文说明：审核材料门禁配置化，脚本会读取该配置生成 Review Notes 并检查敏感词/口径。 */
    @Operation(summary = "查询审核材料配置", description = "查询审核材料门禁和生成策略配置。")
    @GetMapping("/app-review-materials")
    public SavingApiEnvelope<Map<String, Object>> appReviewMaterials() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.appReviewMaterials());
    }

    /** 中文说明：App Store Connect 字段映射只用于提交前人工核对，不自动写入 Apple 后台。 */
    @Operation(summary = "查询 App Store Connect 字段映射", description = "查询提交前人工核对用字段映射。")
    @GetMapping("/app-store-connect-field-mapping")
    public SavingApiEnvelope<Map<String, Object>> appStoreConnectFieldMapping() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.appStoreConnectFieldMapping());
    }

    /** 中文说明：App Store 提交清单从 DB 配置生成，减少个人开发者提审漏项。 */
    @Operation(summary = "查询提交清单", description = "查询 App Store 提交前检查清单。")
    @GetMapping("/app-store-submission-checklist")
    public SavingApiEnvelope<Map<String, Object>> appStoreSubmissionChecklist() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.appStoreSubmissionChecklist());
    }

    /** 中文说明：文档一致性策略用于脚本门禁，确保隐私、订阅和 local-only 口径一致。 */
    @Operation(summary = "查询法律文档一致性策略", description = "查询隐私、订阅和 local-only 文档一致性门禁策略。")
    @GetMapping("/legal-document-consistency-policy")
    public SavingApiEnvelope<Map<String, Object>> legalDocumentConsistencyPolicy() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.legalDocumentConsistencyPolicy());
    }

    /** 中文说明：历史报告 Pro 增强策略仍为 local-only；后端不存报告快照。 */
    @Operation(summary = "查询历史报告 Pro 策略", description = "查询历史报告 Pro 增强策略；后端不存报告快照。")
    @GetMapping("/report-history-pro-policy")
    public SavingApiEnvelope<Map<String, Object>> reportHistoryProPolicy() {
        return SavingApiEnvelope.ok(requestSupport.requestId(), configService.reportHistoryProPolicy());
    }
}
