package com.apphub.backend.apps.fitmystery.purchase;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.apps.fitmystery.config.FitMysteryConfigService;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryPurchaseDataService;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import com.apphub.backend.sys.billing.service.SysBillingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FitMysteryPurchaseService {
    private final SysBillingService billingService;
    private final FitMysteryPurchaseDataService mapper;
    private final FitMysteryConfigService configService;

    public FitMysteryPurchaseService(SysBillingService billingService, FitMysteryPurchaseDataService mapper, FitMysteryConfigService configService) {
        this.billingService = billingService;
        this.mapper = mapper;
        this.configService = configService;
    }

    @Transactional
    public Map<String, Object> verifyAndGrant(Long userId, PurchaseVerifyRequest request) {
        PurchaseIntakeAcceptedView accepted = billingService.verify(FitMysteryAppModule.APP_CODE, userId, request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purchase", accepted);
        data.put("grant", grantConsumableIfVerified(userId, accepted));
        data.put("chanceBalance", mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId));
        return data;
    }

    public Map<String, Object> grantConsumableFromVerifiedNotification(Long userId, String productId, String transactionId, String originalTransactionId) {
        String sourceId = transactionId == null || transactionId.isBlank() ? originalTransactionId : transactionId;
        return grantConsumable(userId, productId, sourceId, "notification_consumable", true);
    }

    private Map<String, Object> grantConsumableIfVerified(Long userId, PurchaseIntakeAcceptedView accepted) {
        String productId = safe(accepted.productId());
        String transactionId = accepted.transactionId() == null || accepted.transactionId().isBlank()
            ? accepted.originalTransactionId()
            : accepted.transactionId();
        return grantConsumable(userId, productId, transactionId, "iap_consumable", "verified".equalsIgnoreCase(accepted.verificationStatus()));
    }

    private Map<String, Object> grantConsumable(Long userId, String productId, String transactionId, String sourceType, boolean verified) {
        productId = safe(productId);
        int grantChance = grantChanceForProduct(productId);
        if (grantChance <= 0) {
            return Map.of("status", "not_consumable", "productId", productId);
        }
        if (!verified) {
            return Map.of(
                "status", "pending_verification",
                "productId", productId,
                "note", "消耗型开盒次数只在 App Store 交易 verified 后发放；客户端不得本地加次数。"
            );
        }
        if (transactionId == null || transactionId.isBlank()) {
            return Map.of("status", "missing_transaction_id", "productId", productId, "grantChance", grantChance);
        }
        String idempotencyKey = sourceType + ":" + transactionId;
        if (mapper.countChanceLedgerByIdempotency(FitMysteryAppModule.APP_CODE, userId, idempotencyKey) > 0) {
            return Map.of("status", "already_granted", "productId", productId, "transactionId", transactionId, "grantChance", grantChance);
        }
        int balance = mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId) + grantChance;
        mapper.insertGrantChance(
            UUID.randomUUID().toString(),
            FitMysteryAppModule.APP_CODE,
            userId,
            grantChance,
            balance,
            sourceType,
            transactionId,
            idempotencyKey,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        return Map.of("status", "granted", "productId", productId, "transactionId", transactionId, "grantChance", grantChance, "balanceAfter", balance);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private int grantChanceForProduct(String productId) {
        Object productsRaw = configService.productPolicy().get("products");
        if (productsRaw instanceof Map<?, ?> products) {
            Object productRaw = ((Map<String, Object>) products).get(productId);
            if (productRaw instanceof Map<?, ?> product) {
                Object grant = product.get("grantChance");
                if (grant instanceof Number number) {
                    return number.intValue();
                }
            }
        }
        return switch (productId == null ? "" : productId) {
            case "com.fitmystery.box5" -> 5;
            case "com.fitmystery.box10" -> 10;
            case "com.fitmystery.box25" -> 25;
            default -> 0;
        };
    }
}
