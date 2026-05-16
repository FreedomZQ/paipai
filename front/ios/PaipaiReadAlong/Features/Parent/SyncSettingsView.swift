import SwiftUI

struct SyncSettingsView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        List {
            Section {
                Toggle(isOn: Binding(
                    get: { appState.syncRuntimeState.cloudSyncEnabled },
                    set: { enabled in
                        Task { await appState.setCloudSyncEnabled(enabled) }
                    }
                )) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(appState.uiText("开启跨设备同步", "Enable cross-device sync"))
                        Text(appState.uiText("将孩子档案、句卡、复习记录与偏好同步到云端。", "Sync child profiles, cards, review progress and preferences across devices."))
                            .font(AppTypography.footnote)
                            .foregroundColor(.secondary)
                    }
                    .frame(minHeight: 56)
                }
            }

            Section(appState.uiText("同步状态", "Status")) {
                HStack {
                    Text(appState.uiText("当前状态", "Current status"))
                    Spacer()
                    Text(statusText(appState.syncRuntimeState.status))
                        .foregroundColor(statusColor(appState.syncRuntimeState.status))
                }
                if let installationId = appState.syncRuntimeState.installationId {
                    HStack {
                        Text("Installation ID")
                        Spacer()
                        Text(String(installationId.prefix(8)) + "…")
                            .foregroundColor(.secondary)
                    }
                }
                HStack {
                    Text(appState.uiText("待上传改动", "Pending changes"))
                    Spacer()
                    Text("\(appState.syncRuntimeState.pendingChangeCount)")
                        .foregroundColor(.secondary)
                }
                if let lastSyncAt = appState.syncRuntimeState.lastSyncAt {
                    HStack {
                        Text(appState.uiText("最近同步", "Last sync"))
                        Spacer()
                        Text(lastSyncAt)
                            .font(AppTypography.footnote)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.trailing)
                    }
                }
                if let error = appState.syncRuntimeState.lastErrorMessage {
                    Text(error)
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.error)
                }
            }

            if !appState.syncRuntimeState.rejectedItems.isEmpty {
                Section(appState.uiText("被拒绝的改动", "Rejected changes")) {
                    ForEach(appState.syncRuntimeState.rejectedItems) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(item.entityType) · \(item.reasonCode)")
                                .font(AppTypography.subheadline.bold())
                            Text(item.reasonMessage)
                                .font(AppTypography.footnote)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }

            Section {
                Button(appState.uiText("立即同步", "Sync now")) {
                    Task { await appState.syncNowFromSettings() }
                }
                .frame(minHeight: AppLayout.minimumTapTarget)
                .disabled(!appState.syncRuntimeState.cloudSyncEnabled)

                Button(appState.uiText("请求重建同步", "Request rebuild")) {
                    Task { await appState.requestSyncRebuild() }
                }
                .frame(minHeight: AppLayout.minimumTapTarget)
                .disabled(!appState.syncRuntimeState.cloudSyncEnabled)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(AppColors.background)
        .navigationTitle(appState.uiText("云同步", "Cloud Sync"))
    }

    private func statusText(_ status: PowerSyncRuntimeStatus) -> String {
        switch status {
        case .idle: return appState.uiText("空闲", "Idle")
        case .bootstrapping: return appState.uiText("初始化中", "Bootstrapping")
        case .syncing: return appState.uiText("同步中", "Syncing")
        case .paused: return appState.uiText("已暂停", "Paused")
        case .error: return appState.uiText("异常", "Error")
        case .disabled: return appState.uiText("未开启", "Disabled")
        }
    }

    private func statusColor(_ status: PowerSyncRuntimeStatus) -> Color {
        switch status {
        case .idle: return AppColors.success
        case .bootstrapping, .syncing: return AppColors.primary
        case .paused: return AppColors.accentYellow
        case .error: return AppColors.error
        case .disabled: return .secondary
        }
    }
}
