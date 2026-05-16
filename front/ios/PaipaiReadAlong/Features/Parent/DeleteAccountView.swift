import SwiftUI

struct DeleteAccountView: View {
    @EnvironmentObject var appState: AppState
    @State private var email = ""
    @State private var code = ""
    @State private var deliveryNote: String?

    private var supportEmail: String? {
        guard let raw = appState.bootstrap.supportEmail?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        return raw
    }

    private var deleteAccountURL: URL? {
        guard let raw = appState.bootstrap.deleteAccountUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        return URL(string: raw)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: AppLayout.spacingXL) {
                MainCard {
                    VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                        Text(appState.uiText("删除账号前请确认", "Before deleting your account"))
                            .font(AppTypography.headline)
                        Text(appState.uiText("1. 账号删除后会清理登录状态并停用孩子档案与句卡数据。\n2. 需要临时输入一个可接收验证码的邮箱完成二次确认。\n3. 该邮箱仅用于这次删除验证，不作为登录方式长期保存。", "1. Deleting the account will revoke the session and deactivate child profiles and review cards.\n2. Enter a reachable email address for one-time deletion verification.\n3. The email is used only for this deletion check and is not kept as a long-term sign-in method."))
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }

                VerificationCodeInputView(
                    title: appState.uiText("删除账号验证码", "Account deletion verification"),
                    subtitle: appState.uiText("为了防止误触，需要先输入邮箱接收验证码，再确认删除。", "To prevent accidental deletion, enter an email address, receive a verification code, and then confirm deletion."),
                    emailLabel: appState.uiText("确认邮箱", "Confirmation email"),
                    codeLabel: appState.uiText("验证码", "Code"),
                    emailPlaceholder: appState.uiText("删除确认邮箱", "Confirmation email"),
                    codePlaceholder: appState.uiText("删除验证码", "Deletion code"),
                    sendTitle: appState.uiText("发送删除验证码", "Send deletion code"),
                    confirmTitle: appState.uiText("确认删除账号", "Confirm account deletion"),
                    footer: appState.uiText("请输入本次可接收验证码的邮箱。系统会用它完成删除确认，但不会把它作为长期登录邮箱保存。", "Enter an email address that can receive the code right now. It is used for deletion confirmation only and is not kept as a long-term sign-in email."),
                    email: $email,
                    code: $code,
                    deliveryNote: $deliveryNote,
                    onSend: sendCode,
                    onConfirm: confirmDeletion
                )

                if supportEmail != nil || deleteAccountURL != nil {
                    MainCard {
                        VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                            Text(appState.uiText("收不到验证码？", "Cannot receive the code?"))
                                .font(AppTypography.headline)
                            Text(appState.uiText("如果临时输入的邮箱无法接收验证码，请使用下面的人工 fallback 入口。", "If the email address you entered cannot receive the verification code, use the fallback support entry below."))
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.textSecondary)
                            if let deleteAccountURL {
                                Link(destination: deleteAccountURL) {
                                    Label(appState.uiText("打开删除账号帮助页", "Open delete-account help page"), systemImage: "link")
                                        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                                        .contentShape(Rectangle())
                                }
                            }
                            if let supportEmail, let mailURL = URL(string: "mailto:\(supportEmail)") {
                                Link(destination: mailURL) {
                                    Label(supportEmail, systemImage: "envelope")
                                        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                                        .contentShape(Rectangle())
                                }
                            }
                        }
                    }
                }

                if let receipt = appState.lastDeletionReceipt {
                    MainCard {
                        VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                            Text(appState.uiText("最近删除结果", "Latest deletion result"))
                                .font(AppTypography.headline)
                            Text(appState.uiText("状态：", "Status: ") + "\(receipt.status) / \(receipt.executionStatus)")
                                .font(AppTypography.footnote)
                            Text(receipt.note)
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.textSecondary)
                        }
                    }
                }
            }
            .padding()
                .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .appScrollDismissesKeyboardInteractively()
        .background(AppColors.background)
        .navigationTitle(appState.uiText("删除账号", "Delete Account"))
    }

    private func sendCode() async {
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedEmail.isEmpty else {
            appState.errorMessage = appState.uiText("请先输入接收验证码的邮箱。", "Please enter an email address to receive the code.")
            return
        }
        if let receipt = await appState.requestDeletionCode(email: normalizedEmail) {
            deliveryNote = receipt.note + (receipt.debugCode.map { appState.uiText("（调试码：\($0)）", " (Debug code: \($0))") } ?? "")
        }
    }

    private func confirmDeletion() async {
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedEmail.isEmpty else {
            appState.errorMessage = appState.uiText("请先输入确认邮箱。", "Please enter the confirmation email first.")
            return
        }
        _ = await appState.confirmDeletion(code: code, email: normalizedEmail)
    }
}
