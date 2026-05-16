package com.apphub.backend.apps.fitmystery.domain.service;

import com.apphub.backend.apps.fitmystery.domain.entity.FitReportGenerationLedgerEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface FitMysteryReportDataService extends IService<FitReportGenerationLedgerEntity> {
    int currentBalance(String appCode, Long userId);
    int countInitialGrant(String appCode, Long userId);
    int countByIdempotency(String appCode, Long userId, String idempotencyKey);
    void insertLedger(String id, String appCode, Long userId, String ledgerType, String reportType, int quotaDelta, int balanceAfter, String sourceType, String periodKey, String localDataHash, String idempotencyKey, OffsetDateTime createdAt);
    List<Map<String, Object>> recent(String appCode, Long userId, int limit);
}
