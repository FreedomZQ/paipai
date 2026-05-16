# Paipai 编译失败隔离修复验证 - 2026-04-15

## 目标

在**不修改原仓库** `/home/admin/code/app/paipai/backend` 的前提下：

1. 复现当前编译失败
2. 定位最小修复点
3. 在沙箱中验证修复是否足以恢复绿色基线
4. 产出可回放补丁文件

## 原仓库状态

原路径：

```text
/home/admin/code/app/paipai/backend
```

实测执行：

```bash
cd /home/admin/code/app/paipai/backend
mvn -q test
```

失败症状：

- `AppStoreBillingService` 缺失 `currentAppStoreConfig()`
- `ApplePlatformReadinessService` 使用了 `appRuntimeConfigService` 但未声明/未注入

## 沙箱路径

为保证原仓库不被修改，复制到：

```text
/home/admin/code/app/backend/sandboxes/paipai-backend-compile-fix
```

## 最小修复点

### 修复点 1：`AppStoreBillingService`

补充方法：

```java
private AppProperties.Billing.AppStore currentAppStoreConfig() {
    return appRuntimeConfigService != null
        ? appRuntimeConfigService.billingAppStore()
        : appProperties.getBilling().getAppStore();
}
```

作用：
- 收口 `currentAppStoreConfig()` 调用缺失
- 保持对 `AppRuntimeConfigService` 的 DB override 兼容
- 当 runtime config service 不可用时回退到原 `appProperties`

### 修复点 2：`ApplePlatformReadinessService`

为类补充字段与构造注入：

```java
private final AppRuntimeConfigService appRuntimeConfigService;
```

作用：
- 与 `currentReadiness()` 内现有逻辑对齐
- 让 readiness 能优先读取 DB override 的 Apple auth / billing 配置

## 补丁文件

已生成：

```text
/home/admin/code/app/backend/patches/paipai-compile-fix-2026-04-15.patch
```

## 沙箱验证结果

在沙箱路径执行：

```bash
cd /home/admin/code/app/backend/sandboxes/paipai-backend-compile-fix
mvn -q test
```

结果：

- **PASS**
- 退出码：`0`

说明：
- 当前发现的编译失败已被这两个最小修复点收口
- 至少在当前仓库快照下，这不是系统性重构崩坏，而是**两处未收口引用**导致的基线中断

## 结论

1. paipai 当前失败点已完成**隔离修复验证**
2. 原仓库**未被修改**
3. 后续若需要真正修复原仓库，可直接应用本补丁
4. 该结果可以作为统一后端工作的可信输入：
   - paipai 的 Apple 能力仍可复用
   - 当前阻塞并非架构不可救，而是重构未收口
