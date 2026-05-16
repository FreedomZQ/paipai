package com.apphub.backend.sys.configcenter.service;

import com.apphub.backend.sys.configcenter.entity.SysRemoteConfigEntity;
import com.apphub.backend.sys.configcenter.model.RemoteConfigNamespaceView;
import com.apphub.backend.sys.configcenter.service.crud.SysRemoteConfigCrudService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置中心服务 `SysRemoteConfigService`。
 * 负责承载该领域的核心业务逻辑、外部依赖调用以及数据读写编排。
 */

@Service
public class SysRemoteConfigService {

    private final SysRemoteConfigCrudService remoteConfigCrudService;
    private final ObjectMapper objectMapper;

    public SysRemoteConfigService(SysRemoteConfigCrudService remoteConfigCrudService, ObjectMapper objectMapper) {
        this.remoteConfigCrudService = remoteConfigCrudService;
        this.objectMapper = objectMapper;
    }

    public RemoteConfigNamespaceView loadNamespace(String appCode, String namespaceCode) {
        List<SysRemoteConfigEntity> entities = remoteConfigCrudService.list(new LambdaQueryWrapper<SysRemoteConfigEntity>()
            .eq(SysRemoteConfigEntity::getAppCode, appCode)
            .eq(SysRemoteConfigEntity::getNamespaceCode, namespaceCode)
            .eq(SysRemoteConfigEntity::getStatus, "active")
            .orderByDesc(SysRemoteConfigEntity::getUpdatedAt)
            .orderByDesc(SysRemoteConfigEntity::getId));
        Map<String, Object> items = new LinkedHashMap<>();
        for (SysRemoteConfigEntity entity : entities) {
            items.computeIfAbsent(entity.getConfigKey(), ignored -> parseConfigValue(entity.getConfigValueJson()));
        }
        return new RemoteConfigNamespaceView(appCode, namespaceCode, items);
    }

    private Object parseConfigValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return null;
            }
            JsonNode value = root.path("value");
            if (!value.isMissingNode()) {
                return objectMapper.convertValue(value, Object.class);
            }
            return objectMapper.convertValue(root, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
