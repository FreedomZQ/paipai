package com.apphub.backend.apps.saving.domain.service;

import com.apphub.backend.apps.saving.domain.entity.SavingExpenseRecordEntity;
import com.apphub.backend.apps.saving.domain.entity.SavingSavingRecordEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Saving 记账数据访问边界。
 *
 * 中文说明：业务层只依赖该可复用接口；当前实现基于 MyBatis-Plus ServiceImpl，
 * 未来拆成微服务时可替换为 RPC/HTTP 适配器而不改 Controller/业务编排逻辑。
 */
public interface SavingFinanceDataService extends IService<SavingExpenseRecordEntity> {
    void insertExpense(SavingExpenseRecordEntity entity);
    void insertSaving(SavingSavingRecordEntity entity);
    List<SavingExpenseRecordEntity> selectExpenses(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit);
    List<SavingSavingRecordEntity> selectSavings(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit);
    SavingExpenseRecordEntity selectExpenseById(Long userId, String id);
    SavingSavingRecordEntity selectSavingById(Long userId, String id);
    int updateExpense(SavingExpenseRecordEntity entity);
    int updateSaving(SavingSavingRecordEntity entity);
    int deleteExpense(Long userId, String id);
    int deleteSaving(Long userId, String id);
    int deleteAllExpensesByUser(Long userId);
    int deleteAllSavingsByUser(Long userId);
    BigDecimal sumExpenses(Long userId, OffsetDateTime startAt, OffsetDateTime endAt);
    BigDecimal sumSavingsByType(Long userId, String savingType, OffsetDateTime startAt, OffsetDateTime endAt);
    int countExpenses(Long userId, OffsetDateTime startAt, OffsetDateTime endAt);
    int countSavings(Long userId, OffsetDateTime startAt, OffsetDateTime endAt);
    List<Map<String, Object>> selectExpenseCategoryBreakdown(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit);
    List<Map<String, Object>> selectSavingCategoryBreakdown(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit);
    List<Map<String, Object>> selectTopSavingActions(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit);
    Map<String, Object> selectHighRiskExpenseHour(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, String timezone);
}
