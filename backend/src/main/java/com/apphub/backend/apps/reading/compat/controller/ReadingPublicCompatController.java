package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.apps.reading.compat.service.ReadingCompatService;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * 拍拍伴读公开配置兼容控制器。
 * 提供启动配置、商品方案和法务链接，避免前端使用本地占位值影响上架合规。
 */
@Tag(name = "拍拍伴读公开配置", description = "拍拍伴读启动配置、商品方案和法务文档接口。")
@RestController
@RequestMapping("/api/v1")
public class ReadingPublicCompatController {
    private final ReadingCompatService readingCompatService;

    public ReadingPublicCompatController(ReadingCompatService readingCompatService) {
        this.readingCompatService = readingCompatService;
    }

    @Operation(summary = "查询启动配置", description = "返回拍拍伴读客户端启动所需配置、语言和支持入口。")
    @GetMapping("/bootstrap/config")
    public ApiResponse<ReadingCompatService.BootstrapConfigView> bootstrap() {
        return ApiResponse.success(currentRequestId(), readingCompatService.bootstrap());
    }

    @Operation(summary = "查询商品方案", description = "返回拍拍伴读可展示的商品方案和权益摘要。")
    @GetMapping("/plans")
    public ApiResponse<List<ReadingCompatService.PlanView>> plans() {
        return ApiResponse.success(currentRequestId(), readingCompatService.plans());
    }

    @Operation(summary = "查询法务文档", description = "返回隐私政策、用户协议和儿童数据说明等文档链接。")
    @GetMapping("/legal/docs")
    public ApiResponse<List<ReadingCompatService.LegalDocView>> legalDocs() {
        List<ReadingCompatService.LegalDocView> docs = readingCompatService.legalDocs().stream()
            .map(item -> new ReadingCompatService.LegalDocView(item.type(), item.locale(), absolutize(item.url())))
            .toList();
        return ApiResponse.success(currentRequestId(), docs);
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    private String absolutize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        if (trimmed.startsWith("/")) {
            return base + trimmed;
        }
        return base + "/" + trimmed;
    }
}
