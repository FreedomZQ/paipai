package com.apphub.backend.sys.app.model;

import java.util.List;

/**
 * 响应模型 `SystemPublicSurfaceView`。
 * 用于描述接口返回结果或观测视图，避免控制器直接暴露内部实体结构。
 */

public record SystemPublicSurfaceView(
    List<PublicEndpointView> endpoints
) {
    public record PublicEndpointView(
        String method,
        String path,
        String exposure,
        String protection
    ) {
    }
}
