package com.apphub.backend.apps.reading.provider;

import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.SysRemoteConfigService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 针对 reading 云端 provider DB 配置读取的测试。
 * 用于确保 OCR / TTS 的 endpoint、headers、model 和音色等都来自 sys_remote_config，而非写死在调用代码中。
 */
class ReadingCloudProviderConfigServiceTest {

    @Test
    void ocrConfigShouldComeFromRemoteConfigNamespace() {
        SysRemoteConfigService remoteConfigService = mock(SysRemoteConfigService.class);
        when(remoteConfigService.loadNamespace("paipai_readingcompanion", "cloud_provider"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "cloud_provider", Map.ofEntries(
                Map.entry("ocr.vendor", "alibaba_bailian"),
                Map.entry("ocr.region", "us_virginia"),
                Map.entry("ocr.endpoint", "https://dashscope-us.aliyuncs.com/compatible-mode/v1/chat/completions"),
                Map.entry("ocr.apiKeyEnvName", "DASHSCOPE_API_KEY_US"),
                Map.entry("ocr.headers", Map.of("Authorization", "Bearer ${API_KEY}", "Content-Type", "application/json")),
                Map.entry("ocr.model", "qwen-vl-ocr-latest"),
                Map.entry("ocr.prompt", "only text"),
                Map.entry("ocr.minPixels", 4096),
                Map.entry("ocr.maxPixels", 9000000)
            )));

        ReadingCloudProviderConfigService service = new ReadingCloudProviderConfigService(remoteConfigService);
        var config = service.ocr();

        assertThat(config.region()).isEqualTo("us_virginia");
        assertThat(config.endpoint()).isEqualTo("https://dashscope-us.aliyuncs.com/compatible-mode/v1/chat/completions");
        assertThat(config.apiKeyEnvName()).isEqualTo("DASHSCOPE_API_KEY_US");
        assertThat(config.headers()).containsEntry("Authorization", "Bearer ${API_KEY}");
        assertThat(config.model()).isEqualTo("qwen-vl-ocr-latest");
        assertThat(config.prompt()).isEqualTo("only text");
        assertThat(config.minPixels()).isEqualTo(4096);
        assertThat(config.maxPixels()).isEqualTo(9000000);
    }

    @Test
    void ttsConfigShouldComeFromRemoteConfigNamespace() {
        SysRemoteConfigService remoteConfigService = mock(SysRemoteConfigService.class);
        when(remoteConfigService.loadNamespace("paipai_readingcompanion", "cloud_provider"))
            .thenReturn(new RemoteConfigNamespaceView("paipai_readingcompanion", "cloud_provider", Map.ofEntries(
                Map.entry("tts.vendor", "alibaba_bailian"),
                Map.entry("tts.region", "beijing"),
                Map.entry("tts.wsUrl", "wss://dashscope.aliyuncs.com/api-ws/v1/inference"),
                Map.entry("tts.apiKeyEnvName", "DASHSCOPE_API_KEY_CN"),
                Map.entry("tts.headers", Map.of("Authorization", "Bearer ${API_KEY}")),
                Map.entry("tts.model", "cosyvoice-v3-plus"),
                Map.entry("tts.voice", "longxiaochun"),
                Map.entry("tts.format", "mp3"),
                Map.entry("tts.sampleRate", 24000),
                Map.entry("tts.volume", 60),
                Map.entry("tts.rate", 1.2),
                Map.entry("tts.pitch", 0.9)
            )));

        ReadingCloudProviderConfigService service = new ReadingCloudProviderConfigService(remoteConfigService);
        var config = service.tts();

        assertThat(config.region()).isEqualTo("beijing");
        assertThat(config.wsUrl()).isEqualTo("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        assertThat(config.apiKeyEnvName()).isEqualTo("DASHSCOPE_API_KEY_CN");
        assertThat(config.headers()).containsEntry("Authorization", "Bearer ${API_KEY}");
        assertThat(config.model()).isEqualTo("cosyvoice-v3-plus");
        assertThat(config.voice()).isEqualTo("longxiaochun");
        assertThat(config.sampleRate()).isEqualTo(24000);
        assertThat(config.volume()).isEqualTo(60);
        assertThat(config.rate()).isEqualTo(1.2);
        assertThat(config.pitch()).isEqualTo(0.9);
    }
}
