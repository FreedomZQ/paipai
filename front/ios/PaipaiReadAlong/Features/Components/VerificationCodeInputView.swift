import SwiftUI

struct VerificationCodeInputView: View {
    @EnvironmentObject var appState: AppState

    let title: String
    let subtitle: String
    let emailLabel: String
    let codeLabel: String
    let emailPlaceholder: String
    let codePlaceholder: String
    let sendTitle: String
    let confirmTitle: String
    let footer: String?
    @Binding var email: String
    @Binding var code: String
    @Binding var deliveryNote: String?
    let onSend: () async -> Void
    let onConfirm: () async -> Void

    var body: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(title)
                    .font(AppTypography.headline)
                Text(subtitle)
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textSecondary)
                VStack(alignment: .leading, spacing: 6) {
                    Text(emailLabel)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                    TextField(emailPlaceholder, text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .appInputSurface()
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text(codeLabel)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                    TextField(codePlaceholder, text: $code)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .appInputSurface()
                }
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: AppLayout.spacingM) {
                        SecondaryButton(title: sendTitle) { Task { await onSend() } }
                        PrimaryButton(title: confirmTitle, isDisabled: code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) { Task { await onConfirm() } }
                    }
                    VStack(spacing: AppLayout.spacingM) {
                        SecondaryButton(title: sendTitle) { Task { await onSend() } }
                        PrimaryButton(title: confirmTitle, isDisabled: code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) { Task { await onConfirm() } }
                    }
                }
                if let deliveryNote, !deliveryNote.isEmpty {
                    Text(deliveryNote)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                }
                if let footer, !footer.isEmpty {
                    Text(footer)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                }
            }
        }
        .appKeyboardDoneToolbar(doneTitle: appState.uiText("完成", "Done"))
    }
}
