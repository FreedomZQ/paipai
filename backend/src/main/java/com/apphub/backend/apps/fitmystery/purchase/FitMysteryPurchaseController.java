package com.apphub.backend.apps.fitmystery.purchase;

import com.apphub.backend.apps.fitmystery.api.FitMysteryApiEnvelope;
import com.apphub.backend.apps.fitmystery.common.FitMysteryRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.apphub.backend.sys.billing.model.PurchaseVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fitmystery/purchases")
@Tag(name = "FitMystery 购买", description = "FitMystery App Store 购买校验与权益发放接口。")
public class FitMysteryPurchaseController {
    private final FitMysteryPurchaseService purchaseService;
    private final FitMysteryRequestSupport requestSupport;

    public FitMysteryPurchaseController(FitMysteryPurchaseService purchaseService, FitMysteryRequestSupport requestSupport) {
        this.purchaseService = purchaseService;
        this.requestSupport = requestSupport;
    }

    @Operation(summary = "校验购买并发放权益", description = "校验 App Store 交易并为当前用户发放 FitMystery 权益。")
    @PostMapping("/verify")
    public FitMysteryApiEnvelope<Map<String, Object>> verify(@Parameter(hidden = true) HttpServletRequest request,
                                                             @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "购买校验请求体。示例：productId=com.fitmystery.box5，transactionId=2000000123456789。", required = true) @Valid @RequestBody PurchaseVerifyRequest body) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), purchaseService.verifyAndGrant(requestSupport.requireUserId(request), body));
    }
}
