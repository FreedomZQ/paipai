package com.apphub.backend.sys.billing.privacy.service;

import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.billing.privacy.entity.SysEntitlementConsumptionReportEntity;
import com.apphub.backend.sys.billing.privacy.entity.SysEntitlementLedgerEventEntity;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementConsumptionReportMapper;
import com.apphub.backend.sys.billing.privacy.mapper.SysEntitlementLedgerEventMapper;
import com.apphub.backend.sys.billing.privacy.model.EntitlementEventReportRequest;
import com.apphub.backend.sys.billing.privacy.model.EntitlementEventReportView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SysEntitlementConsumptionReportService {
    private final SysEntitlementConsumptionReportMapper reportMapper;
    private final SysEntitlementLedgerEventMapper ledgerEventMapper;
    private final Sha256HashService sha256HashService;

    public SysEntitlementConsumptionReportService(
        SysEntitlementConsumptionReportMapper reportMapper,
        SysEntitlementLedgerEventMapper ledgerEventMapper,
        Sha256HashService sha256HashService
    ) {
        this.reportMapper = reportMapper;
        this.ledgerEventMapper = ledgerEventMapper;
        this.sha256HashService = sha256HashService;
    }

    @Transactional
    public EntitlementEventReportView report(String appCode, Long userId, EntitlementEventReportRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> accepted = new ArrayList<>();
        List<EntitlementEventReportView.RejectedEvent> rejected = new ArrayList<>();
        if (request == null || request.events() == null) {
            return new EntitlementEventReportView(List.of(), List.of(new EntitlementEventReportView.RejectedEvent(null, "invalid_payload", "events is required")), null, true);
        }
        for (EntitlementEventReportRequest.EntitlementEventItem item : request.events()) {
            EventValidation validation = validate(item);
            if (!validation.accepted()) {
                rejected.add(new EntitlementEventReportView.RejectedEvent(item == null ? null : item.eventId(), validation.reasonCode(), validation.message()));
                continue;
            }
            String eventId = validation.eventId().toString();
            SysEntitlementConsumptionReportEntity existing = findExisting(appCode, validation.eventId(), item.idempotencyKey());
            if (existing != null) {
                accepted.add(eventId);
                continue;
            }
            SysEntitlementConsumptionReportEntity report = new SysEntitlementConsumptionReportEntity();
            report.setAppCode(appCode);
            report.setUserId(userId);
            report.setEventId(validation.eventId());
            report.setIdempotencyKey(item.idempotencyKey());
            report.setEventType("consume");
            report.setEntitlementCode(item.entitlementCode());
            report.setEntitlementTokenId(item.entitlementTokenId());
            report.setTransactionId(item.transactionId());
            report.setOriginalTransactionId(item.originalTransactionId());
            report.setQuantity(Math.max(1, item.quantity() == null ? 1 : item.quantity()));
            report.setClientEntitlementVersion(item.clientEntitlementVersion());
            report.setDeviceIdHash(sha256HashService.hash(request.deviceId()));
            report.setAppInstanceIdHash(sha256HashService.hash(request.appInstanceId()));
            report.setLocalCreatedAt(parseTime(item.localCreatedAt()));
            report.setReportStatus("accepted");
            report.setRefundStatus("none");
            report.setCountedInRefundDecision(false);
            report.setCreatedAt(now);
            report.setUpdatedAt(now);
            reportMapper.insert(report);
            writeLedgerEvent(appCode, userId, report, now);
            accepted.add(eventId);
        }
        return new EntitlementEventReportView(List.copyOf(accepted), List.copyOf(rejected), System.currentTimeMillis(), !rejected.isEmpty());
    }

    private void writeLedgerEvent(String appCode, Long userId, SysEntitlementConsumptionReportEntity report, OffsetDateTime now) {
        SysEntitlementLedgerEventEntity event = new SysEntitlementLedgerEventEntity();
        event.setAppCode(appCode);
        event.setUserId(userId);
        event.setEventId(report.getEventId());
        event.setEventType("consume");
        event.setEntitlementCode(report.getEntitlementCode());
        event.setEntitlementTokenId(report.getEntitlementTokenId());
        event.setTransactionId(report.getTransactionId());
        event.setOriginalTransactionId(report.getOriginalTransactionId());
        event.setRefundStatus("none");
        event.setRefundEffectType("none");
        event.setRefundedQuantity(0);
        event.setQuantityDelta(-Math.max(1, report.getQuantity() == null ? 1 : report.getQuantity()));
        event.setEntitlementVersion(System.currentTimeMillis());
        event.setReasonCode("client_usage_report");
        event.setSourceType("client_outbox");
        event.setSourceRef(report.getIdempotencyKey());
        event.setMetadataJson("{\"childrenDataExcluded\":true}");
        event.setCreatedAt(now);
        ledgerEventMapper.insert(event);
    }

    private SysEntitlementConsumptionReportEntity findExisting(String appCode, UUID eventId, String idempotencyKey) {
        LambdaQueryWrapper<SysEntitlementConsumptionReportEntity> wrapper = new LambdaQueryWrapper<SysEntitlementConsumptionReportEntity>()
            .eq(SysEntitlementConsumptionReportEntity::getAppCode, appCode)
            .and(w -> w.eq(SysEntitlementConsumptionReportEntity::getEventId, eventId)
                .or()
                .eq(idempotencyKey != null && !idempotencyKey.isBlank(), SysEntitlementConsumptionReportEntity::getIdempotencyKey, idempotencyKey))
            .last("LIMIT 1");
        return reportMapper.selectOne(wrapper);
    }

    private EventValidation validate(EntitlementEventReportRequest.EntitlementEventItem item) {
        if (item == null) {
            return new EventValidation(false, null, "invalid_event", "event is required");
        }
        UUID eventId;
        try {
            eventId = item.eventId() == null || item.eventId().isBlank() ? UUID.randomUUID() : UUID.fromString(item.eventId());
        } catch (Exception ex) {
            return new EventValidation(false, null, "invalid_event_id", "eventId must be a UUID");
        }
        if (item.entitlementCode() == null || item.entitlementCode().isBlank()) {
            return new EventValidation(false, eventId, "missing_entitlement_code", "entitlementCode is required");
        }
        if (item.quantity() != null && item.quantity() <= 0) {
            return new EventValidation(false, eventId, "invalid_quantity", "quantity must be greater than zero");
        }
        if (item.transactionId() == null || item.transactionId().isBlank()) {
            return new EventValidation(false, eventId, "missing_transaction_id", "transactionId is required for refund-safe usage accounting");
        }
        return new EventValidation(true, eventId, null, null);
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private record EventValidation(boolean accepted, UUID eventId, String reasonCode, String message) {
    }
}
