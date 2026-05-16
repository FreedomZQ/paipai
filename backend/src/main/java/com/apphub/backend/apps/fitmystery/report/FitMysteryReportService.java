package com.apphub.backend.apps.fitmystery.report;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.apps.fitmystery.config.FitMysteryConfigService;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryReportDataService;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.entitlement.model.UserEntitlementDecisionView;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class FitMysteryReportService {
    private final FitMysteryReportDataService mapper;
    private final FitMysteryConfigService configService;
    private final SysBillingService billingService;
    private final SysEntitlementCenterService entitlementCenterService;

    public FitMysteryReportService(FitMysteryReportDataService mapper, FitMysteryConfigService configService, SysBillingService billingService, SysEntitlementCenterService entitlementCenterService) {
        this.mapper = mapper;
        this.configService = configService;
        this.billingService = billingService;
        this.entitlementCenterService = entitlementCenterService;
    }

    public Map<String, Object> access(Long userId) {
        ensureInitialFreeGrant(userId);
        boolean pro = hasPro(userId);
        int balance = mapper.currentBalance(FitMysteryAppModule.APP_CODE, userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessLevel", pro ? "pro" : "free");
        data.put("canGenerate", pro || balance > 0);
        data.put("remainingFreeQuota", balance);
        data.put("serverStoresReportPayload", false);
        data.put("policy", reportGenerationPolicy());
        data.put("recentLedger", mapper.recent(FitMysteryAppModule.APP_CODE, userId, 10));
        data.put("note", "报告正文和明细数据由 App 基于本机记录生成；后端只验证生成权益和记录额度流水，为未来云同步权益预留。 ");
        return data;
    }

    @Transactional
    public Map<String, Object> authorize(Long userId, AuthorizeReportGenerationRequest request) {
        ensureInitialFreeGrant(userId);
        String reportType = normalizeReportType(request.reportType());
        String periodKey = required(request.periodKey(), "periodKey");
        String idempotencyKey = required(request.idempotencyKey(), "idempotencyKey");
        String localDataHash = emptyToNull(request.localDataHash());
        boolean pro = hasPro(userId);
        if (mapper.countByIdempotency(FitMysteryAppModule.APP_CODE, userId, idempotencyKey) > 0) {
            return authorizationResponse("already_authorized", pro ? "pro" : "free", mapper.currentBalance(FitMysteryAppModule.APP_CODE, userId), reportType, periodKey);
        }
        if (pro) {
            return authorizationResponse("authorized_pro", "pro", mapper.currentBalance(FitMysteryAppModule.APP_CODE, userId), reportType, periodKey);
        }
        int balance = mapper.currentBalance(FitMysteryAppModule.APP_CODE, userId);
        if (balance <= 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Report generation quota required");
        }
        int after = balance - 1;
        mapper.insertLedger(UUID.randomUUID().toString(), FitMysteryAppModule.APP_CODE, userId, "consume", reportType, -1, after, "report_generation", periodKey, localDataHash, idempotencyKey, now());
        return authorizationResponse("authorized_free_quota_consumed", "free", after, reportType, periodKey);
    }

    public Map<String, Object> localReportPolicy(Long userId, String type, String anchor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportType", normalizeReportType(type));
        data.put("anchor", anchor == null || anchor.isBlank() ? null : anchor);
        data.put("access", access(userId));
        data.put("generationMode", "local_only");
        data.put("serverStoresReportPayload", false);
        data.put("disclaimer", "报告仅用于个人健康习惯记录和娱乐化总结，不构成医疗建议。 ");
        data.put("instructions", List.of(
            "App 使用本机喝水、运动、步数、抽盒和图鉴数据生成报告。",
            "生成前调用 authorize 接口校验 Pro 权益或消耗免费生成额度。",
            "后端不接收、不保存报告正文或用户本机明细；仅保存授权额度流水和可选 localDataHash。"
        ));
        return data;
    }

    private void ensureInitialFreeGrant(Long userId) {
        int grantCount = mapper.countInitialGrant(FitMysteryAppModule.APP_CODE, userId);
        int freeInitialQuota = intValue(reportGenerationPolicy().get("freeInitialQuota"), 1);
        if (grantCount > 0 || freeInitialQuota <= 0) {
            return;
        }
        int after = mapper.currentBalance(FitMysteryAppModule.APP_CODE, userId) + freeInitialQuota;
        mapper.insertLedger(UUID.randomUUID().toString(), FitMysteryAppModule.APP_CODE, userId, "grant", "all", freeInitialQuota, after, "free_initial_grant", null, null, "free_initial_grant:" + userId, now());
    }

    private boolean hasPro(Long userId) {
        try {
            UserEntitlementDecisionView decision = entitlementCenterService.resolveUserEntitlement(FitMysteryAppModule.APP_CODE, userId);
            if (decision != null && decision.features() != null && !decision.features().isEmpty()) {
                // 中文说明：FitMystery 报告权限优先使用统一权益中心，旧 billing snapshot 只作为兼容回退。
                return decision.paid() && decision.serverVerified()
                    && decision.features().values().stream().anyMatch(feature ->
                        Boolean.TRUE.equals(feature.enabled())
                            && ("report_access".equals(feature.featureCode()) || "report_history".equals(feature.featureCode()))
                    );
            }
        } catch (Exception ignored) {
            // fallback below
        }
        EntitlementOverviewView overview = billingService.getEntitlements(FitMysteryAppModule.APP_CODE, userId);
        OffsetDateTime now = now();
        return overview.entitlements().stream().anyMatch(item ->
            ("pro_access".equals(item.entitlementCode()) || "report_weekly_access".equals(item.entitlementCode()) || "report_monthly_access".equals(item.entitlementCode()))
                && "active".equalsIgnoreCase(item.status())
                && (item.expiresAt() == null || item.expiresAt().isAfter(now))
        );
    }

    private Map<String, Object> reportGenerationPolicy() {
        Map<String, Object> namespace = configService.namespace("fit_report_generation_policy");
        Object raw = namespace.get("ios_v1");
        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") Map<String, Object> value = new LinkedHashMap<>((Map<String, Object>) map);
            return value;
        }
        return Map.of("version", "report_generation_policy_v1", "freeInitialQuota", 1, "serverStoresReportPayload", false, "proUnlimited", true);
    }

    private Map<String, Object> authorizationResponse(String status, String accessLevel, int remainingQuota, String reportType, String periodKey) {
        return Map.of(
            "status", status,
            "accessLevel", accessLevel,
            "reportType", reportType,
            "periodKey", periodKey,
            "remainingFreeQuota", remainingQuota,
            "serverStoresReportPayload", false,
            "authorizedAt", now().toString()
        );
    }

    private int intValue(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private String normalizeReportType(String value) { String type = value == null ? "weekly" : value.trim().toLowerCase(Locale.ROOT); if (!Set.of("weekly", "monthly").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportType must be weekly or monthly"); return type; }
    private String required(String value, String field) { if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " required"); return value.trim(); }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    public record AuthorizeReportGenerationRequest(
        @Schema(description = "幂等键，用于避免重复授权。", example = "report-20260428-001") String idempotencyKey,
        @Schema(description = "报告类型。", example = "weekly") String reportType,
        @Schema(description = "报告周期键。", example = "2026-W18") String periodKey,
        @Schema(description = "客户端本机数据摘要哈希，后端不接收明细。", example = "sha256-local-data-hash") String localDataHash
    ) {}
}
