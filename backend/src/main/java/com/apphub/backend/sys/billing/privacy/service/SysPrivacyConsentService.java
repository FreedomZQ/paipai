package com.apphub.backend.sys.billing.privacy.service;

import com.apphub.backend.sys.billing.privacy.entity.SysPrivacyConsentEntity;
import com.apphub.backend.sys.billing.privacy.mapper.SysPrivacyConsentMapper;
import com.apphub.backend.sys.billing.privacy.model.PrivacyConsentRequest;
import com.apphub.backend.sys.billing.privacy.model.PrivacyConsentView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SysPrivacyConsentService {
    public static final String CONSENT_REFUND_CONSUMPTION_SHARING = "apple_refund_consumption_data";
    private static final String LEGACY_REFUND_CONSUMPTION_SHARING = "appstore_refund_consumption_sharing";

    private final SysPrivacyConsentMapper consentMapper;
    private final ObjectMapper objectMapper;

    public SysPrivacyConsentService(SysPrivacyConsentMapper consentMapper, ObjectMapper objectMapper) {
        this.consentMapper = consentMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PrivacyConsentView updateConsent(String appCode, Long userId, PrivacyConsentRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // 合规要求：退款消费信息共享必须由付款客户/监护人显式开启；缺省、空值或撤回都按未同意处理。
        boolean consented = request != null && Boolean.TRUE.equals(request.consented());
        SysPrivacyConsentEntity entity = new SysPrivacyConsentEntity();
        entity.setAppCode(appCode);
        entity.setUserId(userId);
        entity.setConsentType(normalizeConsentType(request == null ? null : request.consentType()));
        entity.setConsentStatus(consented ? "granted" : "withdrawn");
        entity.setPolicyVersion(firstNonBlank(request == null ? null : request.policyVersion(), "refund-consumption-v1"));
        entity.setRegionCode(firstNonBlank(request == null ? null : request.regionCode(), "unknown"));
        entity.setSourceType(firstNonBlank(request == null ? null : request.sourceType(), "app"));
        entity.setSourceRef(request == null ? null : request.sourceRef());
        entity.setMetadataJson(toJson(Map.of(
            "dataMinimization", "parent account, transaction id, product id, aggregated entitlement counts only",
            "childrenDataExcluded", true,
            "defaultOff", true
        )));
        entity.setConsentedAt(consented ? now : null);
        entity.setRevokedAt(consented ? null : now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        consentMapper.insert(entity);
        return toView(entity);
    }

    public boolean hasActiveConsent(String appCode, Long userId, String consentType) {
        SysPrivacyConsentEntity latest = latest(appCode, userId, consentType);
        return latest != null && "granted".equalsIgnoreCase(latest.getConsentStatus()) && latest.getRevokedAt() == null;
    }

    public PrivacyConsentView latestView(String appCode, Long userId, String consentType) {
        return toView(latest(appCode, userId, consentType));
    }

    public SysPrivacyConsentEntity latest(String appCode, Long userId, String consentType) {
        if (userId == null) {
            return null;
        }
        return consentMapper.selectLatest(appCode, userId, normalizeConsentType(consentType));
    }

    private String normalizeConsentType(String consentType) {
        String normalized = firstNonBlank(consentType, CONSENT_REFUND_CONSUMPTION_SHARING)
            .trim()
            .toLowerCase();
        // 历史接口名统一归一到文档约定的 consent_type，避免新老客户端写出两套同意义务记录。
        if (LEGACY_REFUND_CONSUMPTION_SHARING.equals(normalized)) {
            return CONSENT_REFUND_CONSUMPTION_SHARING;
        }
        return normalized;
    }

    private PrivacyConsentView toView(SysPrivacyConsentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PrivacyConsentView(
            entity.getAppCode(),
            entity.getUserId(),
            entity.getConsentType(),
            entity.getConsentStatus(),
            entity.getPolicyVersion(),
            entity.getRegionCode(),
            entity.getSourceType(),
            entity.getSourceRef(),
            entity.getConsentedAt() == null ? null : entity.getConsentedAt().toString(),
            entity.getRevokedAt() == null ? null : entity.getRevokedAt().toString()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
