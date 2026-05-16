package com.apphub.backend.apps.reading.compat.service;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.apps.reading.common.ReadingAuthenticatedUser;
import com.apphub.backend.sys.auth.entity.SysUserDeviceEventEntity;
import com.apphub.backend.sys.auth.mapper.SysUserDeviceEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class ReadingDeviceEventService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;

    private final SysUserDeviceEventMapper deviceEventMapper;
    private final ObjectMapper objectMapper;

    public ReadingDeviceEventService(SysUserDeviceEventMapper deviceEventMapper, ObjectMapper objectMapper) {
        this.deviceEventMapper = deviceEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeviceEventReceipt record(ReadingAuthenticatedUser userOrNull, DeviceEventRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SysUserDeviceEventEntity entity = new SysUserDeviceEventEntity();
        entity.setAppCode(APP_CODE);
        entity.setUserId(userOrNull == null ? null : userOrNull.userId());
        entity.setSessionId(userOrNull == null ? null : userOrNull.session().getId());
        entity.setEventType(defaultIfBlank(request.eventType(), "app_launch"));
        entity.setBundleId(blankToNull(request.bundleId()));
        entity.setClientPlatform(blankToNull(request.clientPlatform()));
        entity.setDeviceModel(blankToNull(request.deviceModel()));
        entity.setSystemName(blankToNull(request.systemName()));
        entity.setSystemVersion(blankToNull(request.systemVersion()));
        entity.setAppVersion(blankToNull(request.appVersion()));
        entity.setBuildNumber(blankToNull(request.buildNumber()));
        entity.setLocale(blankToNull(request.locale()));
        entity.setIpCountry(blankToNull(request.ipCountry()));
        entity.setPayloadJson(toJson(request.payload()));
        entity.setCreatedAt(now);
        deviceEventMapper.insert(entity);
        return new DeviceEventReceipt(entity.getId(), entity.getEventType(), now.toString(), userOrNull != null);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public record DeviceEventRequest(
        @Schema(description = "设备事件类型。", example = "app_launch") String eventType,
        @Schema(description = "客户端 Bundle ID。", example = "com.paipai.readalong.v2") String bundleId,
        @Schema(description = "客户端平台。", example = "ios") String clientPlatform,
        @Schema(description = "设备型号。", example = "iPhone16,2") String deviceModel,
        @Schema(description = "系统名称。", example = "iOS") String systemName,
        @Schema(description = "系统版本。", example = "18.0") String systemVersion,
        @Schema(description = "客户端版本号。", example = "1.0.0") String appVersion,
        @Schema(description = "客户端构建号。", example = "100") String buildNumber,
        @Schema(description = "本地化语言。", example = "zh-Hans") String locale,
        @Schema(description = "IP 归属国家或地区。", example = "CN") String ipCountry,
        @Schema(description = "事件扩展载荷。", example = "{\"source\":\"home\"}") Map<String, Object> payload
    ) {}

    public record DeviceEventReceipt(
        Long eventId,
        String eventType,
        String recordedAt,
        boolean authenticated
    ) {}
}
