import SwiftUI

struct CompensationCodeView: View {
    @EnvironmentObject private var appState: AppState
    @State private var compensationCode = ""
    @State private var isSubmitting = false
    @State private var resultAlert: RedeemResultAlert?

    private var isServerCompensationEnabled: Bool {
        AppIdentity.developerBackendEnabled
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingL) {
                headerSection
                inputSection
            }
            .padding()
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle(compensationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .appScrollDismissesKeyboardInteractively()
        .appKeyboardDoneToolbar(doneTitle: doneText)
        .alert(item: $resultAlert) { alert in
            Alert(
                title: Text(alert.title),
                dismissButton: .default(Text(okText))
            )
        }
        .onChange(of: compensationCode) { _, newValue in
            let normalized = normalizeInput(newValue)
            if normalized != newValue {
                compensationCode = normalized
                return
            }
        }
    }

    private var headerSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                Text(appState.localizedText(
                    zhHans: isServerCompensationEnabled
                        ? "输入家长支持流程发放的补偿码，系统会校验格式、有效期和使用状态。"
                        : "当前首发版本不启用补偿码。为降低儿童数据和购买争议处理风险，购买问题请优先通过 Apple 官方购买问题渠道处理。",
                    english: isServerCompensationEnabled
                        ? "Enter a compensation code issued through parent support. The system checks format, expiry, and usage state."
                        : "Compensation codes are not enabled in this launch version. To reduce child-data and purchase-dispute risk, purchase issues should be handled through Apple.",
                    japanese: isServerCompensationEnabled
                        ? "保護者サポートで発行された補償コードを入力してください。形式、有効期限、使用状態を確認します。"
                        : "初回リリースでは補償コードは有効ではありません。購入に関する問題は Apple の公式窓口で処理してください。",
                    korean: isServerCompensationEnabled
                        ? "부모 지원 절차에서 발급된 보상 코드를 입력하세요. 형식, 만료일, 사용 상태를 확인합니다."
                        : "첫 출시 버전에서는 보상 코드가 활성화되어 있지 않습니다. 구매 문제는 Apple 공식 경로를 이용해 주세요.",
                    spanish: isServerCompensationEnabled
                        ? "Introduce un codigo emitido por soporte para padres. El sistema comprueba formato, caducidad y uso."
                        : "Los codigos de compensacion no estan habilitados en esta version inicial. Los problemas de compra deben gestionarse con Apple."
                ))
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: AppLayout.spacingS) {
                        ruleChip(icon: "checkmark.seal.fill", text: serverVerifiedText, tint: AppColors.success)
                        ruleChip(icon: "clock.fill", text: notExpiredText, tint: AppColors.info)
                        ruleChip(icon: "lock.fill", text: localDuplicateCheckText, tint: AppColors.warning)
                    }
                    VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                        ruleChip(icon: "checkmark.seal.fill", text: serverVerifiedText, tint: AppColors.success)
                        ruleChip(icon: "clock.fill", text: notExpiredText, tint: AppColors.info)
                        ruleChip(icon: "lock.fill", text: localDuplicateCheckText, tint: AppColors.warning)
                    }
                }
            }
        }
    }

    private var inputSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(compensationCodeText)
                    .font(AppTypography.headline)
                TextField(inputPlaceholderText, text: $compensationCode)
                    .keyboardType(.asciiCapable)
                    .textInputAutocapitalization(.characters)
                    .textContentType(.oneTimeCode)
                    .autocorrectionDisabled()
                    .appInputSurface()

                Text(formatExampleText)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)

                PrimaryButton(
                    title: redeemButtonText,
                    icon: "arrow.right.circle.fill",
                    isLoading: isSubmitting,
                    isDisabled: !canSubmit
                ) {
                    Task { await submit() }
                }
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

    private var canSubmit: Bool {
        isServerCompensationEnabled && !isSubmitting && !compensationCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func submit() async {
        guard isServerCompensationEnabled else {
            resultAlert = RedeemResultAlert(title: appState.uiText("当前版本不启用补偿码", "Compensation codes are not enabled"))
            return
        }
        let normalized = normalizedCode(compensationCode)
        guard isCodeValid(normalized) else {
            resultAlert = RedeemResultAlert(title: redeemFailedText)
            return
        }
        isSubmitting = true
        defer { isSubmitting = false }

        guard await appState.redeemCompensationCode(normalized) != nil else {
            resultAlert = RedeemResultAlert(title: redeemFailedText)
            return
        }

        resultAlert = RedeemResultAlert(title: redeemSuccessText)
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

    private var compensationTitle: String {
        appState.localizedText(zhHans: "权益补偿", english: "Compensation", japanese: "補償", korean: "보상", spanish: "Compensacion")
    }

    private var doneText: String {
        appState.localizedText(zhHans: "完成", english: "Done", japanese: "完了", korean: "완료", spanish: "Listo")
    }

    private var okText: String {
        appState.localizedText(zhHans: "知道了", english: "OK", japanese: "OK", korean: "확인", spanish: "Aceptar")
    }

    private var serverVerifiedText: String {
        appState.localizedText(zhHans: "家长支持校验", english: "Support verified", japanese: "サポート確認", korean: "지원 확인", spanish: "Verificado por soporte")
    }

    private var notExpiredText: String {
        appState.localizedText(zhHans: "未过期可用", english: "Not expired", japanese: "有効期限内", korean: "만료 전", spanish: "No caducado")
    }

    private var localDuplicateCheckText: String {
        appState.localizedText(zhHans: "本机防重复", english: "Local duplicate check", japanese: "端末内の重複防止", korean: "기기 중복 방지", spanish: "Evita duplicados")
    }

    private var compensationCodeText: String {
        appState.localizedText(zhHans: "补偿码", english: "Compensation code", japanese: "補償コード", korean: "보상 코드", spanish: "Codigo de compensacion")
    }

    private var inputPlaceholderText: String {
        isServerCompensationEnabled
            ? appState.localizedText(zhHans: "输入发放的补偿码", english: "Enter the issued code", japanese: "発行されたコードを入力", korean: "발급된 코드를 입력", spanish: "Introduce el codigo emitido")
            : appState.localizedText(zhHans: "首发版本未启用", english: "Not enabled in this version", japanese: "このバージョンでは無効", korean: "이 버전에서는 비활성화됨", spanish: "No habilitado")
    }

    private var formatExampleText: String {
        appState.localizedText(zhHans: "格式示例：PP-ABCDE-FGHJK-MNPQR", english: "Format example: PP-ABCDE-FGHJK-MNPQR", japanese: "形式例：PP-ABCDE-FGHJK-MNPQR", korean: "형식 예: PP-ABCDE-FGHJK-MNPQR", spanish: "Ejemplo de formato: PP-ABCDE-FGHJK-MNPQR")
    }

    private var redeemButtonText: String {
        isServerCompensationEnabled
            ? appState.localizedText(zhHans: "提交补偿码", english: "Redeem code", japanese: "コードを交換", korean: "코드 교환", spanish: "Canjear codigo")
            : appState.localizedText(zhHans: "补偿码未启用", english: "Codes disabled", japanese: "コードは無効", korean: "코드 비활성화", spanish: "Codigos desactivados")
    }

    private var redeemFailedText: String {
        appState.localizedText(zhHans: "补偿兑换失败", english: "Redemption failed", japanese: "補償コード交換に失敗しました", korean: "보상 코드 교환 실패", spanish: "No se pudo canjear la compensacion")
    }

    private var redeemSuccessText: String {
        appState.localizedText(zhHans: "补偿兑换成功", english: "Redeemed successfully", japanese: "補償コードを交換しました", korean: "보상 코드 교환 완료", spanish: "Compensacion canjeada")
    }

}

private struct RedeemResultAlert: Identifiable {
    let id = UUID()
    let title: String
}
