package com.apphub.backend.apps.fitmystery.activity;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.apps.fitmystery.config.FitMysteryConfigService;
import com.apphub.backend.apps.fitmystery.domain.entity.FitActivityEventEntity;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryActivityDataService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class FitMysteryActivityService {
    private final FitMysteryActivityDataService mapper;
    private final FitMysteryConfigService configService;

    public FitMysteryActivityService(FitMysteryActivityDataService mapper, FitMysteryConfigService configService) {
        this.mapper = mapper;
        this.configService = configService;
    }

    @Transactional
    public Map<String, Object> batchSubmit(Long userId, ActivityBatchSubmitRequest request) {
        List<ActivityEventRequest> events = request == null || request.events() == null ? List.of() : request.events();
        if (events.isEmpty() || events.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "events size must be 1..50");
        }
        int accepted = 0;
        int rejected = 0;
        int pointsEarnedDelta = 0;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (ActivityEventRequest item : events) {
            String idempotencyKey = required(item.idempotencyKey(), "idempotencyKey");
            if (mapper.countEventByIdempotencyKey(FitMysteryAppModule.APP_CODE, userId, idempotencyKey) > 0) {
                continue;
            }
            OffsetDateTime occurredAt = parseTime(item.occurredAt());
            LocalDate eventDate = occurredAt.toLocalDate();
            BigDecimal quantity = item.quantity() == null ? BigDecimal.ZERO : item.quantity();
            Validation validation = validate(item.eventType(), item.source(), quantity);
            OffsetDateTime now = now();
            String eventId = UUID.randomUUID().toString();
            FitActivityEventEntity entity = new FitActivityEventEntity();
            entity.setId(eventId);
            entity.setAppCode(FitMysteryAppModule.APP_CODE);
            entity.setUserId(userId);
            entity.setIdempotencyKey(idempotencyKey);
            entity.setEventType(normalize(item.eventType(), ""));
            entity.setSource(normalize(item.source(), "manual"));
            entity.setQuantity(quantity);
            entity.setUnit(normalize(item.unit(), defaultUnit(item.eventType())));
            entity.setEventDate(eventDate);
            entity.setOccurredAt(occurredAt);
            entity.setClientRecordedAt(parseOptionalTime(item.clientRecordedAt()));
            entity.setTrustLevel("healthkit".equals(entity.getSource()) ? "high" : "normal");
            entity.setRawPayloadJson(null);
            entity.setStatus(validation.accepted() ? "accepted" : "rejected");
            entity.setRejectReason(validation.reason());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.insertEvent(entity);
            if (!validation.accepted()) {
                rejected++;
                continue;
            }
            int points = calculatePoints(entity.getEventType(), quantity);
            int balance = mapper.currentPointsBalance(FitMysteryAppModule.APP_CODE, userId) + points;
            mapper.insertPointsLedger(UUID.randomUUID().toString(), FitMysteryAppModule.APP_CODE, userId, "earn", points, balance, eventId, "points:" + idempotencyKey, "activity_" + entity.getEventType(), "server_calculated", now);
            mapper.upsertDailySnapshot(FitMysteryAppModule.APP_CODE, userId, eventDate, waterMl(entity), steps(entity), exerciseMinutes(entity), points, balance, mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId), now);
            if (eventDate.equals(today)) {
                pointsEarnedDelta += points;
            }
            accepted++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", accepted);
        data.put("rejected", rejected);
        data.put("pointsEarnedDelta", pointsEarnedDelta);
        data.put("today", today(userId, today));
        data.put("pointsPolicyVersion", String.valueOf(configService.pointsPolicy().getOrDefault("version", "points_v1")));
        return data;
    }

    public Map<String, Object> today(Long userId, LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
        Map<String, Object> row = mapper.selectToday(FitMysteryAppModule.APP_CODE, userId, effectiveDate);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", effectiveDate.toString());
        data.put("waterMl", row == null ? 0 : row.getOrDefault("waterMl", 0));
        data.put("steps", row == null ? 0 : row.getOrDefault("steps", 0));
        data.put("exerciseMinutes", row == null ? 0 : row.getOrDefault("exerciseMinutes", 0));
        data.put("pointsEarned", row == null ? 0 : row.getOrDefault("pointsEarned", 0));
        data.put("pointsSpent", row == null ? 0 : row.getOrDefault("pointsSpent", 0));
        data.put("pointsBalance", mapper.currentPointsBalance(FitMysteryAppModule.APP_CODE, userId));
        data.put("boxChanceBalance", mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId));
        data.put("serverTime", now().toString());
        return data;
    }

    private Validation validate(String typeRaw, String sourceRaw, BigDecimal quantity) {
        String type = normalize(typeRaw, "");
        String source = normalize(sourceRaw, "manual");
        if (!Set.of("water", "steps", "exercise", "weight").contains(type)) return new Validation(false, "unsupported_event_type");
        if (!Set.of("manual", "healthkit", "system").contains(source)) return new Validation(false, "unsupported_source");
        if (quantity.compareTo(BigDecimal.ZERO) < 0) return new Validation(false, "quantity_negative");
        if ("water".equals(type) && quantity.compareTo(BigDecimal.valueOf(2000)) > 0) return new Validation(false, "single_water_too_large");
        if ("exercise".equals(type) && quantity.compareTo(BigDecimal.valueOf(240)) > 0) return new Validation(false, "single_exercise_too_large");
        return new Validation(true, null);
    }

    private int calculatePoints(String type, BigDecimal quantity) {
        return switch (type) {
            case "water" -> quantity.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue() * 2;
            case "steps" -> quantity.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).intValue() * 5;
            case "exercise" -> quantity.divide(BigDecimal.valueOf(10), 0, RoundingMode.DOWN).intValue() * 8;
            default -> 0;
        };
    }

    private int waterMl(FitActivityEventEntity e) { return "water".equals(e.getEventType()) ? e.getQuantity().intValue() : 0; }
    private int steps(FitActivityEventEntity e) { return "steps".equals(e.getEventType()) ? e.getQuantity().intValue() : 0; }
    private int exerciseMinutes(FitActivityEventEntity e) { return "exercise".equals(e.getEventType()) ? e.getQuantity().intValue() : 0; }
    private String defaultUnit(String type) { return "water".equals(type) ? "ml" : ("steps".equals(type) ? "steps" : "minutes"); }
    private String required(String value, String field) { if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " required"); return value.trim(); }
    private String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT); }
    private OffsetDateTime parseTime(String value) { return value == null || value.isBlank() ? now() : OffsetDateTime.parse(value); }
    private OffsetDateTime parseOptionalTime(String value) { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private record Validation(boolean accepted, String reason) {}
    public record ActivityBatchSubmitRequest(
        @Schema(description = "活动事件列表，单次 1-50 条。", example = "[{\"eventType\":\"steps\",\"quantity\":1200}]") List<ActivityEventRequest> events
    ) {}
    public record ActivityEventRequest(
        @Schema(description = "幂等键，用于去重。", example = "activity-20260428-001") String idempotencyKey,
        @Schema(description = "事件类型。", example = "steps") String eventType,
        @Schema(description = "事件来源。", example = "healthkit") String source,
        @Schema(description = "数量。", example = "1200") BigDecimal quantity,
        @Schema(description = "单位。", example = "count") String unit,
        @Schema(description = "事件发生时间，ISO-8601 格式。", example = "2026-04-28T09:00:00Z") String occurredAt,
        @Schema(description = "客户端记录时间，ISO-8601 格式。", example = "2026-04-28T09:00:05Z") String clientRecordedAt
    ) {}
}
