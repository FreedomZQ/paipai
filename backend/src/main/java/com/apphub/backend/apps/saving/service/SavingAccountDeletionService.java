package com.apphub.backend.apps.saving.service;

import com.apphub.backend.apps.saving.SavingAppModule;
import com.apphub.backend.apps.saving.domain.service.SavingFinanceDataService;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * saving 账号删除服务。
 *
 * 设计说明：
 * 1. 仅处理 app_code=saving 的用户，避免误删统一后端中其他 APP 的账号数据。
 * 2. 首发按低运维、低法律风险策略执行：删除 saving 业务记录、撤销身份和会话、软删除 sys_user。
 * 3. 购买交易和 App Store 通知属于财务/审计凭据，不在这里物理删除；它们按 app_code/user_id 保留最小必要记录。
 */
@Service
public class SavingAccountDeletionService {
    private final SavingFinanceDataService financeDataService;
    private final SysAuthDataService authDataService;

    public SavingAccountDeletionService(
        SavingFinanceDataService financeDataService,
        SysAuthDataService authDataService
    ) {
        this.financeDataService = financeDataService;
        this.authDataService = authDataService;
    }

    @Transactional
    public Map<String, Object> deleteCurrentSavingAccount(Long userId) {
        SysUserEntity user = authDataService.userById(userId);
        if (user == null || !SavingAppModule.APP_CODE.equals(user.getAppCode())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "saving account not found");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int expenseRows = financeDataService.deleteAllExpensesByUser(userId);
        int savingRows = financeDataService.deleteAllSavingsByUser(userId);
        int identityRows = authDataService.revokeIdentitiesByUser(SavingAppModule.APP_CODE, userId, now);
        int sessionRows = authDataService.revokeSessionsByUser(SavingAppModule.APP_CODE, userId, now);
        int providerTokenRows = authDataService.revokeProviderTokensByUser(SavingAppModule.APP_CODE, userId, now);

        user.setDisplayName("Deleted saving user");
        user.setStatus("deleted");
        user.setUpdatedAt(now);
        authDataService.updateUser(user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", true);
        data.put("deleted", true);
        data.put("message", "账号删除已完成：saving 业务记录已删除，登录态和第三方身份令牌已撤销；Apple 订阅仍需用户在 Apple 账户订阅页自行管理。");
        data.put("appCode", SavingAppModule.APP_CODE);
        data.put("userId", String.valueOf(userId));
        data.put("expenseRecordDeletedCount", expenseRows);
        data.put("savingRecordDeletedCount", savingRows);
        data.put("identityRevokedCount", identityRows);
        data.put("sessionRevokedCount", sessionRows);
        data.put("providerTokenRevokedCount", providerTokenRows);
        data.put("serverTime", now.toString());
        return data;
    }
}
