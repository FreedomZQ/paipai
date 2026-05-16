package com.apphub.backend.apps.fitmystery.domain.service.impl;

import com.apphub.backend.apps.fitmystery.domain.entity.FitReportGenerationLedgerEntity;
import com.apphub.backend.apps.fitmystery.domain.mapper.FitMysteryReportMapper;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryReportDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FitMysteryReportDataServiceImpl extends ServiceImpl<FitMysteryReportMapper, FitReportGenerationLedgerEntity> implements FitMysteryReportDataService {
    @Override public int currentBalance(String appCode, Long userId) { return baseMapper.currentBalance(appCode, userId); }
    @Override public int countInitialGrant(String appCode, Long userId) { return baseMapper.countInitialGrant(appCode, userId); }
    @Override public int countByIdempotency(String appCode, Long userId, String idempotencyKey) { return baseMapper.countByIdempotency(appCode, userId, idempotencyKey); }
    @Override public void insertLedger(String id, String appCode, Long userId, String ledgerType, String reportType, int quotaDelta, int balanceAfter, String sourceType, String periodKey, String localDataHash, String idempotencyKey, OffsetDateTime createdAt) { baseMapper.insertLedger(id, appCode, userId, ledgerType, reportType, quotaDelta, balanceAfter, sourceType, periodKey, localDataHash, idempotencyKey, createdAt); }
    @Override public List<Map<String, Object>> recent(String appCode, Long userId, int limit) { return baseMapper.recent(appCode, userId, limit); }
}
