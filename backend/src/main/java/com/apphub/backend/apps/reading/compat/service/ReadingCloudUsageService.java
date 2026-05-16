package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceCreditGrantEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceUsageEntity;
import com.apphub.backend.apps.reading.domain.entity.ReadingCloudServiceUsageLogEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingCloudServiceCreditGrantMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingCloudServiceUsageLogMapper;
import com.apphub.backend.apps.reading.domain.mapper.ReadingCloudServiceUsageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * reading 云端服务次数控制服务。
 * 负责校验和记录云端 OCR / 云端朗读的试用与购买次数，防止开发者在云端成本侧被超额调用拖垮。
 */
@Service
public class ReadingCloudUsageService {
    public static final String CLOUD_OCR = "cloud_ocr";
    public static final String CLOUD_TTS = "cloud_tts";
    public static final String LOCAL_CAPTURE = "capture";
    public static final String LOCAL_SPEECH = "speech";
    private static final String APP_CODE = ReadingAppModule.APP_CODE;
    private static final int DEFAULT_GIFT_VALID_DAYS = 30;

    private final ReadingCloudServiceUsageMapper usageMapper;
    private final ReadingCloudServiceUsageLogMapper logMapper;
    private final ReadingCloudServiceCreditGrantMapper creditGrantMapper;
    private final ReadingDailyQuotaConfigService dailyQuotaConfigService;

    @Autowired
    public ReadingCloudUsageService(
        ReadingCloudServiceUsageMapper usageMapper,
        ReadingCloudServiceUsageLogMapper logMapper,
        ReadingCloudServiceCreditGrantMapper creditGrantMapper,
        ReadingDailyQuotaConfigService dailyQuotaConfigService
    ) {
        this.usageMapper = usageMapper;
        this.logMapper = logMapper;
        this.creditGrantMapper = creditGrantMapper;
        this.dailyQuotaConfigService = dailyQuotaConfigService;
    }

    public ReadingCloudUsageService(
        ReadingCloudServiceUsageMapper usageMapper,
        ReadingCloudServiceUsageLogMapper logMapper,
        ReadingDailyQuotaConfigService dailyQuotaConfigService
    ) {
        this(usageMapper, logMapper, null, dailyQuotaConfigService);
    }

    @Transactional
    public CloudUsageDecision ensureQuota(Long userId, String serviceType) {
        ReadingCloudServiceUsageEntity entity = ensureUsageEntity(userId, serviceType);
        int remaining = remaining(entity);
        if (remaining <= 0) {
            return exhaustedDecision(entity.getServiceType());
        }
        return allowedDecision(entity.getServiceType(), remaining);
    }

    @Transactional
    public CloudUsageDecision consume(Long userId, String serviceType) {
        ReadingCloudServiceUsageEntity entity = ensureUsageEntity(userId, serviceType);
        int beforeRemaining = remaining(entity);
        int beforeTrialUsed = value(entity.getTrialUsed());
        int beforePurchasedCredits = value(entity.getPurchasedCredits());
        int beforePurchasedUsed = value(entity.getPurchasedUsed());
        int trialRemaining = Math.max(value(entity.getTrialLimit()) - beforeTrialUsed, 0);
        int purchasedRemaining = purchasedRemaining(userId, normalizeServiceType(serviceType), entity);
        if (trialRemaining <= 0 && purchasedRemaining <= 0) {
            return exhaustedDecision(serviceType);
        }
        if (consumeCreditGrantIfAvailable(userId, normalizeServiceType(serviceType))) {
            entity.setPurchasedUsed(beforePurchasedUsed + 1);
        } else if (purchasedRemaining > 0) {
            entity.setPurchasedUsed(beforePurchasedUsed + 1);
        } else {
            entity.setTrialUsed(beforeTrialUsed + 1);
        }
        entity.setUpdatedAt(now());
        usageMapper.updateById(entity);
        int afterRemaining = remaining(entity);
        insertLog(entity, -1, beforeRemaining, afterRemaining, beforeTrialUsed, beforePurchasedCredits, beforePurchasedUsed, "consume", "system", "system", null);
        return allowedDecision(serviceType, afterRemaining);
    }

