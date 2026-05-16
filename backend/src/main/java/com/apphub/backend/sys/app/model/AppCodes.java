package com.apphub.backend.sys.app.model;

/**
 * 统一后端的 appCode 常量中心。
 *
 * <p>中文维护说明：这是 Java 源码中唯一允许出现具体 appCode 字面量的位置。
 * 业务类不要写死 {@code "paipai_readingcompanion"}，应引用 AppModule 或这里的常量。
 * 这样后续新增 App 时，可以把“对外产品身份”与“内部实现名 / 表前缀 / 兼容层路径”继续解耦。</p>
 */
public final class AppCodes {
    public static final String PAIPAI_READINGCOMPANION = "paipai_readingcompanion";
    public static final String SAVING = "saving";
    public static final String FITMYSTERY = "fitmystery";

    private AppCodes() {
    }
}
