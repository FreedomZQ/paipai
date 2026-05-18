package com.apphub.backend.apps.reading.privacy;

import com.apphub.backend.apps.reading.ReadingAppModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 拍拍伴读合规留存清理任务。
 * 中文说明：个人开发者不额外引入任务队列，先用 Spring 定时任务做低运维清理；所有 SQL 都只处理低敏审计、
 * 过期权益 reservation、诊断日志和已到期的最小保留记录，不触碰或恢复儿童正文内容。
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "backend.apps.paipai_readingcompanion.privacy.retention", name = "job-enabled", havingValue = "true", matchIfMissing = true)
public class ReadingPrivacyRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(ReadingPrivacyRetentionJob.class);
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final JdbcTemplate jdbcTemplate;

    public ReadingPrivacyRetentionJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${backend.apps.paipai_readingcompanion.privacy.retention.cron:0 17 3 * * *}", zone = "UTC")
    public void runScheduledCleanup() {
        CleanupReceipt receipt = cleanupNow(OffsetDateTime.now(ZoneOffset.UTC));
        log.info(
            "reading privacy retention cleanup completed appCode={} expiredReservations={} diagnosticsDeleted={} privacyEventsDeleted={} purchaseRetentionDeleted={} tombstonesDeleted={}",
            APP_CODE,
            receipt.expiredReservations(),
            receipt.diagnosticsDeleted(),
            receipt.privacyEventsDeleted(),
            receipt.purchaseRetentionDeleted(),
            receipt.tombstonesDeleted()
        );
    }

    public CleanupReceipt cleanupNow(OffsetDateTime now) {
        int expiredReservations = jdbcTemplate.update("""
            UPDATE reading_entitlement_reservation
               SET status = 'expired', updated_at = ?
             WHERE app_code = ?
               AND status = 'reserved'
               AND expires_at IS NOT NULL
               AND expires_at < ?
            """, now, APP_CODE, now);

        int diagnosticsDeleted = jdbcTemplate.update("""
            DELETE FROM sys_user_device_event
             WHERE app_code = ?
               AND created_at < ?
            """, APP_CODE, now.minusDays(30));

        int privacyEventsDeleted = jdbcTemplate.update("""
            DELETE FROM reading_privacy_event
             WHERE app_code = ?
               AND retention_policy_code = 'privacy_audit_180d'
               AND created_at < ?
            """, APP_CODE, now.minusDays(180));

        int purchaseRetentionDeleted = jdbcTemplate.update("""
            DELETE FROM reading_privacy_purchase_retention
             WHERE app_code = ?
               AND retention_expires_at < ?
            """, APP_CODE, now);

        int tombstonesDeleted = jdbcTemplate.update("""
            DELETE FROM reading_deleted_user_tombstone
             WHERE app_code = ?
               AND retention_expires_at IS NOT NULL
               AND retention_expires_at < ?
            """, APP_CODE, now);

        return new CleanupReceipt(
            expiredReservations,
            diagnosticsDeleted,
            privacyEventsDeleted,
            purchaseRetentionDeleted,
            tombstonesDeleted,
            now.toString()
        );
    }

    public record CleanupReceipt(
        int expiredReservations,
        int diagnosticsDeleted,
        int privacyEventsDeleted,
        int purchaseRetentionDeleted,
        int tombstonesDeleted,
        String cleanedAt
    ) {}
}
