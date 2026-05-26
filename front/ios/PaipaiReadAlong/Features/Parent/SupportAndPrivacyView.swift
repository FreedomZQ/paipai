import SwiftUI

struct SupportAndPrivacyView: View {
    @EnvironmentObject var appState: AppState
    @State private var showDeleteLearningDataConfirmation = false
    @State private var showClearLearningDataSuccess = false
    @State private var showClearLearningDataError = false
    @State private var showResetWalletConfirmation = false
    @State private var isProcessingLocalDataAction = false
    @State private var clearLearningDataErrorMessage = ""

    private var supportEmail: String? {
        guard let raw = appState.bootstrap.supportEmail?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        return raw
    }

    private var supportPageURL: URL? {
        guard let raw = appState.bootstrap.supportUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              let url = URL(string: raw),
              url.scheme?.lowercased() == "https" else { return nil }
        return url
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                MainCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(appState.uiText("支持与隐私", "Support & Privacy"))
                            .font(AppTypography.title3)
                        Text(appState.uiText("这里集中放家长最关心的几件事：联系客服、查看法律文档、管理本地数据。", "Here are the key parent tools: contact support, open legal documents, and manage local data."))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                MainCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(appState.uiText("数据与存储", "Data & Storage"))
                            .font(AppTypography.headline)
                        Text(appState.uiText(
                            "学习记录、生词本和历史记录仅保存在当前设备。卸载 App 会删除这些学习数据，开发者无法从服务器恢复。本机积分保存在系统 Keychain，换机、抹掉设备或重置本机钱包后可能无法恢复。",
                            "Learning records, vocabulary, and history are stored only on this device. Uninstalling the app deletes learning data, and the developer cannot restore it from a server. Local credits are stored in the system Keychain and may not be recoverable after changing devices, erasing the device, or resetting the local wallet."
                        ))
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                        Button(role: .destructive) {
                            showDeleteLearningDataConfirmation = true
                        } label: {
                            Label(appState.uiText("清除本地学习数据", "Clear local learning data"), systemImage: "trash")
                                .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                        }
                        .disabled(isProcessingLocalDataAction)

                        Button(role: .destructive) {
                            showResetWalletConfirmation = true
                        } label: {
                            Label(appState.uiText("重置本机积分钱包", "Reset local credit wallet"), systemImage: "creditcard.trianglebadge.exclamationmark")
                                .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                        }
                        .disabled(isProcessingLocalDataAction)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                MainCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(appState.uiText("联系客服", "Contact support"))
                            .font(AppTypography.headline)
                        if let supportEmail, let mailURL = URL(string: "mailto:\(supportEmail)") {
                            Link(destination: mailURL) {
                                Label(supportEmail, systemImage: "envelope")
                                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            }
                        }
                        if let supportPageURL {
                            Link(destination: supportPageURL) {
                                Label(supportPageURL.absoluteString, systemImage: "link")
                                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            }
                        }
                        NavigationLink {
                            LegalDocsView(documents: appState.legalDocs)
                        } label: {
                            Label(appState.uiText("查看法律文档", "View legal documents"), systemImage: "doc.text")
                                .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                MainCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(appState.uiText("问题反馈", "Feedback"))
                            .font(AppTypography.headline)
                        Text(feedbackInstruction)
                            .font(AppTypography.body)
                            .foregroundColor(AppColors.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        if let supportEmail, let mailURL = URL(string: "mailto:\(supportEmail)?subject=\(mailSubject)") {
                            Link(destination: mailURL) {
                                Label(supportEmail, systemImage: "envelope.fill")
                                    .font(AppTypography.body.weight(.semibold))
                                    .foregroundColor(AppColors.primary)
                                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            }
                        } else {
                            Text(appState.uiText("反馈邮箱暂未配置，请稍后再试。", "The feedback email is not configured yet. Please try again later."))
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.textTertiary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(20)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle(appState.uiText("隐私与支持", "Privacy & Support"))
        .alert(appState.uiText("清除本地学习数据", "Clear local learning data"), isPresented: $showDeleteLearningDataConfirmation) {
            Button(appState.uiText("取消", "Cancel"), role: .cancel) {}
            Button(appState.uiText("确认清除", "Clear"), role: .destructive) {
                Task { await clearLocalLearningData() }
            }
        } message: {
            Text(appState.uiText(
                "此操作将永久删除本地学习数据，且删除后无法恢复",
                "This permanently deletes local learning data and cannot be undone."
            ))
        }
        .alert(appState.uiText("清除完成", "Cleared"), isPresented: $showClearLearningDataSuccess) {
            Button(appState.uiText("知道了", "OK"), role: .cancel) {}
        } message: {
            Text(appState.uiText("已成功清除本地学习数据", "Local learning data has been cleared."))
        }
        .alert(appState.uiText("清除失败", "Clear failed"), isPresented: $showClearLearningDataError) {
            Button(appState.uiText("知道了", "OK"), role: .cancel) {}
        } message: {
            Text(clearLearningDataErrorMessage)
        }
        .alert(appState.uiText("重置本机积分钱包", "Reset local credit wallet"), isPresented: $showResetWalletConfirmation) {
            Button(appState.uiText("取消", "Cancel"), role: .cancel) {}
            Button(appState.uiText("重置", "Reset"), role: .destructive) {
                Task {
                    isProcessingLocalDataAction = true
                    _ = await appState.resetLocalCreditWallet()
                    isProcessingLocalDataAction = false
                }
            }
        } message: {
            Text(appState.uiText("此操作会清空当前设备保存的本机积分余额和本地交易去重记录，无法通过开发者服务器恢复。", "This clears local credit balances and local transaction deduplication records stored on this device. They cannot be restored through a developer server."))
        }
    }

    private var feedbackInstruction: String {
        guard let supportEmail else {
            return appState.uiText(
                "当前版本暂不提供直接填写反馈的入口。请通过邮箱发送问题描述和 App 版本；如家长愿意，可附大致设备类型。不要附带孩子照片、音频、OCR 原文或身份信息。",
                "This version does not include an in-app feedback form. Please send the issue and app version by email; parents may include a general device type if they choose. Do not include child photos, audio, OCR text, or identity information."
            )
        }
        return appState.uiText(
            "当前版本暂不提供直接填写反馈的入口。请将问题描述和 App 版本发送至 \(supportEmail)；如家长愿意，可附大致设备类型。不要附带孩子照片、音频、OCR 原文或身份信息。",
            "This version does not include an in-app feedback form. Please email the issue and app version to \(supportEmail); parents may include a general device type if they choose. Do not include child photos, audio, OCR text, or identity information."
        )
    }

    private var mailSubject: String {
        appState.uiText("拍拍伴读问题反馈", "Paipai ReadAlong Feedback")
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
    }

    private func clearLocalLearningData() async {
        guard !isProcessingLocalDataAction else { return }
        isProcessingLocalDataAction = true
        let didClear = await appState.deleteLocalLearningData()
        isProcessingLocalDataAction = false
        if didClear {
            showClearLearningDataSuccess = true
        } else {
            clearLearningDataErrorMessage = appState.errorMessage ?? appState.uiText(
                "清除本地学习数据失败，请稍后重试。账户、本机积分和会员状态不受影响。",
                "Failed to clear local learning data. Please try again. Account, local credits, and membership status are not affected."
            )
            appState.errorMessage = nil
            showClearLearningDataError = true
        }
    }
}
