package com.apphub.backend.apps.saving.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 中文说明：saving V1 看板由 App 基于本地 CoreData 生成；后端不读取或聚合用户记账数据。
 * 该路由仅作为未来显式云同步版本的兼容占位，首发禁用。
 */
@RestController
@RequestMapping("/v1/dashboard")
@Tag(name = "省钱星球看板占位", description = "saving V1 local-only 看板占位接口；正式首发返回 410 Gone。")
public class SavingDashboardController {
    @Operation(summary = "查询看板概览（首发禁用）", description = "saving V1 看板由 iOS 基于本机记录生成；该后端聚合接口仅为未来云同步契约占位，首发固定返回 410 Gone。")
    @GetMapping("/overview")
    public void overview() {
        throw new ResponseStatusException(
            HttpStatus.GONE,
            "saving V1 看板由设备端基于本机记录生成；首发禁用后端聚合，避免服务端保存或读取用户记账明细。"
        );
    }
}
