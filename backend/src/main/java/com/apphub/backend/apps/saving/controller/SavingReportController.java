package com.apphub.backend.apps.saving.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 中文说明：saving V1 周报/月报由 iOS App 基于本地 CoreData 生成。
 * 后端不保存用户记账记录，也不保存报告快照；这些路由保留为未来云同步版本的契约占位，首发禁用。
 */
@RestController
@RequestMapping("/v1/reports")
@Tag(name = "省钱星球报告占位", description = "saving V1 local-only 周报/月报占位接口；正式首发返回 410 Gone。")
public class SavingReportController {
    @Operation(summary = "生成周报（首发禁用）", description = "saving V1 周报由 iOS 基于本机记录生成；后端报告聚合接口固定返回 410 Gone。请求体示例（未来）：weekStart=2026-04-27。")
    @PostMapping("/weekly")
    public void weekly() {
        throw disabled();
    }

    @Operation(summary = "生成月报（首发禁用）", description = "saving V1 月报由 iOS 基于本机记录生成；后端报告聚合接口固定返回 410 Gone。请求体示例（未来）：month=2026-04。")
    @PostMapping("/monthly")
    public void monthly() {
        throw disabled();
    }

    private ResponseStatusException disabled() {
        return new ResponseStatusException(
            HttpStatus.GONE,
            "saving V1 周报/月报由设备端基于本机记录生成；首发禁用后端报告聚合与快照，避免保存派生用户数据。"
        );
    }
}