    @Transactional
    public CloudUsageDecision grantPurchase(Long userId, String serviceType, String productCode, int amount, int validDays, String purchaseRef) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }
        String normalizedServiceType = normalizeServiceType(serviceType);
        int safeValidDays = Math.min(Math.max(validDays, 1), 3660);
        if (creditGrantMapper != null && purchaseRef != null && !purchaseRef.isBlank()) {
            ReadingCloudServiceCreditGrantEntity existing = creditGrantMapper.selectBySourceRef(APP_CODE, userId, normalizedServiceType, "internal_purchase", purchaseRef.trim());
            if (existing != null) {
                ReadingCloudServiceUsageEntity existingEntity = ensureUsageEntity(userId, normalizedServiceType);
                return allowedDecision(normalizedServiceType, remaining(existingEntity));
            }
        }
        ReadingCloudServiceUsageEntity entity = ensureUsageEntity(userId, normalizedServiceType);
        int beforeRemaining = remaining(entity);
        int beforeTrialUsed = value(entity.getTrialUsed());
        int beforePurchasedCredits = value(entity.getPurchasedCredits());
        int beforePurchasedUsed = value(entity.getPurchasedUsed());
        entity.setPurchasedCredits(beforePurchasedCredits + amount);
        entity.setUpdatedAt(now());
        usageMapper.updateById(entity);
        if (creditGrantMapper != null) {
            ReadingCloudServiceCreditGrantEntity grant = new ReadingCloudServiceCreditGrantEntity();
            grant.setAppCode(APP_CODE);
            grant.setUserId(userId);
            grant.setServiceType(normalizedServiceType);
            grant.setGrantType("paid");
            grant.setTotalCount(amount);
            grant.setUsedCount(0);
            grant.setSourceType("internal_purchase");
            grant.setSourceRef(blankToNull(purchaseRef));
            grant.setProductCode(blankToNull(productCode));
            grant.setExpiresAt(now().plusDays(safeValidDays));
            grant.setCreatedAt(now());
            grant.setUpdatedAt(now());
            creditGrantMapper.insert(grant);
        }
        int afterRemaining = remaining(entity);
        insertLog(entity, amount, beforeRemaining, afterRemaining, beforeTrialUsed, beforePurchasedCredits, beforePurchasedUsed, "internal_purchase", "user", String.valueOf(userId), blankToNull(purchaseRef));
        return allowedDecision(normalizedServiceType, afterRemaining);
    }

    public int countDailyInternalPurchases(Long userId, String serviceType, OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        if (creditGrantMapper == null) {
            return 0;
        }
        return creditGrantMapper.countDailyInternalPurchases(APP_CODE, userId, normalizeServiceType(serviceType), dayStart, dayEnd);
    }

    @Transactional
    public CloudUsageDecision adjust(Long userId, String serviceType, int delta, String reason, String operatorId, String idempotencyKey) {
        return adjust(userId, serviceType, delta, reason, operatorId, idempotencyKey, DEFAULT_GIFT_VALID_DAYS);
    }

    @Transactional
    public CloudUsageDecision adjust(Long userId, String serviceType, int delta, String reason, String operatorId, String idempotencyKey, Integer validDays) {
        if (delta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delta must not be zero");
        }
        String normalizedServiceType = normalizeServiceType(serviceType);
        ReadingCloudServiceUsageEntity entity = ensureUsageEntity(userId, normalizedServiceType);
        int beforeRemaining = remaining(entity);
        int beforeTrialUsed = value(entity.getTrialUsed());
        int beforePurchasedCredits = value(entity.getPurchasedCredits());
        int beforePurchasedUsed = value(entity.getPurchasedUsed());
        if (delta > 0) {
            if (creditGrantMapper == null) {
                entity.setPurchasedCredits(beforePurchasedCredits + delta);
            } else {
                insertAdminGiftGrant(userId, normalizedServiceType, delta, idempotencyKey, validDays);
            }
        } else {
            int decrease = Math.min(-delta, beforeRemaining);
            int remainingDecrease = decrease;
            if (creditGrantMapper != null) {
                remainingDecrease = Math.max(decrease - reduceActiveCreditGrants(userId, normalizedServiceType, decrease), 0);
            } else {
                int purchasedAvailable = Math.max(beforePurchasedCredits - beforePurchasedUsed, 0);
                int purchasedDecrease = Math.min(decrease, purchasedAvailable);
                entity.setPurchasedCredits(beforePurchasedCredits - purchasedDecrease);
                remainingDecrease = decrease - purchasedDecrease;
            }
            if (remainingDecrease > 0 && value(entity.getTrialLimit()) > 0) {
                entity.setTrialUsed(Math.min(value(entity.getTrialLimit()), beforeTrialUsed + remainingDecrease));
            }
        }
        entity.setUpdatedAt(now());
        usageMapper.updateById(entity);
        int afterRemaining = remaining(entity);
        insertLog(entity, afterRemaining - beforeRemaining, beforeRemaining, afterRemaining, beforeTrialUsed, beforePurchasedCredits, beforePurchasedUsed, normalizedReason(reason), "admin", operatorId, blankToNull(idempotencyKey));
        return allowedDecision(normalizedServiceType, afterRemaining);
    }

    @Transactional
    public List<CloudUsageDecision> grantBatch(List<Long> userIds, String serviceType, int amount, String reason, String operatorId) {
        return grantBatch(userIds, serviceType, amount, reason, operatorId, DEFAULT_GIFT_VALID_DAYS);
    }

    @Transactional
    public List<CloudUsageDecision> grantBatch(List<Long> userIds, String serviceType, int amount, String reason, String operatorId, Integer validDays) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userIds is required");
        }
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
        }
        String normalizedServiceType = normalizeServiceType(serviceType);
        return userIds.stream()
            .distinct()
            .map(userId -> adjust(userId, normalizedServiceType, amount, reason, operatorId, null, validDays))
            .toList();
    }

    public CloudUsageSnapshot snapshot(Long userId) {
        ReadingCloudServiceUsageEntity ocr = ensureUsageEntity(userId, CLOUD_OCR);
        ReadingCloudServiceUsageEntity tts = ensureUsageEntity(userId, CLOUD_TTS);
        return new CloudUsageSnapshot(toQuota(CLOUD_OCR, ocr), toQuota(CLOUD_TTS, tts));
    }

    public List<CloudUsageLogView> recentLogs(Long userId, String serviceType, int limit) {
        String normalizedServiceType = serviceType == null || serviceType.isBlank() ? null : normalizeServiceType(serviceType);
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return logMapper.selectRecentByUser(userId, normalizedServiceType, safeLimit).stream()
            .map(log -> new CloudUsageLogView(
                log.getId(),
                String.valueOf(log.getUserId()),
                log.getServiceType(),
                log.getDelta(),
                log.getBeforeRemaining(),
                log.getAfterRemaining(),
                log.getReason(),
                log.getOperatorType(),
                log.getOperatorId(),
                log.getCreatedAt().toString()
            ))
            .toList();
    }

    public ActiveEntitlementPageView activeEntitlements(Long userId, String serviceType, int page, int pageSize) {
        String normalizedServiceType = serviceType == null || serviceType.isBlank() ? null : normalizeServiceType(serviceType);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        OffsetDateTime current = now();
        List<ActiveEntitlementView> items = new java.util.ArrayList<>();
        if (normalizedServiceType == null || CLOUD_OCR.equals(normalizedServiceType)) {
            items.add(trialEntitlementView(userId, CLOUD_OCR, current));
        }
        if (normalizedServiceType == null || CLOUD_TTS.equals(normalizedServiceType)) {
            items.add(trialEntitlementView(userId, CLOUD_TTS, current));
        }
        if (creditGrantMapper != null) {
            items.addAll(creditGrantMapper.selectActiveByUser(APP_CODE, userId, normalizedServiceType, current).stream()
                .map(this::toActiveEntitlementView)
                .toList());
        }
        items = items.stream()
            .filter(item -> item.totalCount() > 0)
            .sorted((left, right) -> {
                int serviceCompare = left.serviceType().compareTo(right.serviceType());
                if (serviceCompare != 0) {
                    return serviceCompare;
                }
                int expiresCompare = left.expiresAt().compareTo(right.expiresAt());
                if (expiresCompare != 0) {
                    return expiresCompare;
                }
                return Integer.compare(grantTypePriority(left.grantType()), grantTypePriority(right.grantType()));
            })
            .toList();
        int from = Math.min((safePage - 1) * safePageSize, items.size());
        int to = Math.min(from + safePageSize, items.size());
        return new ActiveEntitlementPageView(safePage, safePageSize, items.size() > to, items.subList(from, to));
    }

    public List<ActiveEntitlementView> recentCreditEntitlements(Long userId, String serviceType, int days) {
        if (creditGrantMapper == null) {
            return List.of();
        }
        String normalizedServiceType = serviceType == null || serviceType.isBlank() ? null : normalizeServiceType(serviceType);
        int safeDays = Math.min(Math.max(days, 1), 365);
        OffsetDateTime current = now();
        return creditGrantMapper.selectRecentByUser(APP_CODE, userId, normalizedServiceType, current.minusDays(safeDays), current).stream()
            .map(this::toActiveEntitlementView)
            .filter(item -> item.totalCount() > 0)
            .toList();
    }

    public CreditGrantBalance activeGiftBalance(Long userId, String serviceType) {
        String normalizedServiceType = normalizeServiceType(serviceType);
        if (creditGrantMapper == null) {
            return new CreditGrantBalance(normalizedServiceType, 0, 0, 0);
        }
        OffsetDateTime current = now();
        int total = creditGrantMapper.sumActiveTotal(APP_CODE, userId, normalizedServiceType, "gift", current);
        int used = creditGrantMapper.sumActiveUsed(APP_CODE, userId, normalizedServiceType, "gift", current);
        return new CreditGrantBalance(normalizedServiceType, total, used, Math.max(total - used, 0));
    }

    public CreditGrantBalance activeCreditBalance(Long userId, String serviceType) {
        String normalizedServiceType = normalizeServiceType(serviceType);
        if (creditGrantMapper == null) {
            return new CreditGrantBalance(normalizedServiceType, 0, 0, 0);
        }
        OffsetDateTime current = now();
        int total = creditGrantMapper.sumActiveTotalAllTypes(APP_CODE, userId, normalizedServiceType, current);
        int used = creditGrantMapper.sumActiveUsedAllTypes(APP_CODE, userId, normalizedServiceType, current);
        return new CreditGrantBalance(normalizedServiceType, total, used, Math.max(total - used, 0));
    }

    public boolean consumeGiftCreditIfAvailable(Long userId, String serviceType) {
        return consumeCreditGrantIfAvailable(userId, normalizeServiceType(serviceType));
    }

    private int defaultTrialLimit(String serviceType) {
        String featureCode = switch (serviceType) {
            case CLOUD_TTS -> ReadingDailyQuotaConfigService.FEATURE_CLOUD_TTS;
            case CLOUD_OCR -> ReadingDailyQuotaConfigService.FEATURE_CLOUD_OCR;
            case LOCAL_SPEECH -> ReadingDailyQuotaConfigService.FEATURE_SPEECH;
            case LOCAL_CAPTURE -> ReadingDailyQuotaConfigService.FEATURE_CAPTURE;
            default -> ReadingDailyQuotaConfigService.FEATURE_CLOUD_OCR;
        };
        if (LOCAL_CAPTURE.equals(serviceType) || LOCAL_SPEECH.equals(serviceType)) {
            return 0;
        }
        return dailyQuotaConfigService.dailyLimit("free", featureCode);
    }

    private ReadingCloudServiceUsageEntity ensureUsageEntity(Long userId, String serviceType) {
        String normalizedServiceType = normalizeServiceType(serviceType);
        ReadingCloudServiceUsageEntity entity = usageMapper.selectByUserAndServiceType(userId, normalizedServiceType);
        if (entity != null) {
            resetTrialIfCrossedDay(entity);
            return entity;
        }
        ReadingCloudServiceUsageEntity created = new ReadingCloudServiceUsageEntity();
        created.setAppCode(APP_CODE);
        created.setUserId(userId);
        created.setServiceType(normalizedServiceType);
        created.setTrialLimit(defaultTrialLimit(normalizedServiceType));
        created.setTrialUsed(0);
        created.setPurchasedCredits(0);
        created.setPurchasedUsed(0);
        created.setCreatedAt(now());
        created.setUpdatedAt(now());
        usageMapper.insert(created);
        return created;
    }

    private int remaining(ReadingCloudServiceUsageEntity entity) {
        if (creditGrantMapper != null && entity.getUserId() != null && entity.getServiceType() != null) {
            OffsetDateTime current = now();
            int paidTotal = creditGrantMapper.sumActiveTotal(APP_CODE, entity.getUserId(), entity.getServiceType(), "paid", current);
            int paidUsed = creditGrantMapper.sumActiveUsed(APP_CODE, entity.getUserId(), entity.getServiceType(), "paid", current);
            int giftTotal = creditGrantMapper.sumActiveTotal(APP_CODE, entity.getUserId(), entity.getServiceType(), "gift", current);
            int giftUsed = creditGrantMapper.sumActiveUsed(APP_CODE, entity.getUserId(), entity.getServiceType(), "gift", current);
            return Math.max(value(entity.getTrialLimit()) - value(entity.getTrialUsed()), 0)
                + Math.max(paidTotal - paidUsed, 0)
                + Math.max(giftTotal - giftUsed, 0);
        }
        return Math.max(value(entity.getTrialLimit()) - value(entity.getTrialUsed()), 0)
            + Math.max(value(entity.getPurchasedCredits()) - value(entity.getPurchasedUsed()), 0);
    }

    private int purchasedRemaining(Long userId, String serviceType, ReadingCloudServiceUsageEntity entity) {
        if (creditGrantMapper == null) {
            return Math.max(value(entity.getPurchasedCredits()) - value(entity.getPurchasedUsed()), 0);
        }
        OffsetDateTime current = now();
        int paidTotal = creditGrantMapper.sumActiveTotal(APP_CODE, userId, serviceType, "paid", current);
        int paidUsed = creditGrantMapper.sumActiveUsed(APP_CODE, userId, serviceType, "paid", current);
        int giftTotal = creditGrantMapper.sumActiveTotal(APP_CODE, userId, serviceType, "gift", current);
        int giftUsed = creditGrantMapper.sumActiveUsed(APP_CODE, userId, serviceType, "gift", current);
        return Math.max(paidTotal - paidUsed, 0) + Math.max(giftTotal - giftUsed, 0);
    }

    private boolean consumeCreditGrantIfAvailable(Long userId, String serviceType) {
        if (creditGrantMapper == null) {
            return false;
        }
        List<ReadingCloudServiceCreditGrantEntity> grants = creditGrantMapper.selectActiveUsable(APP_CODE, userId, serviceType, now());
        if (grants.isEmpty()) {
            return false;
        }
        ReadingCloudServiceCreditGrantEntity grant = grants.stream()
            .sorted(creditGrantUsePriorityComparator())
            .findFirst()
            .orElse(null);
        if (grant == null) {
            return false;
        }
        grant.setUsedCount(value(grant.getUsedCount()) + 1);
        grant.setUpdatedAt(now());
        creditGrantMapper.updateById(grant);
        return true;
    }

    private void insertAdminGiftGrant(Long userId, String serviceType, int amount, String idempotencyKey, Integer validDays) {
        if (creditGrantMapper == null || amount <= 0) {
            return;
        }
        String sourceRef = blankToNull(idempotencyKey);
        if (sourceRef != null) {
            ReadingCloudServiceCreditGrantEntity existing = creditGrantMapper.selectBySourceRef(APP_CODE, userId, serviceType, "admin", sourceRef);
            if (existing != null) {
                return;
            }
        }
        int safeValidDays = Math.min(Math.max(validDays == null ? DEFAULT_GIFT_VALID_DAYS : validDays, 1), 3660);
        ReadingCloudServiceCreditGrantEntity grant = new ReadingCloudServiceCreditGrantEntity();
        grant.setAppCode(APP_CODE);
        grant.setUserId(userId);
        grant.setServiceType(serviceType);
        grant.setGrantType("gift");
        grant.setTotalCount(amount);
        grant.setUsedCount(0);
        grant.setSourceType("admin");
        grant.setSourceRef(sourceRef);
        grant.setProductCode(null);
        grant.setExpiresAt(now().plusDays(safeValidDays));
        grant.setCreatedAt(now());
        grant.setUpdatedAt(now());
        creditGrantMapper.insert(grant);
    }

    private int reduceActiveCreditGrants(Long userId, String serviceType, int amount) {
        if (creditGrantMapper == null || amount <= 0) {
            return 0;
        }
        int remainingDecrease = amount;
        int reduced = 0;
        List<ReadingCloudServiceCreditGrantEntity> grants = creditGrantMapper.selectActiveUsable(APP_CODE, userId, serviceType, now()).stream()
            .sorted(creditGrantUsePriorityComparator())
            .toList();
        for (ReadingCloudServiceCreditGrantEntity grant : grants) {
            if (remainingDecrease <= 0) {
                break;
            }
            int available = Math.max(value(grant.getTotalCount()) - value(grant.getUsedCount()), 0);
            if (available <= 0) {
                continue;
            }
            int decrease = Math.min(available, remainingDecrease);
            grant.setUsedCount(value(grant.getUsedCount()) + decrease);
            grant.setUpdatedAt(now());
            creditGrantMapper.updateById(grant);
            reduced += decrease;
            remainingDecrease -= decrease;
        }
        return reduced;
    }

    static Comparator<ReadingCloudServiceCreditGrantEntity> creditGrantUsePriorityComparator() {
        return Comparator
            .comparing(
                ReadingCloudServiceCreditGrantEntity::getExpiresAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .thenComparingInt(grant -> grantTypePriority(grant.getGrantType()))
            .thenComparing(
                ReadingCloudServiceCreditGrantEntity::getId,
                Comparator.nullsLast(Comparator.naturalOrder())
            );
    }

    private static int grantTypePriority(String grantType) {
        String normalized = grantType == null ? "" : grantType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "gift" -> 0;
            case "paid" -> 1;
            default -> 2;
        };
    }

    private void resetTrialIfCrossedDay(ReadingCloudServiceUsageEntity entity) {
        if (entity == null || entity.getUpdatedAt() == null) {
            return;
        }
        if (entity.getUpdatedAt().toLocalDate().isEqual(now().toLocalDate())) {
            return;
        }
        entity.setTrialLimit(defaultTrialLimit(entity.getServiceType()));
        entity.setTrialUsed(0);
        entity.setUpdatedAt(now());
        usageMapper.updateById(entity);
    }

    private CloudUsageDecision allowedDecision(String serviceType, int remaining) {
        return new CloudUsageDecision(true, serviceType, remaining, null, null, List.of("开通会员解锁更高次数", "购买独立云端次数包继续使用"));
    }

    private CloudUsageDecision exhaustedDecision(String serviceType) {
        return new CloudUsageDecision(false, serviceType, 0, exhaustedTitle(serviceType), exhaustedMessage(serviceType), List.of("开通会员解锁更高次数", "购买独立云端次数包继续使用"));
    }

    private CloudQuotaView toQuota(String serviceType, ReadingCloudServiceUsageEntity entity) {
        OffsetDateTime current = now();
        int paidTotal = creditGrantMapper == null ? value(entity.getPurchasedCredits()) : creditGrantMapper.sumActiveTotal(APP_CODE, entity.getUserId(), serviceType, "paid", current);
        int paidUsed = creditGrantMapper == null ? value(entity.getPurchasedUsed()) : creditGrantMapper.sumActiveUsed(APP_CODE, entity.getUserId(), serviceType, "paid", current);
        int giftTotal = creditGrantMapper == null ? 0 : creditGrantMapper.sumActiveTotal(APP_CODE, entity.getUserId(), serviceType, "gift", current);
        int giftUsed = creditGrantMapper == null ? 0 : creditGrantMapper.sumActiveUsed(APP_CODE, entity.getUserId(), serviceType, "gift", current);
        return new CloudQuotaView(
            serviceType,
            value(entity.getTrialLimit()),
            value(entity.getTrialUsed()),
            paidTotal + giftTotal,
            paidUsed + giftUsed,
            remaining(entity),
            entity.getUpdatedAt().toString()
        );
    }

    private ActiveEntitlementView trialEntitlementView(Long userId, String serviceType, OffsetDateTime current) {
        ReadingCloudServiceUsageEntity entity = ensureUsageEntity(userId, serviceType);
        OffsetDateTime dayStart = current.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime expiresAt = dayStart.plusDays(1).minusSeconds(1);
        int total = value(entity.getTrialLimit());
        int used = value(entity.getTrialUsed());
        return new ActiveEntitlementView(
            "trial-" + serviceType + "-" + current.toLocalDate(),
            serviceType,
            "daily_gift",
            "每日赠送",
            total,
            Math.min(used, total),
            Math.max(total - used, 0),
            dayStart.toString(),
            expiresAt.toString(),
            null
        );
    }

    private ActiveEntitlementView toActiveEntitlementView(ReadingCloudServiceCreditGrantEntity entity) {
        int total = value(entity.getTotalCount());
        int used = Math.min(value(entity.getUsedCount()), total);
        boolean expired = entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now());
        int remaining = expired ? 0 : Math.max(total - used, 0);
        String acquireMethod = switch (entity.getSourceType() == null ? "" : entity.getSourceType()) {
            case "internal_purchase" -> "内部购买";
            case "admin" -> "后台赠送";
            default -> "权益赠送";
        };
        return new ActiveEntitlementView(
            String.valueOf(entity.getId()),
            entity.getServiceType(),
            entity.getGrantType(),
            acquireMethod,
            total,
            used,
            remaining,
            entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString(),
            entity.getExpiresAt() == null ? "" : entity.getExpiresAt().toString(),
            entity.getProductCode()
        );
    }

    private void insertLog(
        ReadingCloudServiceUsageEntity entity,
        int delta,
        int beforeRemaining,
        int afterRemaining,
        int beforeTrialUsed,
        int beforePurchasedCredits,
        int beforePurchasedUsed,
        String reason,
        String operatorType,
        String operatorId,
        String idempotencyKey
    ) {
        ReadingCloudServiceUsageLogEntity log = new ReadingCloudServiceUsageLogEntity();
        log.setAppCode(APP_CODE);
        log.setUserId(entity.getUserId());
        log.setServiceType(entity.getServiceType());
        log.setDelta(delta);
        log.setBeforeRemaining(beforeRemaining);
        log.setAfterRemaining(afterRemaining);
        log.setBeforeTrialUsed(beforeTrialUsed);
        log.setAfterTrialUsed(value(entity.getTrialUsed()));
        log.setBeforePurchasedCredits(beforePurchasedCredits);
        log.setAfterPurchasedCredits(value(entity.getPurchasedCredits()));
        log.setBeforePurchasedUsed(beforePurchasedUsed);
        log.setAfterPurchasedUsed(value(entity.getPurchasedUsed()));
        log.setReason(reason);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setIdempotencyKey(idempotencyKey);
        log.setCreatedAt(now());
        logMapper.insert(log);
    }

    private String normalizeServiceType(String serviceType) {
        String normalized = serviceType == null ? "" : serviceType.trim().toLowerCase(Locale.ROOT);
        if (CLOUD_OCR.equals(normalized)) {
            return CLOUD_OCR;
        }
        if (CLOUD_TTS.equals(normalized)) {
            return CLOUD_TTS;
        }
        if ("capture".equals(normalized)
            || "ocr".equals(normalized)
            || "text_recognition".equals(normalized)
            || "image_ocr".equals(normalized)
            || "picture_ocr".equals(normalized)
            || "photo_ocr".equals(normalized)
            || "device_ocr".equals(normalized)
            || "local_ocr".equals(normalized)) {
            return LOCAL_CAPTURE;
        }
        if ("speech".equals(normalized)
            || "tts".equals(normalized)
            || "voice_reading".equals(normalized)
            || "text_to_speech".equals(normalized)
            || "speech_synthesis".equals(normalized)
            || "device_tts".equals(normalized)
            || "local_tts".equals(normalized)) {
            return LOCAL_SPEECH;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported serviceType");
    }

    private String normalizedReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        return normalized.isEmpty() ? "admin_adjustment" : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String exhaustedTitle(String serviceType) {
        return CLOUD_TTS.equals(serviceType) ? "云端朗读试用已用完" : "云端识图试用已用完";
    }

    private String exhaustedMessage(String serviceType) {
        return CLOUD_TTS.equals(serviceType)
            ? "当前账号的云端文本转语音试用次数已用完。你可以继续使用设备自带朗读，或者开通会员 / 购买云端朗读次数包。"
            : "当前账号的云端文字识别试用次数已用完。你可以继续使用设备自带识别，或者开通会员 / 购买云端识别次数包。";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public record CloudUsageDecision(
        boolean allowed,
        String serviceType,
        int remainingCount,
        String upgradeTitle,
        String upgradeMessage,
        List<String> unlockOptions
    ) {
    }

    public record CloudUsageSnapshot(CloudQuotaView ocr, CloudQuotaView tts) {
    }

    public record CreditGrantBalance(
        String serviceType,
        int totalCount,
        int usedCount,
        int remainingCount
    ) {
    }

    public record CloudQuotaView(
        String serviceType,
        int trialLimit,
        int trialUsed,
        int purchasedCredits,
        int purchasedUsed,
        int remainingCount,
        String updatedAt
    ) {
    }

    public record CloudUsageLogView(
        Long id,
        String userId,
        String serviceType,
        int delta,
        int beforeRemaining,
        int afterRemaining,
        String reason,
        String operatorType,
        String operatorId,
        String createdAt
    ) {
    }

    public record ActiveEntitlementPageView(
        int page,
        int pageSize,
        boolean hasMore,
        List<ActiveEntitlementView> records
    ) {
    }

    public record ActiveEntitlementView(
        String id,
        String serviceType,
        String grantType,
        String acquireMethod,
        int totalCount,
        int usedCount,
        int remainingCount,
        String acquiredAt,
        String expiresAt,
        String productCode
    ) {
    }
}
