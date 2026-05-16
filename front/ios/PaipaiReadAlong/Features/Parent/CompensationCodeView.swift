import SwiftUI

struct CompensationCodeView: View {
    @EnvironmentObject private var appState: AppState
    @State private var compensationCode = ""
    @State private var isSubmitting = false
    @State private var validationMessage: String?
    @State private var feedbackMessage: String?
    @State private var feedbackIsSuccess = false
    @State private var lastReceipt: CompensationRedeemReceipt?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingL) {
                headerSection
                inputSection
                if let validationMessage {
                    noticeCard(
                        icon: "exclamationmark.triangle.fill",
                        title: appState.uiText("格式需要调整", "Fix the format"),
                        message: validationMessage,
                        tint: AppColors.warning
                    )
                }
                if let feedbackMessage {
                    resultCard(message: feedbackMessage, isSuccess: feedbackIsSuccess, receipt: lastReceipt)
                }
            }
            .padding()
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle(appState.uiText("权益补偿", "Compensation"))
        .navigationBarTitleDisplayMode(.inline)
        .appScrollDismissesKeyboardInteractively()
        .appKeyboardDoneToolbar(doneTitle: appState.uiText("完成", "Done"))
        .onChange(of: compensationCode) { _, newValue in
            let normalized = normalizeInput(newValue)
            if normalized != newValue {
                compensationCode = normalized
                return
            }
            validationMessage = nil
            feedbackMessage = nil
            lastReceipt = nil
        }
    }

    private var headerSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                Text(appState.uiText("输入后端发放的补偿码，系统会自动校验格式、有效期和使用状态。", "Enter the backend-issued compensation code. The server checks the format, expiry, and usage state."))
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(appState.uiText("同一补偿码只能使用一次，成功后会直接补到当前账号。", "Each code can be redeemed once and will be applied to the current account immediately."))
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: AppLayout.spacingS) {
                        ruleChip(icon: "checkmark.seal.fill", text: appState.uiText("后端校验", "Server verified"), tint: AppColors.success)
                        ruleChip(icon: "clock.fill", text: appState.uiText("未过期可用", "Not expired"), tint: AppColors.info)
                        ruleChip(icon: "lock.fill", text: appState.uiText("单码单次", "Single use"), tint: AppColors.warning)
                    }
                    VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                        ruleChip(icon: "checkmark.seal.fill", text: appState.uiText("后端校验", "Server verified"), tint: AppColors.success)
                        ruleChip(icon: "clock.fill", text: appState.uiText("未过期可用", "Not expired"), tint: AppColors.info)
                        ruleChip(icon: "lock.fill", text: appState.uiText("单码单次", "Single use"), tint: AppColors.warning)
                    }
                }
            }
        }
    }

    private var inputSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("补偿码", "Compensation code"))
                    .font(AppTypography.headline)
                TextField(appState.uiText("请输入补偿码", "Enter the code"), text: $compensationCode)
                    .keyboardType(.asciiCapable)
                    .textInputAutocapitalization(.characters)
                    .textContentType(.oneTimeCode)
                    .autocorrectionDisabled()
                    .appInputSurface()

                Text(appState.uiText("格式示例：PP-ABCDE-FGHJK-MNPQR", "Format example: PP-ABCDE-FGHJK-MNPQR"))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)

                PrimaryButton(
                    title: appState.uiText("提交补偿码", "Redeem code"),
                    icon: "arrow.right.circle.fill",
                    isLoading: isSubmitting,
                    isDisabled: !canSubmit
                ) {
                    Task { await submit() }
                }
            }
        }
    }

    private func resultCard(message: String, isSuccess: Bool, receipt: CompensationRedeemReceipt?) -> some View {
        let tint = isSuccess ? AppColors.success : AppColors.error
        return MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Label(
                    isSuccess ? appState.uiText("补偿成功", "Redeemed") : appState.uiText("补偿失败", "Redeem failed"),
                    systemImage: isSuccess ? "checkmark.seal.fill" : "xmark.octagon.fill"
                )
                .font(AppTypography.headline)
                .foregroundColor(tint)

                Text(message)
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                if let receipt {
                    VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                        detailRow(title: appState.uiText("权益类型", "Benefit type"), value: benefitLabel(for: receipt))
                        detailRow(title: appState.uiText("补偿内容", "Benefit summary"), value: receipt.benefitSummary)
                        if let validUntil = receipt.validUntil {
                            detailRow(title: appState.uiText("有效期", "Valid until"), value: formatDate(validUntil))
                        }
                        if let state = receipt.accountState {
                            detailRow(title: appState.uiText("当前套餐", "Current plan"), value: state.entitlement.planName)
                            detailRow(title: appState.uiText("当前权益", "Current entitlement"), value: entitlementDetail(for: receipt, state: state))
                        }
                    }
                    .padding(.top, 4)
                }
            }
        }
    }

    private func noticeCard(icon: String, title: String, message: String, tint: Color) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                Label(title, systemImage: icon)
                    .font(AppTypography.headline)
                    .foregroundColor(tint)
                Text(message)
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func ruleChip(icon: String, text: String, tint: Color) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: 12, weight: .semibold))
            Text(text)
                .font(AppTypography.caption)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .allowsTightening(true)
        }
        .foregroundColor(tint)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
        .background(tint.opacity(0.10))
        .clipShape(Capsule())
    }

    private func detailRow(title: String, value: String) -> some View {
        HStack(alignment: .top, spacing: AppLayout.spacingS) {
            Text(title)
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textTertiary)
                .frame(width: 84, alignment: .leading)
            Text(value)
                .font(AppTypography.bodySmall)
                .foregroundColor(AppColors.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var canSubmit: Bool {
        !isSubmitting && isCodeValid(normalizedCode(compensationCode))
    }

    private func submit() async {
        let normalized = normalizedCode(compensationCode)
        guard isCodeValid(normalized) else {
            validationMessage = appState.uiText("请检查补偿码格式，示例为 PP-ABCDE-FGHJK-MNPQR。", "Check the code format. Example: PP-ABCDE-FGHJK-MNPQR.")
            return
        }
        isSubmitting = true
        defer { isSubmitting = false }
        validationMessage = nil
        feedbackMessage = nil
        lastReceipt = nil

        guard let receipt = await appState.redeemCompensationCode(normalized) else {
            feedbackIsSuccess = false
            feedbackMessage = appState.errorMessage ?? appState.uiText("补偿失败，请稍后重试。", "Redemption failed. Try again later.")
            return
        }

        feedbackIsSuccess = receipt.status == "applied"
        feedbackMessage = receipt.message ?? appState.uiText("补偿成功，权益已到账。", "Redeemed successfully. Benefits have been applied.")
        lastReceipt = receipt
        compensationCode = ""
    }

    private func normalizeInput(_ raw: String) -> String {
        normalizedCode(raw)
    }

    private func normalizedCode(_ raw: String) -> String {
        let compact = raw.uppercased().filter { $0.isLetter || $0.isNumber }
        guard compact.count == 17, compact.hasPrefix("PP") else {
            return raw.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let body = String(compact.dropFirst(2))
        let first = body.prefix(5)
        let second = body.dropFirst(5).prefix(5)
        let third = body.suffix(5)
        return "PP-\(first)-\(second)-\(third)"
    }

    private func isCodeValid(_ code: String) -> Bool {
        code.range(of: #"^PP-(?:[A-Z2-9]{5}-){2}[A-Z2-9]{5}$"#, options: .regularExpression) != nil
    }

    private func formatDate(_ value: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: value) else { return value }
        let display = DateFormatter()
        display.locale = Locale.current
        display.dateFormat = "yyyy-MM-dd HH:mm"
        return display.string(from: date)
    }

    private func benefitLabel(for receipt: CompensationRedeemReceipt) -> String {
        switch receipt.benefitType {
        case "plan":
            return appState.uiText("方案权益", "Plan benefit")
        case "usage_credit":
            return appState.uiText("次数补偿", "Usage credit")
        default:
            return receipt.benefitType
        }
    }

    private func entitlementDetail(for receipt: CompensationRedeemReceipt, state: AccountState) -> String {
        if let serviceType = receipt.serviceType {
            if serviceType == "cloud_ocr" || serviceType == "cloud_tts" {
                let snapshot = appState.cloudUsageState
                let remaining = serviceType == "cloud_ocr" ? snapshot?.ocr.remainingCount : snapshot?.tts.remainingCount
                return appState.uiText("云端剩余 \(remaining ?? 0) 次", "Cloud remaining \(remaining ?? 0)")
            }
            let remaining = serviceType == "speech" ? state.quota.speechRemaining : state.quota.captureRemaining
            return appState.uiText("剩余 \(remaining) 次", "Remaining \(remaining)")
        }
        if let validUntil = receipt.validUntil {
            return appState.uiText("到期 \(formatDate(validUntil))", "Expires \(formatDate(validUntil))")
        }
        return appState.uiText("已更新到当前账号", "Applied to the current account")
    }
}
