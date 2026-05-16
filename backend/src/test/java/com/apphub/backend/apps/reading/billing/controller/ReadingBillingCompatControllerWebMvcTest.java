package com.apphub.backend.apps.reading.billing.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.service.SessionTokenResolver;
import com.apphub.backend.sys.auth.service.SysAuthSessionService;
import com.apphub.backend.sys.billing.model.EntitlementRefreshItemView;
import com.apphub.backend.sys.billing.model.EntitlementRefreshResultView;
import com.apphub.backend.sys.billing.model.PurchaseIntakeAcceptedView;
import com.apphub.backend.sys.billing.service.SysBillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `ReadingBillingCompatController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(ReadingBillingCompatController.class)
@Import(SessionTokenResolver.class)
class ReadingBillingCompatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysAuthSessionService sysAuthSessionService;

    @MockBean
    private SysBillingService sysBillingService;

    @MockBean
    private ReadingAuthenticatedUserResolver readingAuthenticatedUserResolver;

    @MockBean
    private ReadingCompatService readingCompatService;

    @Test
    void purchaseIntakeShouldReuseUnifiedBillingService() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(sysBillingService.verify(eq("paipai_readingcompanion"), eq(101L), any()))
            .thenReturn(new PurchaseIntakeAcceptedView(41L, "verify", "family_yearly", "tx-1", "otx-1", "pending", "accepted"));
        when(readingCompatService.intakeReceipt(any(), eq(41L), eq("verify"), eq("accepted"), eq("pending"), eq("family_yearly")))
            .thenReturn(new ReadingCompatService.IntakeReceipt(
                "41", "verify", "accepted", "pending", "family_yearly", "family_multi_child_lifetime", "free", true, "ok", sampleAccountState()
            ));

        mockMvc.perform(post("/api/v1/subscriptions/app-store/purchases/intake")
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
            .andExpect(jsonPath("$.data.intakeId").value("41"))
            .andExpect(jsonPath("$.data.status").value("accepted"));
    }

    @Test
    void entitlementRefreshShouldReuseUnifiedBillingService() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion")).thenReturn(Optional.of(appDefinition()));
        when(readingAuthenticatedUserResolver.require(any())).thenReturn(readingUser());
        when(readingCompatService.refreshEntitlement(any()))
            .thenReturn(new ReadingCompatService.EntitlementRefreshView(
                "2026-04-16T00:00:00Z", "family_multi_child_lifetime", "家庭多孩子终身版", 1, "backend_refresh"
            ));

        mockMvc.perform(post("/api/v1/billing/entitlement/refresh")
                .header("Authorization", "Bearer token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.effectivePlanCode").value("family_multi_child_lifetime"))
            .andExpect(jsonPath("$.data.activeProjectionCount").value(1));
    }

    private ReadingAuthenticatedUser readingUser() {
        SysAuthSessionEntity session = new SysAuthSessionEntity();
        session.setId(11L);
        session.setAppCode("paipai_readingcompanion");
        session.setUserId(101L);
        session.setSessionSource("demo");
        session.setStatus("active");
        session.setExpiresAt(OffsetDateTime.parse("2026-05-16T00:00:00Z"));
        SysUserEntity user = new SysUserEntity();
        user.setId(101L);
        user.setAppCode("paipai_readingcompanion");
        user.setUserType("guest");
        user.setDisplayName("Guest");
        user.setStatus("active");
        return new ReadingAuthenticatedUser(session, user, "token-123");
    }

    private ReadingCompatService.AccountStateView sampleAccountState() {
        return new ReadingCompatService.AccountStateView(
            "101",
            "demo",
            new ReadingCompatService.AccountEntitlementView("free", "免费版", "free", 3, 10, 1, 0, 1, 0, false, false, false, null, true, false, "single_child", "child", 0, false, false, false, "backend_sys_billing", Map.of()),
            new ReadingCompatService.DailyQuotaView("2026-04-16", 3, 0, 3, 10, 0, 10)
        );
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
