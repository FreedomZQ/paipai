package com.apphub.backend.apps.saving.domain.service.impl;

import com.apphub.backend.apps.saving.domain.entity.SavingExpenseRecordEntity;
import com.apphub.backend.apps.saving.domain.entity.SavingSavingRecordEntity;
import com.apphub.backend.apps.saving.domain.mapper.SavingFinanceMapper;
import com.apphub.backend.apps.saving.domain.service.SavingFinanceDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SavingFinanceDataServiceImpl extends ServiceImpl<SavingFinanceMapper, SavingExpenseRecordEntity> implements SavingFinanceDataService {
    @Override public void insertExpense(SavingExpenseRecordEntity entity) { baseMapper.insertExpense(entity); }
    @Override public void insertSaving(SavingSavingRecordEntity entity) { baseMapper.insertSaving(entity); }
    @Override public List<SavingExpenseRecordEntity> selectExpenses(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit) { return baseMapper.selectExpenses(userId, startAt, endAt, limit); }
    @Override public List<SavingSavingRecordEntity> selectSavings(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit) { return baseMapper.selectSavings(userId, startAt, endAt, limit); }
    @Override public SavingExpenseRecordEntity selectExpenseById(Long userId, String id) { return baseMapper.selectExpenseById(userId, id); }
    @Override public SavingSavingRecordEntity selectSavingById(Long userId, String id) { return baseMapper.selectSavingById(userId, id); }
    @Override public int updateExpense(SavingExpenseRecordEntity entity) { return baseMapper.updateExpense(entity); }
    @Override public int updateSaving(SavingSavingRecordEntity entity) { return baseMapper.updateSaving(entity); }
    @Override public int deleteExpense(Long userId, String id) { return baseMapper.deleteExpense(userId, id); }
    @Override public int deleteSaving(Long userId, String id) { return baseMapper.deleteSaving(userId, id); }
    @Override public int deleteAllExpensesByUser(Long userId) { return baseMapper.deleteAllExpensesByUser(userId); }
    @Override public int deleteAllSavingsByUser(Long userId) { return baseMapper.deleteAllSavingsByUser(userId); }
    @Override public BigDecimal sumExpenses(Long userId, OffsetDateTime startAt, OffsetDateTime endAt) { return baseMapper.sumExpenses(userId, startAt, endAt); }
    @Override public BigDecimal sumSavingsByType(Long userId, String savingType, OffsetDateTime startAt, OffsetDateTime endAt) { return baseMapper.sumSavingsByType(userId, savingType, startAt, endAt); }
    @Override public int countExpenses(Long userId, OffsetDateTime startAt, OffsetDateTime endAt) { return baseMapper.countExpenses(userId, startAt, endAt); }
    @Override public int countSavings(Long userId, OffsetDateTime startAt, OffsetDateTime endAt) { return baseMapper.countSavings(userId, startAt, endAt); }
    @Override public List<Map<String, Object>> selectExpenseCategoryBreakdown(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit) { return baseMapper.selectExpenseCategoryBreakdown(userId, startAt, endAt, limit); }
    @Override public List<Map<String, Object>> selectSavingCategoryBreakdown(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit) { return baseMapper.selectSavingCategoryBreakdown(userId, startAt, endAt, limit); }
    @Override public List<Map<String, Object>> selectTopSavingActions(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, int limit) { return baseMapper.selectTopSavingActions(userId, startAt, endAt, limit); }
    @Override public Map<String, Object> selectHighRiskExpenseHour(Long userId, OffsetDateTime startAt, OffsetDateTime endAt, String timezone) { return baseMapper.selectHighRiskExpenseHour(userId, startAt, endAt, timezone); }
}
