package com.apphub.backend.apps.fitmystery.box;

import com.apphub.backend.apps.fitmystery.FitMysteryAppModule;
import com.apphub.backend.apps.fitmystery.config.FitMysteryConfigService;
import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxDrawEntity;
import com.apphub.backend.apps.fitmystery.domain.entity.FitBlindBoxItemEntity;
import com.apphub.backend.apps.fitmystery.domain.service.FitMysteryBoxDataService;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class FitMysteryBoxService {
    private static final String DEFAULT_POOL = "starter_pool";
    private final FitMysteryBoxDataService mapper;
    private final FitMysteryConfigService configService;
    private final SecureRandom random = new SecureRandom();

    public FitMysteryBoxService(FitMysteryBoxDataService mapper, FitMysteryConfigService configService) {
        this.mapper = mapper;
        this.configService = configService;
    }

    public Map<String, Object> state(Long userId) {
        Map<String, Object> boxPolicy = configService.boxPolicy();
        Map<String, Object> odds = configService.oddsDisclosure();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pointsBalance", mapper.currentPointsBalance(FitMysteryAppModule.APP_CODE, userId));
        data.put("chanceBalance", mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId));
        data.put("pointsPerDraw", intValue(boxPolicy.get("pointsPerDraw"), 100));
        data.put("defaultPoolCode", boxPolicy.getOrDefault("defaultPoolCode", DEFAULT_POOL));
        data.put("availablePools", List.of(Map.of(
            "poolCode", DEFAULT_POOL,
            "displayName", "健康初心者",
            "access", "available",
            "oddsVersion", odds.getOrDefault("version", "starter_pool_odds_v1"),
            "oddsDisclosure", odds,
            "noCashValueNotice", odds.getOrDefault("noCashValueNotice", boxPolicy.get("noCashValueNotice"))
        )));
        return data;
    }

    @Transactional
    public Map<String, Object> open(Long userId, OpenBoxRequest request) {
        String idempotencyKey = required(request.idempotencyKey(), "idempotencyKey");
        FitBlindBoxDrawEntity existing = mapper.selectDrawByIdempotencyKey(FitMysteryAppModule.APP_CODE, userId, idempotencyKey);
        if (existing != null) {
            return drawResponse(userId, existing.getId(), existing.getPoolCode(), existing.getItemCode(), existing.getRarity(), existing.getConsumeType(), existing.getOddsVersion(), false);
        }
        String poolCode = normalize(request.poolCode(), DEFAULT_POOL);
        String consumeType = normalize(request.consumeType(), "points");
        int pointsPerDraw = intValue(configService.boxPolicy().get("pointsPerDraw"), 100);
        int pointsBalance = mapper.currentPointsBalance(FitMysteryAppModule.APP_CODE, userId);
        int chanceBalance = mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId);
        int pointsSpent = 0;
        int chancesSpent = 0;
        if ("points".equals(consumeType)) {
            if (pointsBalance < pointsPerDraw) throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough points");
            pointsSpent = pointsPerDraw;
        } else if ("chance".equals(consumeType)) {
            if (chanceBalance < 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough box chances");
            chancesSpent = 1;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consumeType must be points or chance");
        }
        List<FitBlindBoxItemEntity> items = mapper.selectActiveItems(FitMysteryAppModule.APP_CODE, poolCode);
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "No active items in pool");
        FitBlindBoxItemEntity selected = weightedPick(items);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String drawId = UUID.randomUUID().toString();
        String oddsVersion = String.valueOf(configService.oddsDisclosure().getOrDefault("version", "starter_pool_odds_v1"));
        mapper.insertDraw(drawId, FitMysteryAppModule.APP_CODE, userId, poolCode, selected.getItemCode(), selected.getRarity(), consumeType, pointsSpent, chancesSpent, "server_weighted_v1", oddsVersion, idempotencyKey, now);
        if (pointsSpent > 0) {
            mapper.insertSpendPoints(UUID.randomUUID().toString(), FitMysteryAppModule.APP_CODE, userId, -pointsSpent, pointsBalance - pointsSpent, drawId, "box_points:" + idempotencyKey, now);
        }
        if (chancesSpent > 0) {
            mapper.insertSpendChance(UUID.randomUUID().toString(), FitMysteryAppModule.APP_CODE, userId, -1, chanceBalance - 1, drawId, "box_chance:" + idempotencyKey, now);
        }
        Integer before = mapper.obtainCount(FitMysteryAppModule.APP_CODE, userId, selected.getItemCode());
        mapper.upsertCollection(FitMysteryAppModule.APP_CODE, userId, selected.getItemCode(), drawId, now);
        return drawResponse(userId, drawId, poolCode, selected.getItemCode(), selected.getRarity(), consumeType, oddsVersion, before == null || before == 0);
    }

    public Map<String, Object> collection(Long userId, int limit) {
        return Map.of("items", mapper.selectCollection(FitMysteryAppModule.APP_CODE, userId, safeLimit(limit)), "serverTime", OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    public Map<String, Object> history(Long userId, int limit) {
        return Map.of("items", mapper.selectDrawHistory(FitMysteryAppModule.APP_CODE, userId, safeLimit(limit)), "serverTime", OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    private Map<String, Object> drawResponse(Long userId, String drawId, String poolCode, String itemCode, String rarity, String consumeType, String oddsVersion, boolean isNew) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("drawId", drawId);
        data.put("poolCode", poolCode);
        data.put("consumeType", consumeType);
        data.put("oddsVersion", oddsVersion);
        data.put("item", Map.of("itemCode", itemCode, "rarity", rarity));
        data.put("isNew", isNew);
        data.put("balances", Map.of(
            "pointsBalance", mapper.currentPointsBalance(FitMysteryAppModule.APP_CODE, userId),
            "chanceBalance", mapper.currentChanceBalance(FitMysteryAppModule.APP_CODE, userId)
        ));
        data.put("noCashValueNotice", configService.oddsDisclosure().getOrDefault("noCashValueNotice", "仅获得 App 内虚拟收藏卡，无现金价值，不可交易、转让、提现或兑换实物。"));
        return data;
    }

    private FitBlindBoxItemEntity weightedPick(List<FitBlindBoxItemEntity> items) {
        int total = items.stream().mapToInt(i -> i.getWeight() == null ? 0 : Math.max(0, i.getWeight())).sum();
        if (total <= 0) return items.get(0);
        int cursor = random.nextInt(total) + 1;
        int seen = 0;
        for (FitBlindBoxItemEntity item : items) {
            seen += item.getWeight() == null ? 0 : Math.max(0, item.getWeight());
            if (cursor <= seen) return item;
        }
        return items.get(items.size() - 1);
    }

    private int safeLimit(int limit) { return Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100)); }
    private int intValue(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String required(String value, String field) { if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " required"); return value.trim(); }
    public record OpenBoxRequest(
        @Schema(description = "幂等键，用于避免重复开盒。", example = "box-open-20260428-001") String idempotencyKey,
        @Schema(description = "奖池编码。", example = "starter_pool") String poolCode,
        @Schema(description = "消耗类型。", example = "free_quota") String consumeType
    ) {}
}
