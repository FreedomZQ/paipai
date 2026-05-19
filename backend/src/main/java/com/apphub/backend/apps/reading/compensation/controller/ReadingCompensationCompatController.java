package com.apphub.backend.apps.reading.compensation.controller;

import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Tag(name = "拍拍伴读补偿码", description = "家长区域补偿码兑换接口。")
@RestController
@RequestMapping("/api/v1/account/compensation")
public class ReadingCompensationCompatController {
    private static final String APP_CODE = "paipai_readingcompanion";
    private static final String ANONYMOUS_PROVIDER = "anonymous_device";
    private static final String ANONYMOUS_ID_HEADER = "X-Paipai-Anonymous-Id";

    private final SysAuthDataService authDataService;
    private final SysCompensationService compensationService;
    private final ReadingCompatService readingCompatService;

    public ReadingCompensationCompatController(
        SysAuthDataService authDataService,
        SysCompensationService compensationService,
        ReadingCompatService readingCompatService
    ) {
        this.authDataService = authDataService;
        this.compensationService = compensationService;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "兑换补偿码", description = "家长输入补偿码后，后端校验有效性并补偿到当前设备权益；不要求登录。")
    @PostMapping("/redeem")
    public ApiResponse<CompensationRedeemResultView> redeem(
        @Valid @RequestBody CompensationRedeemRequest request,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        Long userId = anonymousUserId(httpServletRequest);
        CompensationRedeemResultView redeemed = compensationService.redeem(
            APP_CODE,
            userId,
            normalizeAnonymousId(httpServletRequest.getHeader(ANONYMOUS_ID_HEADER)),
            request.compensationCode()
        );
        ReadingCompatService.AccountStateView accountState = readingCompatService.accountState(userId, ANONYMOUS_PROVIDER);
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

    private Long anonymousUserId(HttpServletRequest request) {
        String anonymousId = normalizeAnonymousId(request.getHeader(ANONYMOUS_ID_HEADER));
        SysUserIdentityEntity existing = authDataService.identityByProvider(APP_CODE, ANONYMOUS_PROVIDER, anonymousId);
        if (existing != null) {
            return existing.getUserId();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SysUserEntity user = new SysUserEntity();
        user.setAppCode(APP_CODE);
        user.setUserType("anonymous");
        user.setDisplayName("Anonymous Device");
        user.setStatus("active");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        authDataService.saveUser(user);

        SysUserIdentityEntity identity = new SysUserIdentityEntity();
        identity.setAppCode(APP_CODE);
        identity.setUserId(user.getId());
        identity.setProviderCode(ANONYMOUS_PROVIDER);
        identity.setProviderSubject(anonymousId);
        identity.setStatus("active");
        identity.setPayloadJson("{}");
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        authDataService.saveIdentity(identity);
        return user.getId();
    }

    private String normalizeAnonymousId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "missing";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "");
        if (normalized.isBlank()) {
            return "missing";
        }
        return normalized.length() > 128 ? normalized.substring(0, 128) : normalized;
    }

    private String requestId() {
        String value = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
