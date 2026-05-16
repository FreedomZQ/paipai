package com.apphub.backend.apps.fitmystery.account;

import com.apphub.backend.apps.fitmystery.api.FitMysteryApiEnvelope;
import com.apphub.backend.apps.fitmystery.common.FitMysteryRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fitmystery/account")
@Tag(name = "FitMystery 账号", description = "FitMystery 账号与数据删除接口。")
public class FitMysteryAccountController {
    private final FitMysteryAccountService accountService;
    private final FitMysteryRequestSupport requestSupport;

    public FitMysteryAccountController(FitMysteryAccountService accountService, FitMysteryRequestSupport requestSupport) {
        this.accountService = accountService;
        this.requestSupport = requestSupport;
    }

    @Operation(summary = "删除当前账号数据", description = "删除当前登录用户在 FitMystery 下的应用数据；用户身份从 Authorization 会话解析。")
    @DeleteMapping
    public FitMysteryApiEnvelope<Map<String, Object>> deleteAccount(@Parameter(hidden = true) HttpServletRequest request) {
        return FitMysteryApiEnvelope.ok(requestSupport.requestId(), accountService.deleteAppData(requestSupport.requireUserId(request)));
    }
}
