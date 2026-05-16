package com.apphub.backend.apps.reading.compat.controller;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.domain.entity.ReadingResourcePackCatalogEntity;
import com.apphub.backend.apps.reading.domain.mapper.ReadingResourcePackCatalogMapper;
import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Tag(name = "拍拍伴读资源包管理", description = "资源包启用和禁用管理接口。")
@RestController
@RequestMapping("/api/v1/admin/reading/resource-packs")
public class ReadingResourcePackAdminController {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final ReadingResourcePackCatalogMapper resourcePackCatalogMapper;

    @Value("${backend.apps.paipai_readingcompanion.admin.configToken:}")
    private String adminConfigToken;

    public ReadingResourcePackAdminController(ReadingResourcePackCatalogMapper resourcePackCatalogMapper) {
        this.resourcePackCatalogMapper = resourcePackCatalogMapper;
    }

    @Operation(summary = "启用或禁用资源包", description = "更新资源包状态。status=active 表示可购买，status=inactive 表示前端不展示也不可购买。")
    @PatchMapping("/{packageCode}/status")
    public ApiResponse<ResourcePackStatusReceipt> updateStatus(
        @Parameter(description = "管理 token。") @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @PathVariable String packageCode,
        @Valid @RequestBody ResourcePackStatusUpdateRequest body,
        @Parameter(hidden = true) HttpServletRequest request
    ) {
        requireAdminToken(token);
        String status = normalizeStatus(body.status());
        ReadingResourcePackCatalogEntity entity = resourcePackCatalogMapper.selectByPackageCode(APP_CODE, packageCode);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_PACK_NOT_FOUND");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        resourcePackCatalogMapper.updateStatus(APP_CODE, packageCode, status);
        return ApiResponse.success(currentRequestId(), new ResourcePackStatusReceipt(
            entity.getPackageCode(),
            status,
            "active".equals(status),
            now.toString()
        ));
    }

    private String normalizeStatus(String raw) {
        String status = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "active", "enabled", "enable", "on" -> "active";
            case "inactive", "disabled", "disable", "off" -> "inactive";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RESOURCE_PACK_STATUS_INVALID");
        };
    }

    private void requireAdminToken(String token) {
        if (adminConfigToken == null || adminConfigToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_NOT_CONFIGURED");
        }
        if (token == null || !adminConfigToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_INVALID");
        }
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null ? "unknown" : requestId;
    }

    public record ResourcePackStatusUpdateRequest(@NotBlank String status) {}

    public record ResourcePackStatusReceipt(
        String packageCode,
        String status,
        Boolean enabled,
        String updatedAt
    ) {}
}
