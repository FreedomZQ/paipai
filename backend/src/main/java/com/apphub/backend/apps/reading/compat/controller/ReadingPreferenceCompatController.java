package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingPreferenceService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
@Tag(name = "拍拍伴读偏好", description = "当前用户语言、学习轨道、语音和云同步偏好接口。")
public class ReadingPreferenceCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingPreferenceService preferenceService;

    public ReadingPreferenceCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingPreferenceService preferenceService) {
        this.userResolver = userResolver;
        this.preferenceService = preferenceService;
    }

    @Operation(summary = "查询我的偏好", description = "查询当前登录用户的语言、学习轨道、语音和同步偏好。")
    @GetMapping("/me")
    public ApiResponse<ReadingPreferenceService.PreferenceView> me(@Parameter(hidden = true) HttpServletRequest request) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), preferenceService.get(user));
    }

    @Operation(summary = "更新我的偏好", description = "局部更新当前登录用户的偏好；未传字段保持不变。")
    @PatchMapping("/me")
    public ApiResponse<ReadingPreferenceService.PreferenceView> patch(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "偏好更新请求体。示例：uiLocale=zh-Hans，readingTrackCode=zh_to_en。", required = true) @RequestBody ReadingPreferenceService.PreferencePatchRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), preferenceService.update(user, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
