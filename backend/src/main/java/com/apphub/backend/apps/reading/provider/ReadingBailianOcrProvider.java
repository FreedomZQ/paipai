package com.apphub.backend.apps.reading.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * reading 阿里百炼 OCR provider。
 * 使用 DashScope OpenAI 兼容接口调用 Qwen OCR 模型，endpoint / headers / model 全部从 DB 配置读取。
 */
@Service
public class ReadingBailianOcrProvider {
    private final ReadingCloudProviderConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ReadingBailianOcrProvider(ReadingCloudProviderConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public OcrProviderResult extract(String imageBase64, String mimeType, String promptOverride) {
        ReadingCloudProviderConfigService.CloudOcrConfig config = configService.ocr();
        String apiKey = resolveApiKey(config.apiKeyEnvName());
        if (apiKey == null || apiKey.isBlank()) {
            return new OcrProviderResult(false, null, null, null, null, null, null, "云端 OCR 尚未配置 API Key。", config.minPixels(), config.maxPixels());
        }
        try {
            Map<String, Object> requestBody = buildRequest(config, apiKey, imageBase64, mimeType, promptOverride);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.endpoint()))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            for (Map.Entry<String, String> entry : resolveHeaders(config.headers(), apiKey).entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new OcrProviderResult(false, null, config.vendor(), config.model(), config.region(), null, null, "云端 OCR 调用失败，http_status=" + response.statusCode(), config.minPixels(), config.maxPixels());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = extractContent(root);
            return new OcrProviderResult(true, null, config.vendor(), config.model(), config.region(), text, promptOverride == null || promptOverride.isBlank() ? config.prompt() : promptOverride, null, config.minPixels(), config.maxPixels());
        } catch (Exception exception) {
            return new OcrProviderResult(false, null, config.vendor(), config.model(), config.region(), null, null, "云端 OCR 异常：" + exception.getMessage(), config.minPixels(), config.maxPixels());
        }
    }

    private Map<String, Object> buildRequest(ReadingCloudProviderConfigService.CloudOcrConfig config, String apiKey, String imageBase64, String mimeType, String promptOverride) {
        String dataUrl = "data:" + (mimeType == null || mimeType.isBlank() ? "image/jpeg" : mimeType) + ";base64," + imageBase64;
        String prompt = promptOverride == null || promptOverride.isBlank() ? config.prompt() : promptOverride;
        Map<String, Object> imageItem = new LinkedHashMap<>();
        imageItem.put("type", "image_url");
        imageItem.put("image_url", Map.of("url", dataUrl));
        imageItem.put("min_pixels", config.minPixels());
        imageItem.put("max_pixels", config.maxPixels());
        Map<String, Object> textItem = Map.of("type", "text", "text", prompt);
        Map<String, Object> message = Map.of("role", "user", "content", java.util.List.of(imageItem, textItem));
        return Map.of(
            "model", config.model(),
            "messages", java.util.List.of(message)
        );
    }

    private Map<String, String> resolveHeaders(Map<String, String> configured, String apiKey) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            result.put(entry.getKey(), entry.getValue().replace("${API_KEY}", apiKey));
        }
        if (!result.containsKey("Authorization")) {
            result.put("Authorization", "Bearer " + apiKey);
        }
        if (!result.containsKey("Content-Type")) {
            result.put("Content-Type", "application/json");
        }
        return result;
    }

    private String resolveApiKey(String envName) {
        return envName == null || envName.isBlank() ? System.getenv("DASHSCOPE_API_KEY") : System.getenv(envName);
    }

    private String extractContent(JsonNode root) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        return content.toString();
    }

    public record OcrProviderResult(
        boolean success,
        String traceId,
        String provider,
        String model,
        String region,
        String text,
        String prompt,
        String errorMessage,
        Integer minPixels,
        Integer maxPixels
    ) {}
}
