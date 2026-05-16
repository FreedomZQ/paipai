package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 拍拍伴读 usage 时间策略读取服务。
 *
 * <p>这里刻意不把「30 天留存」「最近 N 天展示」等时间窗口硬编码在业务代码里，
 * 而是统一从 sys_remote_config 的 reading_usage_policy namespace 读取。这样个人开发者上线后
 * 可以通过数据库配置调整留存与展示窗口，不需要重新发版；未来新增 App 也应使用自己的 app_code
 * 独立配置，避免把拍拍伴读的儿童数据/家长区策略误复用到其他产品。</p>
 *
 * <p>时间口径说明：服务端统一用 UTC 保存 OffsetDateTime；按天聚合默认使用 UTC 日期作为后端兜底。
 * iOS 端离线家长区会按设备本地日历展示“今天/最近 N 天”，更贴近全球用户所在地的自然日体验。
 * 若未来需要服务端严格按用户所在时区聚合，应在用户偏好中显式保存 IANA timezone 后再扩展。</p>
 */
@Service
public class ReadingUsagePolicyService {
    public static final String NAMESPACE = "reading_usage_policy";
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final int DEFAULT_RECENT_SUMMARY_DAYS = 7;
    private static final int DEFAULT_MAX_SESSION_HOURS = 24;
    private static final int MIN_RETENTION_DAYS = 1;
    private static final int MAX_RETENTION_DAYS = 366;
    private static final int MIN_RECENT_SUMMARY_DAYS = 1;
    private static final int MAX_RECENT_SUMMARY_DAYS = 31;
    private static final int MIN_MAX_SESSION_HOURS = 1;
    private static final int MAX_MAX_SESSION_HOURS = 24;

    private final SysRemoteConfigService sysRemoteConfigService;

    public ReadingUsagePolicyService(SysRemoteConfigService sysRemoteConfigService) {
        this.sysRemoteConfigService = sysRemoteConfigService;
    }

    public UsagePolicyView currentPolicy() {
        Map<String, Object> items = sysRemoteConfigService.loadNamespace(APP_CODE, NAMESPACE).items();
        return new UsagePolicyView(
            clamp(intValue(items.get("retentionDays"), DEFAULT_RETENTION_DAYS), MIN_RETENTION_DAYS, MAX_RETENTION_DAYS),
            clamp(intValue(items.get("recentSummaryDays"), DEFAULT_RECENT_SUMMARY_DAYS), MIN_RECENT_SUMMARY_DAYS, MAX_RECENT_SUMMARY_DAYS),
            stringValue(items.get("dayBoundary"), "client_local"),
            clamp(intValue(items.get("maxSessionHours"), DEFAULT_MAX_SESSION_HOURS), MIN_MAX_SESSION_HOURS, MAX_MAX_SESSION_HOURS)
        );
    }

    private int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String stringValue(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw).trim();
        return value.isBlank() ? fallback : value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public record UsagePolicyView(
        int retentionDays,
        int recentSummaryDays,
        String dayBoundary,
        int maxSessionHours
    ) {}
}
