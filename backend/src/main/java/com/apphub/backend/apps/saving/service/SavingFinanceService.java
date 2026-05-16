package com.apphub.backend.apps.saving.service;

import com.apphub.backend.apps.saving.domain.entity.SavingExpenseRecordEntity;
import com.apphub.backend.apps.saving.domain.entity.SavingSavingRecordEntity;
import com.apphub.backend.apps.saving.domain.service.SavingFinanceDataService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class SavingFinanceService {
    private final SavingFinanceDataService mapper;
    private final SavingConfigService configService;

    public SavingFinanceService(SavingFinanceDataService mapper, SavingConfigService configService) {
        this.mapper = mapper;
        this.configService = configService;
    }

    @Transactional
    public Map<String, Object> createExpense(Long userId, ExpenseUpsertRequest request) {
        OffsetDateTime now = now();
        SavingExpenseRecordEntity entity = new SavingExpenseRecordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        applyExpense(entity, request, now);
        entity.setSource("ios");
        entity.setCreatedAt(now);
        mapper.insertExpense(entity);
        return createResponse(entity.getId(), "expense", entity.getOccurredAt());
    }

    @Transactional
    public Map<String, Object> createSaving(Long userId, SavingUpsertRequest request) {
        OffsetDateTime now = now();
        SavingSavingRecordEntity entity = new SavingSavingRecordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        applySaving(entity, request, now);
        entity.setSource("ios");
        entity.setCreatedAt(now);
        mapper.insertSaving(entity);
        return createResponse(entity.getId(), "saving", entity.getOccurredAt());
    }

    @Transactional
    public Map<String, Object> updateExpense(Long userId, String recordId, ExpenseUpsertRequest request) {
        SavingExpenseRecordEntity entity = mapper.selectExpenseById(userId, recordId);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense record not found");
        applyExpense(entity, request, now());
        mapper.updateExpense(entity);
        return expenseItem(entity);
    }

    @Transactional
    public Map<String, Object> updateSaving(Long userId, String recordId, SavingUpsertRequest request) {
        SavingSavingRecordEntity entity = mapper.selectSavingById(userId, recordId);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saving record not found");
        applySaving(entity, request, now());
        mapper.updateSaving(entity);
        return savingItem(entity);
    }

    @Transactional
    public Map<String, Object> delete(Long userId, String recordType, String recordId) {
        String type = normalizeRecordType(recordType);
        int rows = "expense".equals(type) ? mapper.deleteExpense(userId, recordId) : mapper.deleteSaving(userId, recordId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", recordId);
        data.put("recordType", type);
        data.put("deleted", rows > 0);
        data.put("serverTime", now().toString());
        return data;
    }

    public Map<String, Object> list(Long userId, String recordType, int pageSize, OffsetDateTime startAt, OffsetDateTime endAt) {
        String type = normalizeFilter(recordType);
        int limit = Math.max(1, Math.min(pageSize <= 0 ? 50 : pageSize, 100));
        List<Map<String, Object>> items = new ArrayList<>();
        if (!"saving".equals(type)) {
            for (SavingExpenseRecordEntity expense : mapper.selectExpenses(userId, startAt, endAt, limit + 1)) {
                items.add(expenseItem(expense));
            }
        }
        if (!"expense".equals(type)) {
            for (SavingSavingRecordEntity saving : mapper.selectSavings(userId, startAt, endAt, limit + 1)) {
                items.add(savingItem(saving));
            }
        }
        items.sort((a, b) -> String.valueOf(b.get("occurredAt")).compareTo(String.valueOf(a.get("occurredAt"))));
        boolean hasMore = items.size() > limit;
        if (hasMore) items = new ArrayList<>(items.subList(0, limit));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordTypeFilter", type);
        data.put("pageSize", limit);
        data.put("empty", items.isEmpty());
        data.put("hasMore", hasMore);
        data.put("items", items);
        return data;
    }

    public Map<String, Object> dashboard(Long userId, String locale, String timezone, int recentLimit) {
        ZoneId zone = safeZone(timezone);
        OffsetDateTime now = OffsetDateTime.now(zone);
        Window today = dayWindow(now);
        Window week = weekWindow(now);
        Window month = monthWindow(now);
        List<Map<String, Object>> recent = extractItems(list(userId, "all", Math.max(1, Math.min(recentLimit <= 0 ? 5 : recentLimit, 20)), null, null).get("items"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("locale", locale == null || locale.isBlank() ? "zh-Hans" : locale);
        data.put("timezone", zone.getId());
        data.put("empty", recent.isEmpty());
        data.put("todayRecorded", !recent.isEmpty() && recent.stream().anyMatch(item -> String.valueOf(item.get("occurredAt")).compareTo(today.start().toString()) >= 0));
        data.put("streakDays", recent.isEmpty() ? 0 : 1);
        data.put("greeting", Map.of(
            "title", String.valueOf(configService.copy(recent.isEmpty() ? "dashboard.empty.title" : "dashboard.default.title", recent.isEmpty() ? "开始记录第一笔" : "今天也在变会省")),
            "subtitle", String.valueOf(configService.copy(recent.isEmpty() ? "dashboard.empty.subtitle" : "dashboard.default.subtitle", recent.isEmpty() ? "记录花费和省下的钱，仪表盘会自动生成。" : "继续记录，周报和月报会更准确。"))
        ));
        data.put("todayOverview", overview(String.valueOf(configService.copy("dashboard.period.today", "今日")), userId, today));
        data.put("weekOverview", overview(String.valueOf(configService.copy("dashboard.period.week", "本周")), userId, week));
        data.put("monthOverview", overview(String.valueOf(configService.copy("dashboard.period.month", "本月")), userId, month));
        data.put("recentRecords", recent);
        return data;
    }

    public Map<String, Object> report(Long userId, String reportType, ReportRequest request) {
        return report(userId, reportType, request, "free");
    }

    public Map<String, Object> report(Long userId, String reportType, ReportRequest request, String planCode) {
        ZoneId zone = safeZone(request == null ? null : request.timezone());
        String effectivePlan = normalize(planCode, "free");
        boolean advancedUnlocked = !"free".equalsIgnoreCase(effectivePlan);
        OffsetDateTime anchor = OffsetDateTime.now(zone);
        Window current = "monthly".equals(reportType) ? monthWindow(anchor) : weekWindow(anchor);
        Window previous = new Window(current.start().minus(Duration.between(current.start(), current.end())), current.start());
        Map<String, Object> overview = overview("", userId, current);
        Map<String, Object> previousOverview = overview("", userId, previous);
        List<Map<String, Object>> expenseByCategory = categoryBreakdown(mapper.selectExpenseCategoryBreakdown(userId, current.start(), current.end(), 8));
        List<Map<String, Object>> savingByCategory = categoryBreakdown(mapper.selectSavingCategoryBreakdown(userId, current.start(), current.end(), 8));
        List<Map<String, Object>> previousExpenseByCategory = categoryBreakdown(mapper.selectExpenseCategoryBreakdown(userId, previous.start(), previous.end(), 20));
        List<Map<String, Object>> topSavingActions = savingActions(mapper.selectTopSavingActions(userId, current.start(), current.end(), 5));
        Map<String, Object> highRiskHour = highRiskWindow(mapper.selectHighRiskExpenseHour(userId, current.start(), current.end(), zone.getId()));
        BigDecimal expense = bd(overview.get("expenseAmount"));
        BigDecimal saved = bd(overview.get("totalSavedAmount"));
        BigDecimal previousExpense = bd(previousOverview.get("expenseAmount"));
        BigDecimal previousSaved = bd(previousOverview.get("totalSavedAmount"));
        boolean hasRecords = ((Number) overview.get("expenseCount")).intValue() + ((Number) overview.get("savingCount")).intValue() > 0;
        String typeText = "monthly".equals(reportType) ? "月" : "周";
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("generationMode", "database_aggregation");
        content.put("locale", request == null || request.locale() == null ? "zh-Hans" : request.locale());
        content.put("timezone", zone.getId());
        content.put("title", String.valueOf(configService.copy("report." + reportType + ".title", typeText + "度省钱报告")));
        content.put("planCode", effectivePlan);
        content.put("access", reportAccess(effectivePlan, advancedUnlocked));
        content.put("summary", hasRecords
            ? String.valueOf(configService.copy("report.summary.prefix", "本期已生成基于真实记录的报告。"))
            : String.valueOf(configService.copy("report.empty.summary", "本期还没有记录，先添加几笔花费和省钱记录。")));
        content.put("disclaimer", configService.copy("report.disclaimer", "报告仅用于个人记账复盘，不构成财务建议。"));
        content.put("dataReadiness", Map.of(
            "aggregationLevel", "user_records",
            "hasFinancialRecords", hasRecords,
            "currentPeriodExpenseCount", overview.get("expenseCount"),
            "currentPeriodSavingCount", overview.get("savingCount"),
            "missingSources", List.of(),
            "note", hasRecords ? "live" : "empty"
        ));
        content.put("overview", Map.of(
            "currency", "CNY",
            "expenseCount", overview.get("expenseCount"),
            "savingCount", overview.get("savingCount"),
            "totalExpenseAmount", expense,
            "confirmedSavingAmount", overview.get("confirmedSavingAmount"),
            "avoidedSavingAmount", overview.get("avoidedSavingAmount"),
            "totalSavedAmount", saved,
            "savingRate", overview.get("savingRate"),
            "netResultAmount", saved.subtract(expense)
        ));
        content.put("comparison", Map.of(
            "previousPeriodStart", previous.start().toString(),
            "previousPeriodEnd", previous.end().toString(),
            "previousExpenseAmount", previousExpense,
            "previousSavedAmount", previousSaved,
            "previousSavingRate", previousOverview.get("savingRate"),
            "expenseChangeAmount", expense.subtract(previousExpense),
            "expenseChangeRate", rate(expense.subtract(previousExpense), previousExpense),
            "savedChangeAmount", saved.subtract(previousSaved),
            "savedChangeRate", rate(saved.subtract(previousSaved), previousSaved)
        ));
        Map<String, Object> highlights = new LinkedHashMap<>();
        highlights.put("topExpenseCategory", advancedUnlocked && !expenseByCategory.isEmpty() ? expenseByCategory.get(0) : null);
        highlights.put("maxSavingAction", advancedUnlocked && !topSavingActions.isEmpty() ? topSavingActions.get(0) : null);
        highlights.put("topSavingActions", advancedUnlocked ? topSavingActions : List.of());
        highlights.put("highRiskTimeWindow", advancedUnlocked ? highRiskHour : null);
        content.put("highlights", highlights);
        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("expenseByCategory", advancedUnlocked ? expenseByCategory : List.of());
        breakdowns.put("savingByCategory", advancedUnlocked ? savingByCategory : List.of());
        breakdowns.put("categoryTrends", advancedUnlocked ? categoryTrends(expenseByCategory, previousExpenseByCategory) : List.of());
        content.put("breakdowns", breakdowns);
        List<Map<String, Object>> suggestions = new ArrayList<>();
        suggestions.add(Map.of(
            "code", hasRecords ? "keep_recording" : "start_recording",
            "title", String.valueOf(configService.copy(hasRecords ? "report.suggestion.keep.title" : "report.suggestion.start.title", hasRecords ? "保持记录" : "先完成第一批记录")),
            "description", String.valueOf(configService.copy(hasRecords ? "report.suggestion.keep.description" : "report.suggestion.start.description", hasRecords ? "持续记录能提升报告准确度。" : "添加 3 笔花费和 1 笔省钱记录后，趋势会更可信。"))
        ));
        if (!advancedUnlocked && hasRecords) {
            suggestions.add(Map.of(
                "code", "unlock_complete_report",
                "title", String.valueOf(configService.copy("report.suggestion.unlock.title", "解锁完整复盘")),
                "description", String.valueOf(configService.copy("report.suggestion.unlock.description", "Pro 可查看分类结构、趋势复盘、Top 省钱行为和 CSV 导出。"))
            ));
        }
        content.put("suggestions", suggestions);
        content.put("sections", List.of(Map.of("code", "core_metrics", "title", String.valueOf(configService.copy("report.section.coreMetrics.title", "核心指标")), "items", List.of(
            Map.of("key", "expenseAmount", "value", expense),
            Map.of("key", "savedAmount", "value", saved)
        ))));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportType", reportType);
        data.put("locale", content.get("locale"));
        data.put("periodStart", current.start().toString());
        data.put("periodEnd", current.end().toString());
        data.put("generatedAt", now().toString());
        data.put("cached", false);
        data.put("content", content);
        return data;
    }


    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                items.add((Map<String, Object>) map);
            }
        }
        return items;
    }

    private void applyExpense(SavingExpenseRecordEntity e, ExpenseUpsertRequest r, OffsetDateTime now) {
        e.setAmount(amount(r.amount())); e.setCurrency(normalize(r.currency(), "CNY")); e.setCategoryCode(required(r.categoryCode(), "categoryCode"));
        e.setCategoryName(emptyToNull(r.categoryName())); e.setMerchantName(emptyToNull(r.merchantName())); e.setNote(emptyToNull(r.note())); e.setOccurredAt(parseTime(r.occurredAt())); e.setUpdatedAt(now);
    }
    private void applySaving(SavingSavingRecordEntity e, SavingUpsertRequest r, OffsetDateTime now) {
        e.setAmount(amount(r.amount())); e.setCurrency(normalize(r.currency(), "CNY")); e.setSavingType(normalizeSavingType(r.savingType())); e.setCategoryCode(required(r.categoryCode(), "categoryCode"));
        e.setCategoryName(emptyToNull(r.categoryName())); e.setScenario(emptyToNull(r.scenario())); e.setNote(emptyToNull(r.note())); e.setOccurredAt(parseTime(r.occurredAt())); e.setUpdatedAt(now);
    }
    private Map<String, Object> createResponse(String id, String type, OffsetDateTime occurredAt) { return Map.of("recordId", id, "recordType", type, "occurredAt", occurredAt.toString(), "serverTime", now().toString()); }
    private Map<String, Object> expenseItem(SavingExpenseRecordEntity e) { Map<String,Object> m = baseItem(e.getId(), "expense", e.getAmount(), e.getCurrency(), e.getCategoryCode(), e.getCategoryName(), e.getNote(), e.getOccurredAt(), e.getCreatedAt(), e.getUpdatedAt()); m.put("title", first(e.getMerchantName(), e.getCategoryName(), e.getCategoryCode())); m.put("merchantName", e.getMerchantName()); m.put("savingType", null); m.put("scenario", null); return m; }
    private Map<String, Object> savingItem(SavingSavingRecordEntity e) { Map<String,Object> m = baseItem(e.getId(), "saving", e.getAmount(), e.getCurrency(), e.getCategoryCode(), e.getCategoryName(), e.getNote(), e.getOccurredAt(), e.getCreatedAt(), e.getUpdatedAt()); m.put("title", first(e.getScenario(), e.getCategoryName(), e.getCategoryCode())); m.put("merchantName", null); m.put("savingType", e.getSavingType()); m.put("scenario", e.getScenario()); return m; }
    private Map<String,Object> baseItem(String id, String type, BigDecimal amount, String currency, String code, String name, String note, OffsetDateTime occurredAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) { Map<String,Object> m = new LinkedHashMap<>(); m.put("recordId", id); m.put("recordType", type); m.put("amount", amount); m.put("currency", currency); m.put("categoryCode", code); m.put("categoryName", first(name, code)); m.put("note", note); m.put("occurredAt", occurredAt.toString()); m.put("createdAt", createdAt.toString()); m.put("updatedAt", updatedAt.toString()); return m; }
    private Map<String,Object> overview(String label, Long userId, Window window) { BigDecimal expense = nz(mapper.sumExpenses(userId, window.start(), window.end())); BigDecimal confirmed = nz(mapper.sumSavingsByType(userId, "confirmed", window.start(), window.end())); BigDecimal avoided = nz(mapper.sumSavingsByType(userId, "avoided", window.start(), window.end())); BigDecimal saved = confirmed.add(avoided); Map<String,Object> m = new LinkedHashMap<>(); m.put("label", label); m.put("expenseAmount", money(expense)); m.put("confirmedSavingAmount", money(confirmed)); m.put("avoidedSavingAmount", money(avoided)); m.put("totalSavedAmount", money(saved)); m.put("savingRate", rate(saved, expense.add(saved))); m.put("expenseCount", mapper.countExpenses(userId, window.start(), window.end())); m.put("savingCount", mapper.countSavings(userId, window.start(), window.end())); return m; }
    private Map<String, Object> reportAccess(String planCode, boolean advancedUnlocked) { Map<String, Object> access = new LinkedHashMap<>(); access.put("planCode", planCode); access.put("advancedUnlocked", advancedUnlocked); access.put("upgradeTrigger", "report_locked"); access.put("lockedModules", advancedUnlocked ? List.of() : List.of("category_breakdown", "trend_review", "top_actions", "high_risk_window", "csv_export")); access.put("disclaimer", "报告仅用于个人记账复盘，不构成财务、投资、税务或法律建议。"); return access; }
    private List<Map<String, Object>> categoryBreakdown(List<Map<String, Object>> rows) { List<Map<String, Object>> items = new ArrayList<>(); for (Map<String, Object> row : rows) { Map<String, Object> item = new LinkedHashMap<>(); item.put("categoryCode", str(row, "categoryCode", "category_code")); item.put("categoryName", first(str(row, "categoryName", "category_name"), str(row, "categoryCode", "category_code"))); item.put("amount", money(bd(row.get("amount")))); item.put("count", intValue(row.get("count"))); items.add(item); } return items; }
    private List<Map<String, Object>> savingActions(List<Map<String, Object>> rows) { List<Map<String, Object>> items = new ArrayList<>(); for (Map<String, Object> row : rows) { Map<String, Object> item = new LinkedHashMap<>(); item.put("title", first(str(row, "title"), str(row, "categoryName", "category_name"), str(row, "categoryCode", "category_code"))); item.put("savingType", first(str(row, "savingType", "saving_type"), "avoided")); item.put("categoryCode", str(row, "categoryCode", "category_code")); item.put("categoryName", first(str(row, "categoryName", "category_name"), str(row, "categoryCode", "category_code"))); item.put("amount", money(bd(row.get("amount")))); Object occurredAt = firstObject(row, "occurredAt", "occurred_at"); item.put("occurredAt", occurredAt == null ? now().toString() : occurredAt.toString()); items.add(item); } return items; }
    private List<Map<String, Object>> categoryTrends(List<Map<String, Object>> current, List<Map<String, Object>> previous) { Map<String, Map<String, Object>> previousByCode = new LinkedHashMap<>(); for (Map<String, Object> item : previous) previousByCode.put(String.valueOf(item.get("categoryCode")), item); List<Map<String, Object>> trends = new ArrayList<>(); for (Map<String, Object> item : current) { BigDecimal currentAmount = bd(item.get("amount")); Map<String, Object> prev = previousByCode.get(String.valueOf(item.get("categoryCode"))); BigDecimal previousAmount = prev == null ? BigDecimal.ZERO : bd(prev.get("amount")); Map<String, Object> trend = new LinkedHashMap<>(); trend.put("categoryCode", item.get("categoryCode")); trend.put("categoryName", item.get("categoryName")); trend.put("currentAmount", money(currentAmount)); trend.put("previousAmount", money(previousAmount)); trend.put("changeAmount", money(currentAmount.subtract(previousAmount))); trend.put("changeRate", rate(currentAmount.subtract(previousAmount), previousAmount)); trends.add(trend); } return trends; }
    private Map<String, Object> highRiskWindow(Map<String, Object> row) { if (row == null || row.isEmpty()) return null; int hour = intValue(firstObject(row, "hour")); Map<String, Object> item = new LinkedHashMap<>(); item.put("label", String.format(Locale.ROOT, "%02d:00-%02d:00", hour, (hour + 1) % 24)); item.put("amount", money(bd(row.get("amount")))); item.put("count", intValue(row.get("count"))); return item; }
    private Window dayWindow(OffsetDateTime t) { OffsetDateTime s = t.toLocalDate().atStartOfDay(t.getOffset()).toOffsetDateTime(); return new Window(s, s.plusDays(1)); }
    private Window weekWindow(OffsetDateTime t) { LocalDate d = t.toLocalDate().minusDays(Math.max(0, t.getDayOfWeek().getValue() - 1)); OffsetDateTime s = d.atStartOfDay(t.getOffset()).toOffsetDateTime(); return new Window(s, s.plusDays(7)); }
    private Window monthWindow(OffsetDateTime t) { OffsetDateTime s = t.toLocalDate().withDayOfMonth(1).atStartOfDay(t.getOffset()).toOffsetDateTime(); return new Window(s, s.plusMonths(1)); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private OffsetDateTime parseTime(String value) { try { return OffsetDateTime.parse(required(value, "occurredAt")); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "occurredAt must be ISO-8601 with timezone"); } }
    private ZoneId safeZone(String timezone) { try { return timezone == null || timezone.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(timezone); } catch (Exception e) { return ZoneId.of("Asia/Shanghai"); } }
    private BigDecimal amount(Double value) { if (value == null || value <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"); return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private BigDecimal money(BigDecimal v) { return nz(v).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal bd(Object v) { return v instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(v)); }
    private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) { return denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : numerator.divide(denominator, 4, RoundingMode.HALF_UP); }
    private String normalize(String v, String f) { return v == null || v.isBlank() ? f : v.trim(); }
    private String normalizeFilter(String v) { String n = normalize(v, "all").toLowerCase(Locale.ROOT); return Set.of("all", "expense", "saving").contains(n) ? n : "all"; }
    private String normalizeRecordType(String v) { String n = normalize(v, "").toLowerCase(Locale.ROOT); if (!Set.of("expense", "saving").contains(n)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordType must be expense or saving"); return n; }
    private String normalizeSavingType(String v) { return "confirmed".equalsIgnoreCase(v) ? "confirmed" : "avoided"; }
    private String required(String v, String field) { if (v == null || v.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required"); return v.trim(); }
    private String emptyToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private String first(String... values) { for (String v : values) if (v != null && !v.isBlank()) return v; return ""; }
    private Object firstObject(Map<String, Object> row, String... keys) { for (String key : keys) { Object value = row.get(key); if (value != null) return value; } return null; }
    private String str(Map<String, Object> row, String... keys) { Object value = firstObject(row, keys); return value == null ? "" : String.valueOf(value); }
    private int intValue(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }

    public record ExpenseUpsertRequest(Double amount, String currency, String categoryCode, String categoryName, String merchantName, String note, String occurredAt) {}
    public record SavingUpsertRequest(Double amount, String currency, String savingType, String categoryCode, String categoryName, String scenario, String note, String occurredAt) {}
    public record ReportRequest(String locale, String timezone) {}
    private record Window(OffsetDateTime start, OffsetDateTime end) {}
}
