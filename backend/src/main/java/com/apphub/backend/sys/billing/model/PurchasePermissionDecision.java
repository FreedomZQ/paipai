package com.apphub.backend.sys.billing.model;

import java.util.Map;

/**
 * 购买权限判定结果。
 *
 * <p>该模型由统一计费层返回给各业务 App，字段保持 App 无关：
 * appCode 表示当前应用，productCode 表示可选的具体购买项，allowed 表示是否允许继续购买流程。
 * messageKey/messageTextMap 用于前端按照界面语言展示文案，避免不同 App 在客户端硬编码禁购原因。
 */
public record PurchasePermissionDecision(
    String appCode,
    String productCode,
    Boolean allowed,
    String status,
    String reasonCode,
    String messageKey,
    Map<String, String> messageTextMap,
    String message
) {}
