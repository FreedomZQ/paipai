package com.apphub.backend.sys.app.model;

import java.util.Map;

/**
 * 数据模型 `AppDefinition`。
 * 用于承载 应用编排与发布门禁 领域在服务之间传递的结构化数据。
 */

public record AppDefinition(
    String code,
    String name,
    String apiPrefix,
    String tablePrefix,
    Support support,
    Map<String, Object> raw
) {
    public record Support(
        boolean legalRequired,
        boolean appleSignInRequired,
        boolean billingRequired
    ) {
    }
}
