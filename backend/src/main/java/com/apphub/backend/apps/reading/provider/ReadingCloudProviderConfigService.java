package com.apphub.backend.apps.reading.provider;

import com.apphub.backend.apps.reading.ReadingAppModule;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * reading 云端 provider 配置服务。
 * 统一从 sys_remote_config 的 `cloud_provider` namespace 读取 OCR / TTS provider 配置，避免把 region、endpoint、headers、model 等写死在代码中。
 */
@Service
public class ReadingCloudProviderConfigService {
    private static final String APP_CODE = ReadingAppModule.APP_CODE;
    private static final String NAMESPACE = "cloud_provider";

    private final SysRemoteConfigService sysRemoteConfigService;

    public ReadingCloudProviderConfigService(SysRemoteConfigService sysRemoteConfigService) {
        this.sysRemoteConfigService = sysRemoteConfigService;
    }

    @SuppressWarnings("unchecked")
    public CloudOcrConfig ocr() {
        Map<String, Object> items = sysRemoteConfigService.loadNamespace(APP_CODE, NAMESPACE).items();
        return new CloudOcrConfig(
            stringValue(items, "ocr.vendor", "alibaba_bailian"),
            stringValue(items, "ocr.region", "singapore"),
            stringValue(items, "ocr.endpoint", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"),
            stringValue(items, "ocr.apiKeyEnvName", "DASHSCOPE_API_KEY"),
            mapValue(items, "ocr.headers"),
            stringValue(items, "ocr.model", "qwen-vl-ocr-latest"),
            stringValue(items, "ocr.prompt", "Please output only the text content from the image without any additional descriptions or formatting."),
            intValue(items, "ocr.minPixels", 3072),
            intValue(items, "ocr.maxPixels", 8388608)
        );
    }

    @SuppressWarnings("unchecked")
    public CloudTtsConfig tts() {
        Map<String, Object> items = sysRemoteConfigService.loadNamespace(APP_CODE, NAMESPACE).items();
        return new CloudTtsConfig(
            stringValue(items, "tts.vendor", "alibaba_bailian"),
            stringValue(items, "tts.region", "singapore"),
            stringValue(items, "tts.wsUrl", "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference"),
            stringValue(items, "tts.apiKeyEnvName", "DASHSCOPE_API_KEY"),
            mapValue(items, "tts.headers"),
            stringValue(items, "tts.model", "cosyvoice-v3-flash"),
            stringValue(items, "tts.voice", "longanyang"),
            stringValue(items, "tts.format", "mp3"),
            intValue(items, "tts.sampleRate", 22050),
            intValue(items, "tts.volume", 50),
            doubleValue(items, "tts.rate", 1.0),
            doubleValue(items, "tts.pitch", 1.0)
        );
    }

    private String stringValue(Map<String, Object> items, String key, String defaultValue) {
        Object value = items.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> mapValue(Map<String, Object> items, String key) {
        Object value = items.get(key);
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return result;
        }
        return Map.of();
    }

    private int intValue(Map<String, Object> items, String key, int defaultValue) {
        Object value = items.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double doubleValue(Map<String, Object> items, String key, double defaultValue) {
        Object value = items.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public record CloudOcrConfig(
        String vendor,
        String region,
        String endpoint,
        String apiKeyEnvName,
        Map<String, String> headers,
        String model,
        String prompt,
        int minPixels,
        int maxPixels
    ) {}

    public record CloudTtsConfig(
        String vendor,
        String region,
        String wsUrl,
        String apiKeyEnvName,
        Map<String, String> headers,
        String model,
        String voice,
        String format,
        int sampleRate,
        int volume,
        double rate,
        double pitch
    ) {}
}
