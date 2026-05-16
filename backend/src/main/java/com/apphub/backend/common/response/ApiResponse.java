package com.apphub.backend.common.response;

/**
 * `ApiResponse` 类。
 * 用于承载统一后端中的基础职责，并作为对应领域逻辑的实现入口或数据结构载体。
 */

public record ApiResponse<T>(
    boolean success,
    String requestId,
    T data,
    String message
) {
    public static <T> ApiResponse<T> success(String requestId, T data) {
        return new ApiResponse<>(true, requestId, data, null);
    }
}
