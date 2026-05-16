package com.apphub.backend.sys.configcenter.controller;

import com.apphub.backend.sys.app.model.AppDefinition;
import com.apphub.backend.sys.app.service.AppDefinitionService;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 针对 `SysConfigCenterController` 的 WebMvc 测试。
 * 用于验证控制器路由、请求校验、响应结构以及鉴权或门禁行为是否符合预期。
 */

@WebMvcTest(SysConfigCenterController.class)
class SysConfigCenterControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppDefinitionService appDefinitionService;

    @MockBean
    private SysRemoteConfigService sysRemoteConfigService;

    @Test
    void namespaceShouldReturnRemoteConfig() throws Exception {
        when(appDefinitionService.get("paipai_readingcompanion"))
            .thenReturn(Optional.of(new AppDefinition(
                "paipai_readingcompanion",
                "拍拍伴读",
                "/api/v1",
                "reading_",
                new AppDefinition.Support(true, true, true),
                Map.of()
            )));
        when(sysRemoteConfigService.loadNamespace("paipai_readingcompanion", "bootstrap"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "bootstrap", Map.of(
                "default_locale", "zh-Hans",
                "supported_locales", java.util.List.of("zh-Hans", "en")
            )));

        mockMvc.perform(get("/api/v1/system/config/paipai_readingcompanion/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.appCode").value("paipai_readingcompanion"))
            .andExpect(jsonPath("$.data.namespaceCode").value("bootstrap"))
            .andExpect(jsonPath("$.data.items.default_locale").value("zh-Hans"));
    }
}
