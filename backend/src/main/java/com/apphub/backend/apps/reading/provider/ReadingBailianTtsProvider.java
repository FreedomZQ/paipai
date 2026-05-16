package com.apphub.backend.apps.reading.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * reading 阿里百炼 TTS provider。
 * 使用 CosyVoice WebSocket API 进行服务端代理调用，避免把 API Key 暴露给客户端。
 */
@Service
public class ReadingBailianTtsProvider {
    private final ReadingCloudProviderConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ReadingBailianTtsProvider(ReadingCloudProviderConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public TtsProviderResult synthesize(String text, String languageCode, Float rateOverride) {
        ReadingCloudProviderConfigService.CloudTtsConfig config = configService.tts();
        String apiKey = resolveApiKey(config.apiKeyEnvName());
        if (apiKey == null || apiKey.isBlank()) {
            return new TtsProviderResult(false, config.vendor(), config.model(), config.region(), null, null, text, languageCode, rateOverride, "云端 TTS 尚未配置 API Key。");
        }
        String taskId = UUID.randomUUID().toString();
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch finishedLatch = new CountDownLatch(1);
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        StringBuilder eventBuffer = new StringBuilder();
        final String[] failure = {null};

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                WebSocket.Listener.super.onOpen(webSocket);
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                eventBuffer.append(data);
                if (last) {
                    try {
                        JsonNode root = objectMapper.readTree(eventBuffer.toString());
                        String event = root.path("header").path("event").asText("");
                        if ("task-started".equals(event)) {
                            startedLatch.countDown();
                        }
                        if ("task-finished".equals(event)) {
                            finishedLatch.countDown();
                        }
                        if ("task-failed".equals(event)) {
                            failure[0] = root.path("header").path("error_message").asText("云端 TTS 任务失败。");
                            finishedLatch.countDown();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        eventBuffer.setLength(0);
                    }
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                audioBuffer.writeBytes(bytes);
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                failure[0] = error.getMessage();
                finishedLatch.countDown();
            }
        };

        try {
            WebSocket webSocket = buildWebSocket(config, apiKey, listener).join();
            sendText(webSocket, runTaskPayload(taskId, config, rateOverride));
            boolean started = startedLatch.await(15, TimeUnit.SECONDS);
            if (!started) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "task_not_started").join();
                return new TtsProviderResult(false, config.vendor(), config.model(), config.region(), null, null, text, languageCode, rateOverride, "云端 TTS 未返回 task-started 事件。");
            }
            sendText(webSocket, continueTaskPayload(taskId, text));
            sendText(webSocket, finishTaskPayload(taskId));
            boolean finished = finishedLatch.await(45, TimeUnit.SECONDS);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            if (!finished) {
                return new TtsProviderResult(false, config.vendor(), config.model(), config.region(), null, null, text, languageCode, rateOverride, "云端 TTS 超时未返回完成事件。");
            }
            if (failure[0] != null) {
                return new TtsProviderResult(false, config.vendor(), config.model(), config.region(), null, null, text, languageCode, rateOverride, failure[0]);
            }
            byte[] audioBytes = audioBuffer.toByteArray();
            if (audioBytes.length == 0) {
                return new TtsProviderResult(false, config.vendor(), config.model(), config.region(), null, null, text, languageCode, rateOverride, "云端 TTS 未返回任何音频数据。");
            }
            String base64 = java.util.Base64.getEncoder().encodeToString(audioBytes);
            return new TtsProviderResult(true, config.vendor(), config.model(), config.region(), base64, mimeType(config.format()), text, languageCode, rateOverride, null);
        } catch (Exception exception) {
            return new TtsProviderResult(false, config.vendor(), config.model(), config.region(), null, null, text, languageCode, rateOverride, "云端 TTS 异常：" + exception.getMessage());
        }
    }

    private CompletableFuture<WebSocket> buildWebSocket(ReadingCloudProviderConfigService.CloudTtsConfig config, String apiKey, WebSocket.Listener listener) {
        var builder = httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
        for (Map.Entry<String, String> entry : resolveHeaders(config.headers(), apiKey).entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        return builder.buildAsync(URI.create(config.wsUrl()), listener);
    }

    private void sendText(WebSocket webSocket, String payload) {
        webSocket.sendText(payload, true).join();
    }

    private String runTaskPayload(String taskId, ReadingCloudProviderConfigService.CloudTtsConfig config, Float rateOverride) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("header", Map.of(
            "action", "run-task",
            "task_id", taskId,
            "streaming", "duplex"
        ));
        root.put("payload", Map.of(
            "task_group", "audio",
            "task", "tts",
            "function", "SpeechSynthesizer",
            "model", config.model(),
            "parameters", Map.of(
                "text_type", "PlainText",
                "voice", config.voice(),
                "format", config.format(),
                "sample_rate", config.sampleRate(),
                "volume", config.volume(),
                "rate", rateOverride == null ? config.rate() : rateOverride,
                "pitch", config.pitch()
            ),
            "input", Map.of()
        ));
        return objectMapper.writeValueAsString(root);
    }

    private String continueTaskPayload(String taskId, String text) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "header", Map.of(
                "action", "continue-task",
                "task_id", taskId,
                "streaming", "duplex"
            ),
            "payload", Map.of(
                "input", Map.of("text", text)
            )
        ));
    }

    private String finishTaskPayload(String taskId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "header", Map.of(
                "action", "finish-task",
                "task_id", taskId,
                "streaming", "duplex"
            ),
            "payload", Map.of(
                "input", Map.of()
            )
        ));
    }

    private Map<String, String> resolveHeaders(Map<String, String> configured, String apiKey) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            result.put(entry.getKey(), entry.getValue().replace("${API_KEY}", apiKey));
        }
        if (!result.containsKey("Authorization")) {
            result.put("Authorization", "Bearer " + apiKey);
        }
        return result;
    }

    private String resolveApiKey(String envName) {
        return envName == null || envName.isBlank() ? System.getenv("DASHSCOPE_API_KEY") : System.getenv(envName);
    }

    private String mimeType(String format) {
        if (format == null) {
            return "audio/mpeg";
        }
        return switch (format.toLowerCase()) {
            case "wav" -> "audio/wav";
            case "pcm" -> "audio/L16";
            case "opus" -> "audio/opus";
            default -> "audio/mpeg";
        };
    }

    public record TtsProviderResult(
        boolean success,
        String provider,
        String model,
        String region,
        String audioBase64,
        String mimeType,
        String text,
        String languageCode,
        Float rate,
        String errorMessage
    ) {}
}
