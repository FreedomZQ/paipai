package com.apphub.backend.apps.saving.controller;

import com.apphub.backend.apps.common.AppVersionPolicyService;
import com.apphub.backend.apps.saving.service.SavingConfigService;
import com.apphub.backend.apps.saving.service.SavingRequestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 中文说明：这些接口承载 saving 首发前的 DB 化内容，包括记录分类、权益对比、功能开关、引导/空状态/留存文案和审核说明。
 * 测试目的不是校验具体文案，而是固定前后端契约，避免未来多 APP 共用后端时把 saving 配置硬编码回客户端。
 */
@WebMvcTest(SavingConfigController.class)
class SavingConfigControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SavingConfigService configService;

    @MockBean
    private SavingRequestSupport requestSupport;

    @MockBean
    private AppVersionPolicyService appVersionPolicyService;

    @Test
    void categoriesShouldReturnRemoteCatalogEnvelope() throws Exception {
        when(requestSupport.requestId()).thenReturn("req-config");
        when(configService.recordCategories(anyString())).thenReturn(Map.of(
            "version", 1,
            "expense", Map.of("items", List.of(Map.of("code", "food", "displayName", "餐饮", "enabled", true))),
            "saving", Map.of("items", List.of(Map.of("code", "subscription", "displayName", "订阅取消", "enabled", true)))
        ));

        mockMvc.perform(get("/v1/config/categories").param("locale", "zh-Hans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("req-config"))
            .andExpect(jsonPath("$.data.expense.items[0].code").value("food"))
            .andExpect(jsonPath("$.data.saving.items[0].code").value("subscription"));
    }

    @Test
    void entitlementMatrixShouldReturnComparisonSections() throws Exception {
        when(requestSupport.requestId()).thenReturn("req-config");
        when(configService.entitlementMatrix(anyString())).thenReturn(Map.of(
            "title", "免费版与 Pro 权益对比",
            "plans", List.of(Map.of("code", "free"), Map.of("code", "pro_monthly")),
            "sections", List.of(Map.of("code", "core", "items", List.of(Map.of("code", "monthly_records"))))
        ));

        mockMvc.perform(get("/v1/config/entitlement-matrix"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.plans[1].code").value("pro_monthly"))
            .andExpect(jsonPath("$.data.sections[0].items[0].code").value("monthly_records"));
    }

    @Test
    void launchCopyAndReviewNotesShouldBeConfigBacked() throws Exception {
        when(requestSupport.requestId()).thenReturn("req-config");
        when(configService.reportAccess()).thenReturn(Map.of("plans", Map.of("free", Map.of("modules", Map.of("trend_review", "locked")), "pro_monthly", Map.of("modules", Map.of("trend_review", "full")))));
        when(configService.featureFlags(anyString(), anyString())).thenReturn(Map.of("flags", Map.of("recordCategoriesRemoteEnabled", true)));
        when(configService.onboarding(anyString())).thenReturn(Map.of("emptyStates", Map.of("records", Map.of("all", Map.of("title", "还没有记录")))));
        when(configService.appReviewNotes()).thenReturn(Map.of("bundleId", "com.savingsplanet.app", "secretPolicy", "no secrets"));
        when(appVersionPolicyService.policy(anyString(), anyString(), anyString(), anyString())).thenReturn(Map.of(
            "currentVersion", "0.1.0",
            "latestVersion", "0.2.0",
            "updateAvailable", true,
            "appStoreUrl", "https://apps.apple.com/app/id1234567890"
        ));

        mockMvc.perform(get("/v1/config/report-access"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.plans.free.modules.trend_review").value("locked"))
            .andExpect(jsonPath("$.data.plans.pro_monthly.modules.trend_review").value("full"));
        mockMvc.perform(get("/v1/config/feature-flags"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.flags.recordCategoriesRemoteEnabled").value(true));
        mockMvc.perform(get("/v1/config/onboarding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.emptyStates.records.all.title").value("还没有记录"));
        mockMvc.perform(get("/v1/config/app-review-notes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bundleId").value("com.savingsplanet.app"));
        mockMvc.perform(get("/v1/config/app-version").param("platform", "ios").param("appVersion", "0.1.0").param("buildNumber", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.latestVersion").value("0.2.0"))
            .andExpect(jsonPath("$.data.updateAvailable").value(true))
            .andExpect(jsonPath("$.data.appStoreUrl").value("https://apps.apple.com/app/id1234567890"));
    }
}
