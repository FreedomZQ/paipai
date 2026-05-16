package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.announcement.service.ReadingAnnouncementService;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUserResolver;
import com.apphub.backend.sys.app.model.AppCodes;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "拍拍伴读公告", description = "拍拍伴读公告拉取接口，用于启动弹窗和本地历史公告缓存。")
@RestController
@RequestMapping("/api/v1/announcements")
public class ReadingAnnouncementCompatController {
    private final ReadingAuthenticatedUserResolver userResolver;
    private final ReadingAnnouncementService announcementService;

    public ReadingAnnouncementCompatController(
        ReadingAuthenticatedUserResolver userResolver,
        ReadingAnnouncementService announcementService
    ) {
        this.userResolver = userResolver;
        this.announcementService = announcementService;
    }

    @Operation(summary = "查询公告列表", description = "按 appCode 查询公告，并标记或过滤当前是否处于展示时间窗。")
    @GetMapping
    public ApiResponse<List<ReadingAnnouncementService.AnnouncementView>> list(
        @Parameter(description = "App 标识。示例：paipai_readingcompanion", example = "paipai_readingcompanion") @RequestParam(defaultValue = AppCodes.PAIPAI_READINGCOMPANION) String appCode,
        @Parameter(description = "查询最近多少天的公告，最大 30 天。示例：30", example = "30") @RequestParam(defaultValue = "30") int windowDays,
        @Parameter(description = "公告场景。示例：home_banner", example = "home_banner") @RequestParam(required = false) String scene,
        @Parameter(description = "本地化语言。示例：zh-Hans", example = "zh-Hans") @RequestParam(required = false) String locale,
        @Parameter(description = "客户端版本号。示例：1.0.0", example = "1.0.0") @RequestParam(required = false) String appVersion,
        @Parameter(description = "当前套餐编码。示例：free", example = "free") @RequestParam(required = false) String planCode,
        @Parameter(description = "是否只返回当前展示时间窗内公告。示例：true", example = "true") @RequestParam(defaultValue = "false") boolean activeOnly,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        userResolver.require(request);
        List<ReadingAnnouncementService.AnnouncementView> items =
            (scene == null && locale == null && appVersion == null && planCode == null && !activeOnly && AppCodes.PAIPAI_READINGCOMPANION.equals(appCode))
                ? announcementService.listRecent(windowDays)
                : announcementService.listRecent(appCode, windowDays, scene, locale, appVersion, planCode, activeOnly);
        return ApiResponse.success(currentRequestId(), items);
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }
}
