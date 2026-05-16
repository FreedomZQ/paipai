package com.apphub.backend.apps.saving.controller;

import com.apphub.backend.apps.saving.api.SavingApiEnvelope;
import com.apphub.backend.apps.saving.service.SavingAccountDeletionService;
import com.apphub.backend.apps.saving.service.SavingRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * saving 账号接口。
 *
 * 中文说明：首发必须提供 App 内账号删除入口，满足 Apple 登录型应用的审核要求。
 * 该控制器只挂载 `/v1/account` 且强制校验 session.appCode=saving，不影响其他 APP。
 */
@RestController
@RequestMapping("/v1/account")
@Tag(name = "省钱星球账号", description = "saving 账号删除与账号生命周期接口。")
public class SavingAccountController {
    private final SavingRequestSupport requestSupport;
    private final SavingAccountDeletionService deletionService;

    public SavingAccountController(SavingRequestSupport requestSupport, SavingAccountDeletionService deletionService) {
        this.requestSupport = requestSupport;
        this.deletionService = deletionService;
    }

    @Operation(summary = "删除当前账号", description = "删除当前 saving 用户账号及本地同步相关服务端会话；用户身份从 Authorization 会话解析。")
    @DeleteMapping
    public SavingApiEnvelope<Map<String, Object>> deleteCurrentAccount(@Parameter(hidden = true) HttpServletRequest request) {
        Long userId = requestSupport.requireUserId(request);
        return SavingApiEnvelope.ok(requestSupport.requestId(), deletionService.deleteCurrentSavingAccount(userId));
    }
}
