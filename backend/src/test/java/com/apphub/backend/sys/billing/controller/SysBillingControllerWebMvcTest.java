package com.apphub.backend.sys.billing.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.model.AuthenticatedSessionView;
import com.apphub.backend.sys.auth.model.CurrentUserView;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.billing.model.EntitlementItemView;
import com.apphub.backend.sys.billing.model.EntitlementOverviewView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshItemView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.model.PurchaseRestoreAcceptedView;
import com.apphub.backend.sys.billing.service.SysBillingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SysBillingController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(SysBillingController.class)
@Import({SessionTokenResolver.class, AppCompatControllerSupport.class})
class SysBillingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAuthSessionService sysAuthSessionService;

    @MockBean
    private SysBillingService sysBillingService;

    @Test
    void verifyShouldAcceptPurchaseIntake() throws Exception {
        mockAuthenticatedSession();
        when(sysBillingService.verify(eq("paipai_readingcompanion"), eq(101L), any()))
            .thenReturn(new PurchaseIntakeAcceptedView(11L, "verify", "family_yearly", "tx-1", "otx-1", "pending", "accepted"));

        mockMvc.perform(post("/api/v1/system/billing/apps/paipai_readingcompanion/purchases/verify")
                .header("Authorization", "Bearer token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productId": "family_yearly",
                      "transactionId": "tx-1",
                      "originalTransactionId": "otx-1",
                      "signedTransactionInfo": "signed-jws"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intakeId").value(11))
            .andExpect(jsonPath("$.data.sourceType").value("verify"))
            .andExpect(jsonPath("$.data.processingStatus").value("accepted"));
    }

    @Test
    void restoreShouldAcceptMultipleTransactions() throws Exception {
        mockAuthenticatedSession();
        when(sysBillingService.restore(eq("paipai_readingcompanion"), eq(101L), any()))
            .thenReturn(new PurchaseRestoreAcceptedView(
                "paipai_readingcompanion",
                101L,
                2,
                List.of(
                    new PurchaseIntakeAcceptedView(21L, "restore", "family_yearly", "tx-1", "otx-1", "pending", "accepted"),
                    new PurchaseIntakeAcceptedView(22L, "restore", "family_yearly", "tx-2", "otx-2", "pending", "accepted")
                )
            ));

        mockMvc.perform(post("/api/v1/system/billing/apps/paipai_readingcompanion/purchases/restore")
                .header("X-Session-Token", "token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "transactions": [
                        {
                          "productId": "family_yearly",
                          "transactionId": "tx-1",
                          "originalTransactionId": "otx-1",
                          "signedTransactionInfo": "signed-1"
                        },
                        {
                          "productId": "family_yearly",
                          "transactionId": "tx-2",
                          "originalTransactionId": "otx-2",
                          "signedTransactionInfo": "signed-2"
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.acceptedCount").value(2));
    }

    @Test
    void entitlementsShouldReturnOverview() throws Exception {
        mockAuthenticatedSession();
        when(sysBillingService.getEntitlements("paipai_readingcompanion", 101L))
            .thenReturn(new EntitlementOverviewView(
                "paipai_readingcompanion",
                101L,
                1,
                List.of(new EntitlementItemView("family_access", "active", "subscription", OffsetDateTime.parse("2026-05-01T00:00:00Z")))
            ));

        mockMvc.perform(get("/api/v1/system/billing/apps/paipai_readingcompanion/entitlements")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pendingTransactionCount").value(1))
            .andExpect(jsonPath("$.data.entitlements[0].entitlementCode").value("family_access"));
    }

    @Test
    void refreshEntitlementsShouldReturnRefreshResult() throws Exception {
        mockAuthenticatedSession();
        when(sysBillingService.refreshEntitlements("paipai_readingcompanion", 101L))
            .thenReturn(new EntitlementRefreshResultView(
                "paipai_readingcompanion",
                101L,
                2,
                1,
                2,
                List.of(
                    new EntitlementRefreshItemView("otx-1", "family_yearly", "verified", true, "ok"),
                    new EntitlementRefreshItemView("otx-2", "family_monthly", "failed_remote_lookup", false, "timeout")
                )
            ));

        mockMvc.perform(post("/api/v1/system/billing/apps/paipai_readingcompanion/entitlements/refresh")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.candidateCount").value(2))
            .andExpect(jsonPath("$.data.refreshedCount").value(1))
            .andExpect(jsonPath("$.data.results[0].lookupStatus").value("verified"));
    }

    @Test
    void verifyShouldReturnUnauthorizedWhenSessionAppMismatch() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "saving",
                "demo",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "saving", "guest", "Guest", "active")
            )));

        mockMvc.perform(post("/api/v1/system/billing/apps/paipai_readingcompanion/purchases/verify")
                .header("Authorization", "Bearer token-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productId": "family_yearly",
                      "originalTransactionId": "otx-1",
                      "signedTransactionInfo": "signed-jws"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    private void mockAuthenticatedSession() {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(sysAuthSessionService.findCurrentSession("token-123"))
            .thenReturn(Optional.of(new AuthenticatedSessionView(
                "paipai_readingcompanion",
                "demo",
                "active",
                OffsetDateTime.parse("2026-05-16T00:00:00Z"),
                new CurrentUserView(101L, "paipai_readingcompanion", "guest", "Guest", "active")
            )));
    }

    private AppDefinition appDefinition() {
        return new AppDefinition(
            "paipai_readingcompanion",
            "拍拍伴读",
            "/api/v1",
            "reading_",
            new AppDefinition.Support(true, true, true),
            Map.of()
        );
    }
}
