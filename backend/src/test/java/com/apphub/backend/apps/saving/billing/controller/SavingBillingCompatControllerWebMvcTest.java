package com.apphub.backend.apps.saving.billing.controller;

import com.apphub.backend.apps.saving.service.SavingConfigService;
import com.apphub.backend.apps.saving.service.SavingRequestSupport;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshItemView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.service.SysBillingService;
import com.apphub.backend.sys.entitlement.service.SysEntitlementCenterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SavingBillingCompatController` 的 WebMvc 测试。
 *
 * 中文说明：saving iOS 首发依赖旧版 `/v1` 兼容响应结构。这里重点验证控制器仍复用统一计费内核，
 * 同时向客户端返回 `verified/restored + entitlement` 这种低耦合 DTO，避免把统一后端内部 intake/transaction
 * 字段直接暴露给前端，后续接入更多 APP 时也能继续复用同一套计费服务。
 */
@WebMvcTest(SavingBillingCompatController.class)
@Import(SessionTokenResolver.class)
class SavingBillingCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SavingRequestSupport requestSupport;

    @MockBean
    private SavingConfigService configService;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAuthSessionService sysAuthSessionService;

    @MockBean
    private SysBillingService sysBillingService;

    @MockBean
    private SysEntitlementCenterService sysEntitlementCenterService;

    @Test
    void verifyShouldReuseUnifiedBillingServiceAndReturnFrontendDto() throws Exception {
        mockSavingRequest();
        when(appDefinitionService.get("saving")).thenReturn(Optional.of(appDefinition()));
        when(sysBillingService.verify(eq("saving"), eq(202L), any()))
            .thenReturn(new PurchaseIntakeAcceptedView(51L, "verify", "pro_yearly", "tx-9", "otx-9", "verified", "accepted"));
        when(sysBillingService.getEntitlements("saving", 202L)).thenReturn(proOverview());

        mockMvc.perform(post("/v1/purchases/verify")
                .header("Authorization", "Bearer token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "platform": "ios",
                      "productId": "pro_yearly",
                      "transactionId": "tx-9",
                      "originalTransactionId": "otx-9",
                      "signedTransactionInfo": "signed-jws"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verified").value(true))
            .andExpect(jsonPath("$.data.entitlement.userId").value("202"))
            .andExpect(jsonPath("$.data.entitlement.status").value("active"));
    }

    @Test
    void refreshShouldReuseUnifiedBillingServiceAndReturnEntitlementDto() throws Exception {
        mockSavingRequest();
        when(appDefinitionService.get("saving")).thenReturn(Optional.of(appDefinition()));
        when(sysBillingService.refreshEntitlements("saving", 202L))
            .thenReturn(new EntitlementRefreshResultView(
                "saving",
                202L,
                1,
                1,
                1,
                List.of(new EntitlementRefreshItemView("otx-9", "pro_yearly", "verified", true, "ok"))
            ));
        when(sysBillingService.getEntitlements("saving", 202L)).thenReturn(proOverview());

        mockMvc.perform(post("/v1/entitlements/refresh")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("202"))
            .andExpect(jsonPath("$.data.plan").value("pro_monthly"))
            .andExpect(jsonPath("$.data.status").value("active"));
    }

    private void mockSavingRequest() {
        when(requestSupport.requireUserId(any())).thenReturn(202L);
        when(requestSupport.requestId()).thenReturn("req-test");
        when(configService.namespace("saving_entitlement_limits")).thenReturn(Map.of(
            "free", Map.of("monthlyRecordLimit", 50),
            "pro_monthly", Map.of("monthlyRecordLimit", -1)
        ));
    }

    private EntitlementOverviewView proOverview() {
        return new EntitlementOverviewView(
            "saving",
            202L,
            0,
            List.of(new EntitlementItemView("premium_reports", "active", "purchase", OffsetDateTime.parse("2026-05-16T00:00:00Z")))
        );
    }

    private AppDefinition appDefinition() {
        return new AppDefinition(
            "saving",
            "省钱项目",
            "/v1",
            "saving_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
    }
}
