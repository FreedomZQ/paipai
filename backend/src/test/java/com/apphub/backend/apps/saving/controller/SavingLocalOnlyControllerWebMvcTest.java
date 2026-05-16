package com.apphub.backend.apps.saving.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 中文说明：saving V1 用户使用数据仅保存在 App 本地，后端 records/dashboard/report 写入或聚合入口必须禁用。 */
@WebMvcTest({SavingFinancialRecordController.class, SavingDashboardController.class, SavingReportController.class})
class SavingLocalOnlyControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void recordEndpointsShouldBeDisabledUntilExplicitCloudSync() throws Exception {
        mockMvc.perform(get("/v1/records"))
            .andExpect(status().isGone());
        mockMvc.perform(post("/v1/records/expenses").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isGone());
        mockMvc.perform(put("/v1/records/expenses/00000000-0000-0000-0000-000000000001").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isGone());
        mockMvc.perform(delete("/v1/records/expense/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isGone());
    }

    @Test
    void dashboardAndReportAggregationShouldBeDisabledUntilExplicitCloudSync() throws Exception {
        mockMvc.perform(get("/v1/dashboard/overview"))
            .andExpect(status().isGone());
        mockMvc.perform(post("/v1/reports/weekly").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isGone());
        mockMvc.perform(post("/v1/reports/monthly").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isGone());
    }
}
