import SwiftUI

struct SyncStatusBadgeView: View {
    let state: SyncRuntimeState

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: iconName)
                .font(AppTypography.caption.bold())
            Text(label)
                .font(AppTypography.caption.bold())
            if state.pendingChangeCount > 0 {
                Text("\(state.pendingChangeCount)")
                    .font(AppTypography.caption.bold())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(.white.opacity(0.18), in: Capsule())
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(backgroundColor, in: Capsule())
        .foregroundStyle(.white)
        .frame(minHeight: 32)
        .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 2)
    }

    private var label: String {
        switch state.status {
        case .idle: return "Sync"
        case .bootstrapping: return "Init"
        case .syncing: return "Syncing"
        case .paused: return "Paused"
        case .error: return "Error"
        case .disabled: return "Off"
        }
    }

    private var iconName: String {
        switch state.status {
        case .idle: return "checkmark.icloud.fill"
        case .bootstrapping: return "icloud.and.arrow.down"
        case .syncing: return "arrow.triangle.2.circlepath.icloud.fill"
        case .paused: return "pause.circle.fill"
        case .error: return "exclamationmark.icloud.fill"
        case .disabled: return "icloud.slash.fill"
        }
    }

    private var backgroundColor: Color {
        switch state.status {
        case .idle: return AppColors.success
        case .bootstrapping, .syncing: return AppColors.primary
        case .paused: return AppColors.accentYellow
        case .error: return AppColors.error
        case .disabled: return .gray
        }
    }
}
