package com.apphub.backend.sys.compensation.service;

import com.apphub.backend.apps.reading.compat.service.ReadingCloudUsageService;
import com.apphub.backend.common.util.Sha256HashService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.compensation.entity.SysCompensationCodeEntity;
import com.apphub.backend.sys.compensation.entity.SysCompensationRedemptionEntity;
import com.apphub.backend.sys.compensation.mapper.SysCompensationCodeMapper;
import com.apphub.backend.sys.compensation.mapper.SysCompensationRedemptionMapper;
import com.apphub.backend.sys.compensation.model.CompensationCodeCreateRequest;
import com.apphub.backend.sys.compensation.model.CompensationCodeView;
import com.apphub.backend.sys.compensation.model.CompensationRedeemResultView;
import com.apphub.backend.sys.entitlement.entity.SysMembershipPlanEntity;
import com.apphub.backend.sys.entitlement.entity.SysUserEntitlementGrantEntity;
import com.apphub.backend.sys.entitlement.mapper.SysUserEntitlementGrantMapper;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class SysCompensationService {
    public static final String STATUS_UNUSED = "unused";
    public static final String STATUS_USED = "used";
    public static final String STATUS_VOIDED = "voided";
    public static final String BENEFIT_PLAN = "plan";
    public static final String BENEFIT_USAGE_CREDIT = "usage_credit";
    private static final Pattern CODE_PATTERN = Pattern.compile("^PP-(?:[A-Z2-9]{5}-){2}[A-Z2-9]{5}$");
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_BODY_LENGTH = 15;

    private final SysCompensationCodeMapper codeMapper;
    private final SysCompensationRedemptionMapper redemptionMapper;
    private final SysUserEntitlementGrantMapper userEntitlementGrantMapper;
    private final SysEntitlementCenterService entitlementCenterService;
    private final ReadingCloudUsageService readingCloudUsageService;
    private final AppDefinitionService appDefinitionService;
    private final ObjectMapper objectMapper;
    private final Sha256HashService sha256HashService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SysCompensationService(
        SysCompensationCodeMapper codeMapper,
        SysCompensationRedemptionMapper redemptionMapper,
        SysUserEntitlementGrantMapper userEntitlementGrantMapper,
        SysEntitlementCenterService entitlementCenterService,
        ReadingCloudUsageService readingCloudUsageService,
        AppDefinitionService appDefinitionService,
        ObjectMapper objectMapper,
        Sha256HashService sha256HashService
    ) {
        this.codeMapper = codeMapper;
        this.redemptionMapper = redemptionMapper;
        this.userEntitlementGrantMapper = userEntitlementGrantMapper;
        this.entitlementCenterService = entitlementCenterService;
        this.readingCloudUsageService = readingCloudUsageService;
        this.appDefinitionService = appDefinitionService;
        this.objectMapper = objectMapper;
        this.sha256HashService = sha256HashService;
    }

    @Transactional
    public CompensationCodeView createCode(String appCode, Long operatorUserId, CompensationCodeCreateRequest request) {
        requireApp(appCode);
        String normalizedBenefitType = normalizeBenefitType(request.benefitType());
        String code = resolveCode(appCode, request.compensationCode());
        if (codeMapper.selectCount(new LambdaQueryWrapper<SysCompensationCodeEntity>()
            .eq(SysCompensationCodeEntity::getAppCode, appCode)
            .eq(SysCompensationCodeEntity::getCompensationCode, code)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "补偿码已存在");
        }

        OffsetDateTime now = now();
        SysCompensationCodeEntity entity = new SysCompensationCodeEntity();
        entity.setAppCode(appCode);
        entity.setCompensationCode(code);
        entity.setCodeHash(sha256HashService.hash(code));
        entity.setBenefitType(normalizedBenefitType);
        entity.setPlanCode(blankToNull(request.planCode()));
        entity.setEntitlementCode(resolveEntitlementCode(appCode, normalizedBenefitType, request.planCode(), request.entitlementCode()));
        entity.setServiceType(resolveServiceType(normalizedBenefitType, request.serviceType()));
        entity.setGrantCount(resolveGrantCount(normalizedBenefitType, request.grantCount()));
        entity.setGrantValidDays(request.grantValidDays());
        entity.setExpiresAt(resolveExpiresAt(now, request.expiresAt(), request.grantValidDays()));
        entity.setMaxUses(1);
        entity.setUsedCount(0);
        entity.setStatus(STATUS_UNUSED);
        entity.setMetadataJson(toJson(Map.of(
            "note", blankToNull(request.note()) == null ? "" : request.note(),
            "benefitType", normalizedBenefitType
        )));
        entity.setCreatedByUserId(operatorUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        codeMapper.insert(entity);
        return toView(entity);
    }

    public List<CompensationCodeView> listCodes(String appCode, String status, String benefitType) {
        requireApp(appCode);
        return codeMapper.selectList(new LambdaQueryWrapper<SysCompensationCodeEntity>()
            .eq(SysCompensationCodeEntity::getAppCode, appCode)
            .eq(status != null && !status.isBlank(), SysCompensationCodeEntity::getStatus, status.trim().toLowerCase(Locale.ROOT))
            .eq(benefitType != null && !benefitType.isBlank(), SysCompensationCodeEntity::getBenefitType, normalizeBenefitType(benefitType))
            .orderByDesc(SysCompensationCodeEntity::getCreatedAt)
            .orderByDesc(SysCompensationCodeEntity::getId))
            .stream()
            .map(this::toView)
            .toList();
    }

    public CompensationCodeView getCode(String appCode, String compensationCode) {
        requireApp(appCode);
        SysCompensationCodeEntity entity = selectExistingCode(appCode, compensationCode);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "补偿码不存在");
        }
        return toView(entity);
    }

    @Transactional
    public CompensationCodeView voidCode(String appCode, String compensationCode, String reason) {
        requireApp(appCode);
        SysCompensationCodeEntity entity = selectExistingCode(appCode, compensationCode);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "补偿码不存在");
        }
        if (STATUS_USED.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "补偿码已使用，不能作废");
        }
        if (STATUS_VOIDED.equals(entity.getStatus())) {
            return toView(entity);
        }
        OffsetDateTime now = now();
        entity.setStatus(STATUS_VOIDED);
        entity.setVoidReason(blankToNull(reason));
        entity.setUpdatedAt(now);
        codeMapper.updateById(entity);
        return toView(entity);
    }

    @Transactional
    public CompensationRedeemResultView redeem(String appCode, Long userId, String rawCompensationCode) {
        requireApp(appCode);
        String compensationCode = normalizeCode(rawCompensationCode);
        if (!CODE_PATTERN.matcher(compensationCode).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码格式不正确");
        }
        SysCompensationCodeEntity entity = selectExistingCode(appCode, compensationCode);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "补偿码不存在");
        }
        OffsetDateTime now = now();
        if (entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "补偿码已过期");
        }
        if (!STATUS_UNUSED.equals(entity.getStatus()) || (entity.getUsedCount() != null && entity.getUsedCount() >= safe(entity.getMaxUses()))) {
            if (STATUS_VOIDED.equals(entity.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "补偿码已作废");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "补偿码已使用");
        }
        int affected = codeMapper.update(
            null,
            new LambdaUpdateWrapper<SysCompensationCodeEntity>()
                .eq(SysCompensationCodeEntity::getId, entity.getId())
                .eq(SysCompensationCodeEntity::getStatus, STATUS_UNUSED)
                .eq(SysCompensationCodeEntity::getUsedCount, safe(entity.getUsedCount()))
                .set(SysCompensationCodeEntity::getStatus, STATUS_USED)
                .set(SysCompensationCodeEntity::getUsedCount, safe(entity.getUsedCount()) + 1)
                .set(SysCompensationCodeEntity::getUsedByUserId, userId)
                .set(SysCompensationCodeEntity::getUsedAt, now)
                .set(SysCompensationCodeEntity::getUpdatedAt, now)
        );
        if (affected <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "补偿码已使用");
        }

        String benefitSummary = applyBenefit(appCode, userId, entity, now);
        SysCompensationRedemptionEntity record = new SysCompensationRedemptionEntity();
        record.setAppCode(appCode);
        record.setUserId(userId);
        record.setCompensationCodeId(entity.getId());
        record.setCompensationCode(entity.getCompensationCode());
        record.setBenefitType(entity.getBenefitType());
        record.setBenefitSummary(benefitSummary);
        record.setPlanCode(entity.getPlanCode());
        record.setEntitlementCode(entity.getEntitlementCode());
        record.setServiceType(entity.getServiceType());
        record.setGrantCount(entity.getGrantCount());
        record.setRedeemAt(now);
        record.setValidUntilAt(resolveGrantExpireAt(now, entity));
        record.setStatus("applied");
        record.setResultMessage("补偿成功");
        record.setBeforeJson("{}");
        record.setAfterJson("{}");
        record.setMetadataJson(entity.getMetadataJson());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        redemptionMapper.insert(record);

        return new CompensationRedeemResultView(
            entity.getCompensationCode(),
            record.getStatus(),
            entity.getBenefitType(),
            benefitSummary,
            entity.getPlanCode(),
            entity.getEntitlementCode(),
            entity.getServiceType(),
            entity.getGrantCount(),
            record.getValidUntilAt() == null ? null : record.getValidUntilAt().toString(),
            now.toString(),
            "补偿成功",
            null
        );
    }

    private String applyBenefit(String appCode, Long userId, SysCompensationCodeEntity entity, OffsetDateTime now) {
        if (BENEFIT_USAGE_CREDIT.equals(entity.getBenefitType())) {
            String serviceType = blankToNull(entity.getServiceType());
            if (serviceType == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码缺少次数类型");
            }
            int grantCount = safe(entity.getGrantCount());
            int validDays = entity.getGrantValidDays() == null ? 30 : Math.max(entity.getGrantValidDays(), 1);
            readingCloudUsageService.grantPurchase(userId, serviceType, entity.getCompensationCode(), grantCount, validDays, entity.getCompensationCode());
            return "补偿 " + grantCount + " 次 " + serviceLabel(serviceType) + "，有效期至 " + now.plusDays(validDays);
        }

        if (BENEFIT_PLAN.equals(entity.getBenefitType())) {
            String planCode = blankToNull(entity.getPlanCode());
            if (planCode == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码缺少权益方案");
            }
            SysMembershipPlanEntity plan = entitlementCenterService.listPlans(appCode).stream()
                .filter(item -> planCode.equals(item.getPlanCode()))
                .findFirst()
                .orElse(null);
            if (plan == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码关联的权益方案不存在");
            }
            SysUserEntitlementGrantEntity grant = new SysUserEntitlementGrantEntity();
            grant.setAppCode(appCode);
            grant.setUserId(userId);
            grant.setGrantCode(entity.getCompensationCode());
            grant.setPlanCode(plan.getPlanCode());
            grant.setEntitlementCode(blankToNull(entity.getEntitlementCode()) == null ? plan.getEntitlementCode() : entity.getEntitlementCode());
            grant.setSourceType("compensation_code");
            grant.setSourceRef(entity.getCompensationCode());
            grant.setStatus("active");
            grant.setStartsAt(now);
            grant.setExpiresAt(resolveGrantExpireAt(now, entity));
            grant.setReason("补偿码兑换");
            grant.setOperatorUserId(null);
            grant.setMetadataJson(entity.getMetadataJson());
            grant.setCreatedAt(now);
            grant.setUpdatedAt(now);
            userEntitlementGrantMapper.insert(grant);
            return "补偿权益方案 " + plan.getDisplayName();
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持的补偿类型");
    }

    private OffsetDateTime resolveGrantExpireAt(OffsetDateTime now, SysCompensationCodeEntity entity) {
        if (entity.getGrantValidDays() != null && entity.getGrantValidDays() > 0) {
            return now.plusDays(entity.getGrantValidDays());
        }
        return entity.getExpiresAt();
    }

    private CompensationCodeView toView(SysCompensationCodeEntity entity) {
        Map<String, Object> metadata = decodeJson(entity.getMetadataJson());
        return new CompensationCodeView(
            entity.getId(),
            entity.getAppCode(),
            entity.getCompensationCode(),
            entity.getBenefitType(),
            entity.getPlanCode(),
            entity.getEntitlementCode(),
            entity.getServiceType(),
            entity.getGrantCount(),
            entity.getGrantValidDays(),
            entity.getExpiresAt(),
            entity.getMaxUses(),
            entity.getUsedCount(),
            entity.getStatus(),
            entity.getUsedByUserId(),
            entity.getUsedAt(),
            entity.getVoidReason(),
            metadata,
            entity.getCreatedByUserId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private SysCompensationCodeEntity selectExistingCode(String appCode, String compensationCode) {
        return codeMapper.selectOne(new LambdaQueryWrapper<SysCompensationCodeEntity>()
            .eq(SysCompensationCodeEntity::getAppCode, appCode)
            .eq(SysCompensationCodeEntity::getCompensationCode, normalizeCode(compensationCode))
            .last("LIMIT 1"));
    }

    private String resolveCode(String appCode, String rawCode) {
        String normalized = blankToNull(rawCode) == null ? null : normalizeCode(rawCode);
        if (normalized != null) {
            if (!CODE_PATTERN.matcher(normalized).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码格式不正确");
            }
            return normalized;
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            String generated = generateCode();
            if (codeMapper.selectCount(new LambdaQueryWrapper<SysCompensationCodeEntity>()
                .eq(SysCompensationCodeEntity::getAppCode, appCode)
                .eq(SysCompensationCodeEntity::getCompensationCode, generated)) == 0) {
                return generated;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "补偿码生成失败");
    }

    private String generateCode() {
        char[] body = new char[CODE_BODY_LENGTH];
        for (int i = 0; i < body.length; i++) {
            body[i] = CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)];
        }
        return "PP-" + new String(body, 0, 5) + "-" + new String(body, 5, 5) + "-" + new String(body, 10, 5);
    }

    private String normalizeCode(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (compact.matches("^PP[A-Z2-9]{15}$")) {
            String body = compact.substring(2);
            return "PP-" + body.substring(0, 5) + "-" + body.substring(5, 10) + "-" + body.substring(10);
        }
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String normalizeBenefitType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿类型不能为空");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!BENEFIT_PLAN.equals(normalized) && !BENEFIT_USAGE_CREDIT.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持的补偿类型");
        }
        return normalized;
    }

    private String resolveEntitlementCode(String appCode, String benefitType, String planCode, String entitlementCode) {
        if (!BENEFIT_PLAN.equals(benefitType)) {
            return blankToNull(entitlementCode);
        }
        if (blankToNull(entitlementCode) != null) {
            return entitlementCode.trim();
        }
        String resolvedPlanCode = blankToNull(planCode);
        if (resolvedPlanCode == null) {
            return null;
        }
        return entitlementCenterService.listPlans(appCode).stream()
            .filter(item -> resolvedPlanCode.equals(item.getPlanCode()))
            .findFirst()
            .map(SysMembershipPlanEntity::getEntitlementCode)
            .orElse(null);
    }

    private String resolveServiceType(String benefitType, String serviceType) {
        if (!BENEFIT_USAGE_CREDIT.equals(benefitType)) {
            return blankToNull(serviceType);
        }
        String normalized = blankToNull(serviceType);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码缺少次数类型");
        }
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private Integer resolveGrantCount(String benefitType, Integer grantCount) {
        if (BENEFIT_USAGE_CREDIT.equals(benefitType)) {
            if (grantCount == null || grantCount < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补偿码次数不能为空");
            }
            return grantCount;
        }
        return grantCount == null ? 1 : Math.max(grantCount, 1);
    }

    private OffsetDateTime resolveExpiresAt(OffsetDateTime now, OffsetDateTime expiresAt, Integer grantValidDays) {
        if (expiresAt != null) {
            return expiresAt;
        }
        if (grantValidDays != null && grantValidDays > 0) {
            return now.plusDays(grantValidDays);
        }
        return null;
    }

    private void requireApp(String appCode) {
        AppDefinition definition = appDefinitionService.get(appCode).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在"));
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在");
        }
    }

    private Map<String, Object> decodeJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of("raw", json);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String serviceLabel(String serviceType) {
        if (serviceType == null) {
            return "次数";
        }
        return switch (serviceType) {
            case ReadingCloudUsageService.LOCAL_CAPTURE -> "拍读";
            case ReadingCloudUsageService.LOCAL_SPEECH -> "朗读";
            case ReadingCloudUsageService.CLOUD_OCR -> "云端 OCR";
            case ReadingCloudUsageService.CLOUD_TTS -> "云端朗读";
            default -> serviceType;
        };
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
