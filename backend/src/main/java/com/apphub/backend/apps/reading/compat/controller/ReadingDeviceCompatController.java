package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.apps.reading.compat.service.ReadingDeviceEventService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "拍拍伴读设备事件", description = "客户端设备、安装和版本事件上报接口。")
public class ReadingDeviceCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingDeviceEventService deviceEventService;

    public ReadingDeviceCompatController(ReadingAuthenticatedUserResolver userResolver, ReadingDeviceEventService deviceEventService) {
        this.userResolver = userResolver;
        this.deviceEventService = deviceEventService;
    }

    @Operation(summary = "上报设备事件", description = "记录客户端登录成功后的设备、版本和环境事件；需要有效登录会话。")
    @PostMapping("/device-event")
    public ApiResponse<ReadingDeviceEventService.DeviceEventReceipt> event(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "设备事件请求体。示例：eventType=apple_login_succeeded，clientPlatform=ios，appVersion=1.0.0。", required = true) @RequestBody ReadingDeviceEventService.DeviceEventRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        ReadingAuthenticatedUser user = userResolver.require(request);
        return ApiResponse.success(currentRequestId(), deviceEventService.record(user, body));
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
