package com.apphub.backend.apps.saving.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 中文说明：saving V1 报告由 App 本地生成，后端报告聚合入口首发必须禁用，避免存储/聚合用户使用数据。 */
@WebMvcTest(SavingReportController.class)
class SavingReportControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportAggregationEndpointsShouldBeDisabledForLocalOnlyV1() throws Exception {
        mockMvc.perform(post("/v1/reports/weekly")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"zh-Hans\",\"timezone\":\"Asia/Shanghai\"}"))
            .andExpect(status().isGone());

        mockMvc.perform(post("/v1/reports/monthly")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"zh-Hans\",\"timezone\":\"Asia/Shanghai\"}"))
            .andExpect(status().isGone());
    }
}
