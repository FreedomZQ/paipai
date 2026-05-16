package com.apphub.backend.sys.configcenter.model;

import java.util.Map;

/**
 * 响应模型 `RemoteConfigNamespaceView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record RemoteConfigNamespaceView(
    String appCode,
    String namespaceCode,
    Map<String, Object> items
) {
}
