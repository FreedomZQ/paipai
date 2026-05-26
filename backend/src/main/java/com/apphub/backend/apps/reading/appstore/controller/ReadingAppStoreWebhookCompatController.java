package com.apphub.backend.apps.reading.appstore.controller;

import com.apphub.backend.apps.common.AppCompatControllerSupport;
import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationAcceptedView;
import com.apphub.backend.sys.appstore.model.AppStoreNotificationIngestRequest;
import com.apphub.backend.sys.appstore.service.SysAppStoreNotificationService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 拍拍伴读的 App Store 兼容控制器。
 * 用于保留旧版对外路由，并将请求适配到统一后端内核。
 */

@Tag(name = "拍拍伴读 App Store 回调", description = "拍拍伴读 App Store Server Notification 兼容接收接口。")
@RestController
@RequestMapping("/api/v1/webhooks/app-store")
public class ReadingAppStoreWebhookCompatController {

    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final AppCompatControllerSupport appCompatControllerSupport;
    private final SysAppStoreNotificationService sysAppStoreNotificationService;
    private final ReadingCompatService readingCompatService;

    public ReadingAppStoreWebhookCompatController(
        AppCompatControllerSupport appCompatControllerSupport,
        SysAppStoreNotificationService sysAppStoreNotificationService,
        ReadingCompatService readingCompatService
    ) {
        this.appCompatControllerSupport = appCompatControllerSupport;
        this.sysAppStoreNotificationService = sysAppStoreNotificationService;
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "接收 App Store 通知", description = "接收 App Store Server Notification，并交由统一通知服务验签和去重处理。")
    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AppStoreNotificationAcceptedView> notifications(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "接口请求体，字段中文说明和示例见 DTO Schema；未特别说明时由当前登录用户上下文补齐 userId。", required = true) @RequestBody Map<String, Object> payload) {
        appCompatControllerSupport.requireAppDefinition(APP_CODE);
        if (readingCompatService.isLocalOnlyLaunchMode()) {
            // 中文说明：无自有后端首发不接 App Store Server Notifications；
            // 消耗型本机积分只由 iOS StoreKit verified transaction 写入本机 Keychain。
            throw new LocalOnlyAppStoreWebhookDisabledException();
        }
        String signedPayload = payload.get("signedPayload") == null ? null : String.valueOf(payload.get("signedPayload"));
        if (signedPayload == null || signedPayload.isBlank()) {
            throw new InvalidPayloadException();
        }
        AppStoreNotificationIngestRequest request = new AppStoreNotificationIngestRequest(
            signedPayload,
            payload.get("notificationUuid") == null ? null : String.valueOf(payload.get("notificationUuid")),
            payload.get("notificationType") == null ? null : String.valueOf(payload.get("notificationType")),
            payload.get("subtype") == null ? null : String.valueOf(payload.get("subtype")),
            payload
        );
        return ApiResponse.success(currentRequestId(), sysAppStoreNotificationService.ingest(APP_CODE, request));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private static class InvalidPayloadException extends RuntimeException {
    }

    @ResponseStatus(HttpStatus.GONE)
    private static class LocalOnlyAppStoreWebhookDisabledException extends RuntimeException {
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
