package com.apphub.backend.sys.billing.privacy.model;

import java.util.List;

public record RefundTicketView(
    String appCode,
    Long userId,
    String lookupType,
    String lookupValue,
    List<TicketItem> tickets
) {
    public record TicketItem(
        Long transactionRecordId,
        String orderNo,
        String transactionId,
        String originalTransactionId,
        String productId,
        String productType,
        String purchaseAt,
        Long priceMilliAmount,
        String currency,
        String verificationStatus,
        String refundStatus,
        String revocationAt,
        Integer usedEntitlementCount,
        Integer totalEntitlementCount,
        Integer usageRatioMilli
    ) {
    }
}
