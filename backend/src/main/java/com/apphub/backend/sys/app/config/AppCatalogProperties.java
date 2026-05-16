package com.apphub.backend.sys.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多 App 目录配置。
 *
 * <p>中文维护说明：</p>
 * <ul>
 *   <li>{@code supported} 只从 {@code application.yml} / 环境配置读取，不在 Java 里写死默认 App。</li>
 *   <li>新增 App 时，先在配置里增加 appCode 和 app-definition 路径，再补对应 AppModule。</li>
 *   <li>如果配置缺失，系统宁可不加载任何 App，也不要悄悄回退到 Paipai，避免账号、权益或同步数据串线。</li>
 * </ul>
 */

@ConfigurationProperties(prefix = "backend.apps")
public class AppCatalogProperties {

    private List<String> supported = List.of();
    private Map<String, String> definitions = new LinkedHashMap<>();

    public List<String> getSupported() {
        return supported;
    }

    public void setSupported(List<String> supported) {
        this.supported = supported;
    }

    public Map<String, String> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, String> definitions) {
        this.definitions = definitions;
    }
}
