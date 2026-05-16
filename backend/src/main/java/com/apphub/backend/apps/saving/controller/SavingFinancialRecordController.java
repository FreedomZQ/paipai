package com.apphub.backend.apps.saving.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 中文说明：saving V1 不进行用户记账数据云同步，后端不得存储用户使用数据。
 * 这些路由保留为未来云同步契约占位，但首发统一禁用，正式 App 使用 iOS 本地 CoreData。
 */
@RestController
@RequestMapping("/v1/records")
@Tag(name = "省钱星球记录占位", description = "saving V1 local-only 记账记录占位接口；正式首发均返回 410 Gone。")
public class SavingFinancialRecordController {
    @Operation(summary = "查询记录列表（首发禁用）", description = "saving V1 记录保存在设备本机；后端记录列表接口仅为未来云同步占位，首发固定返回 410 Gone。")
    @GetMapping
    public void list() {
        throw disabled();
    }

    @Operation(summary = "创建支出记录（首发禁用）", description = "首发不上传支出记录；该接口固定返回 410 Gone。请求体示例（未来）：amount=25.50，categoryCode=food。")
    @PostMapping("/expenses")
    public void createExpense() {
        throw disabled();
    }

    @Operation(summary = "创建存钱记录（首发禁用）", description = "首发不上传存钱记录；该接口固定返回 410 Gone。请求体示例（未来）：amount=100.00，goalId=goal-001。")
    @PostMapping("/savings")
    public void createSaving() {
        throw disabled();
    }

    @Operation(summary = "更新支出记录（首发禁用）", description = "首发不上传支出记录；该接口固定返回 410 Gone。")
    @PutMapping("/expenses/{recordId}")
    public void updateExpense(@Parameter(description = "记录 ID。示例：expense-001", example = "expense-001") @PathVariable String recordId) {
        throw disabled();
    }

    @Operation(summary = "更新存钱记录（首发禁用）", description = "首发不上传存钱记录；该接口固定返回 410 Gone。")
    @PutMapping("/savings/{recordId}")
    public void updateSaving(@Parameter(description = "记录 ID。示例：saving-001", example = "saving-001") @PathVariable String recordId) {
        throw disabled();
    }

    @Operation(summary = "删除记录（首发禁用）", description = "首发不上传/删除服务端记录；该接口固定返回 410 Gone。")
    @DeleteMapping("/{recordType}/{recordId}")
    public void delete(@Parameter(description = "记录类型。示例：expenses，可选 expenses/savings。", example = "expenses") @PathVariable String recordType,
                       @Parameter(description = "记录 ID。示例：expense-001", example = "expense-001") @PathVariable String recordId) {
        throw disabled();
    }

    private ResponseStatusException disabled() {
        return new ResponseStatusException(
            HttpStatus.GONE,
            "saving V1 记录默认保存在设备本机；首发禁用后端记账数据存储。未来如发布云同步，需用户明确开启并走新的同步契约。"
        );
    }
}
