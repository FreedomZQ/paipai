package com.apphub.backend.apps.reading.compensation.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.compensation.model.CompensationRedeemRequest;
import com.apphub.backend.sys.compensation.model.CompensationRedeemResultView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "拍拍伴读补偿码", description = "家长区域补偿码兑换接口。")
@RestController
@RequestMapping("/api/v1/account/compensation")
public class ReadingCompensationCompatController {
    private static final String APP_CODE = "paipai_readingcompanion";

    private final ReadingAuthenticatedUserResolver userResolver;
    private final SysCompensationService compensationService;
    private final ReadingCompatService readingCompatService;

    public ReadingCompensationCompatController(
        ReadingAuthenticatedUserResolver userResolver,
        SysCompensationService compensationService,
        ReadingCompatService readingCompatService
    ) {
        this.userResolver = userResolver;
        this.compensationService = compensationService;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "兑换补偿码", description = "家长输入补偿码后，后端校验有效性并补偿到当前账号。")
    @PostMapping("/redeem")
    public ApiResponse<CompensationRedeemResultView> redeem(
        @Valid @RequestBody CompensationRedeemRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        ReadingAuthenticatedUser user = userResolver.require(httpServletRequest);
        CompensationRedeemResultView redeemed = compensationService.redeem(APP_CODE, user.userId(), request.compensationCode());
        ReadingCompatService.AccountStateView accountState = readingCompatService.accountState(user);
        return ApiResponse.success(
            requestId(),
            new CompensationRedeemResultView(
                redeemed.compensationCode(),
                redeemed.status(),
                redeemed.benefitType(),
                redeemed.benefitSummary(),
                redeemed.planCode(),
                redeemed.entitlementCode(),
                redeemed.serviceType(),
                redeemed.grantCount(),
                redeemed.validUntil(),
                redeemed.redeemedAt(),
                redeemed.message(),
                accountState
            )
        );
    }

    private String requestId() {
        String value = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
