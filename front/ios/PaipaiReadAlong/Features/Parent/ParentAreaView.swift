import AuthenticationServices
import SwiftUI
import UIKit

struct ParentAreaView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            Group {
                if !appState.isParentGateVerified {
                    ParentGateView(
                        onPass: {
                            appState.isParentGateVerified = true
                        }
                    )
                } else {
                    ParentDashboardView(
                        children: appState.children,
                        familyUsageSummary: appState.familyUsageSummary,
                        currentChildUsageSummary: appState.childUsageSummaries[appState.selectedChild.id]
                    )
                        .environmentObject(appState)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .toolbarBackground(.hidden, for: .navigationBar)
            .task {
                await appState.bootstrapIfNeeded()
                await appState.refreshParentData()
            }
        }
    }
}

struct ParentGateView: View {
    @EnvironmentObject var appState: AppState
    let onPass: () -> Void

    private enum Step {
        case math
        case checking
        case devicePassword
        case offlinePassword
        case createOfflinePassword
        case recovery
        case resetOfflinePassword
    }

    private let gateService = ParentGateService.shared
    @State private var step: Step = .math
    @State private var answer = ""
    @State private var num1 = Int.random(in: 10...99)
    @State private var num2 = Int.random(in: 1...9)
    @State private var message: String?
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var recoveryAnswers: [String: String] = [:]
    @State private var isPasswordVisible = false
    @State private var isBusy = false

    var correctAnswer: Int { num1 + num2 }

    var body: some View {
        GeometryReader { proxy in
            let isWide = proxy.size.width >= 700
            let contentSpacing: CGFloat = isWide ? 10 : 8
            let iconSize: CGFloat = isWide ? 40 : 34
            let titleFont = isWide ? AppTypography.title3 : AppTypography.headline
            let promptFont = isWide ? AppTypography.bodySmall : AppTypography.caption
            let equationFontSize: CGFloat = isWide ? 28 : 24
            let displayWidth: CGFloat = isWide ? 108 : 92
            let displayHeight: CGFloat = isWide ? 48 : 44
            let keypadMaxWidth: CGFloat = isWide ? 320 : 280
            let keyFontSize: CGFloat = isWide ? 20 : 19
            let keyHeight: CGFloat = isWide ? 44 : 42

            ScrollView {
                VStack(spacing: contentSpacing) {
                    Spacer(minLength: 0)

                    HStack(spacing: AppLayout.spacingS) {
                        Text("🔒")
                            .font(AppTypography.scaledFont(size: iconSize))
                        Text(appState.uiText("家长验证", "Parent Verification"))
                            .font(titleFont)
                            .foregroundColor(AppColors.textPrimary)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)

                    Text(appState.uiText("请完成一道简单算题后进入家长中心，避免孩子误触设置和账号操作。", "Solve one quick math question to enter Parents and avoid accidental settings or account changes."))
                        .font(promptFont)
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)

                    Group {
                        switch step {
                        case .math:
                            mathCard(
                                isWide: isWide,
                                equationFontSize: equationFontSize,
                                displayWidth: displayWidth,
                                displayHeight: displayHeight,
                                keypadMaxWidth: keypadMaxWidth,
                                keyFontSize: keyFontSize,
                                keyHeight: keyHeight
                            )
                        case .checking:
                            progressCard
                        case .devicePassword:
                            devicePasswordCard(isWide: isWide)
                        case .offlinePassword:
                            offlinePasswordCard(isWide: isWide)
                        case .createOfflinePassword:
                            createPasswordCard(isWide: isWide, isReset: false)
                        case .recovery:
                            recoveryCard(isWide: isWide)
                        case .resetOfflinePassword:
                            createPasswordCard(isWide: isWide, isReset: true)
                        }
                    }

                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, AppLayout.paddingScreen)
                .padding(.vertical, isWide ? AppLayout.spacingM : AppLayout.spacingS)
                .adaptiveContentFrame(maxWidth: isWide ? 560 : 420)
            }
        }
        .background(AppColors.background)
        .onAppear {
            answer = ""
            message = nil
            step = .math
        }
    }

    private func mathCard(
        isWide: Bool,
        equationFontSize: CGFloat,
        displayWidth: CGFloat,
        displayHeight: CGFloat,
        keypadMaxWidth: CGFloat,
        keyFontSize: CGFloat,
        keyHeight: CGFloat
    ) -> some View {
        VStack(spacing: AppLayout.spacingM) {
            MainCard {
                VStack(spacing: isWide ? 12 : 10) {
                    HStack(spacing: AppLayout.spacingS) {
                        Text("\(num1) + \(num2) =")
                            .font(AppTypography.scaledFont(size: equationFontSize, weight: .bold, design: .rounded))
                            .foregroundColor(AppColors.primary)

                        ParentAnswerDisplay(
                            text: answer,
                            placeholder: appState.uiText("答案", "Answer"),
                            width: displayWidth,
                            height: displayHeight
                        )
                    }
                    .frame(maxWidth: .infinity, alignment: .center)

                    ParentNumberPad(
                        answer: $answer,
                        clearTitle: appState.uiText("清空", "Clear"),
                        keyFontSize: keyFontSize,
                        keyHeight: keyHeight,
                        spacing: 8,
                        rowSpacing: 8
                    )
                    .frame(maxWidth: keypadMaxWidth)

                    Text(appState.uiText("先完成算题，再进行家长身份验证。", "Solve the math question first, then complete parent verification."))
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)

                    gateMessage
                }
                .frame(maxWidth: .infinity)
            }

            HStack(spacing: AppLayout.spacingM) {
                secondaryButton(title: appState.uiText("重新生成", "New question"), icon: "arrow.clockwise") {
                    regenerateMathQuestion()
                }
                primaryButton(title: appState.uiText("提交", "Submit"), icon: "checkmark.circle.fill", isDisabled: answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    verifyAnswer()
                }
            }
        }
    }

    private var progressCard: some View {
        MainCard {
            HStack(spacing: AppLayout.spacingM) {
                ProgressView()
                Text(appState.uiText("正在检查设备安全状态...", "Checking device security status..."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func devicePasswordCard(isWide: Bool) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("使用设备解锁密码", "Use device passcode"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("系统会验证当前设备的解锁密码。验证通过后进入家长区。", "iOS will verify this device's unlock passcode. After it passes, Parents will open."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                gateMessage
                primaryButton(title: appState.uiText("验证设备密码", "Verify device passcode"), icon: "lock.shield", isDisabled: isBusy) {
                    authenticateDevice()
                }
            }
        }
    }

    private func offlinePasswordCard(isWide: Bool) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("输入离线密码", "Enter offline password"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                passwordField(appState.uiText("离线密码", "Offline password"), text: $password)
                gateMessage
                primaryButton(title: appState.uiText("进入家长区", "Open Parents"), icon: "checkmark.circle.fill", isDisabled: password.isEmpty || isBusy) {
                    verifyOfflinePassword()
                }
                Button(appState.uiText("忘记密码", "Forgot password")) {
                    clearSensitiveInputs()
                    step = .recovery
                    message = nil
                }
                .font(AppTypography.buttonSmall)
                .foregroundColor(AppColors.primary)
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }

    private func createPasswordCard(isWide: Bool, isReset: Bool) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(isReset ? appState.uiText("重置离线密码", "Reset offline password") : appState.uiText("创建离线密码", "Create offline password"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("设备未设置解锁密码或已启用离线密码。请设置至少 6 位、同时包含数字和字母的本地密码。密码和找回答案只保存在本机 Keychain。", "Set a local password with at least 6 characters including letters and numbers. The password and recovery answers stay only in this device's Keychain."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                passwordField(appState.uiText("新密码", "New password"), text: $password)
                passwordField(appState.uiText("确认密码", "Confirm password"), text: $confirmPassword)
                ForEach(ParentGateService.recoveryQuestions) { question in
                    Text(questionText(question))
                        .font(AppTypography.footnote.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                    TextField(appState.uiText("请输入答案", "Enter answer"), text: bindingForRecoveryAnswer(question.id))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .font(AppTypography.bodySmall)
                        .padding(AppLayout.spacingM)
                        .background(Color.white)
                        .overlay(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM).stroke(AppColors.border, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM))
                }
                gateMessage
                primaryButton(title: isReset ? appState.uiText("保存新密码", "Save new password") : appState.uiText("创建并进入", "Create and continue"), icon: "key.fill", isDisabled: isBusy) {
                    saveOfflinePassword(isReset: isReset)
                }
            }
        }
    }

    private func recoveryCard(isWide: Bool) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("找回离线密码", "Recover offline password"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("锁定时间结束后，回答任意一个预设问题即可重置密码。", "After any lock has expired, answer any one saved question to reset the password."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(ParentGateService.recoveryQuestions) { question in
                    Text(questionText(question))
                        .font(AppTypography.footnote.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                    TextField(appState.uiText("答案", "Answer"), text: bindingForRecoveryAnswer(question.id))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .font(AppTypography.bodySmall)
                        .padding(AppLayout.spacingM)
                        .background(Color.white)
                        .overlay(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM).stroke(AppColors.border, lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM))
                }
                gateMessage
                HStack(spacing: AppLayout.spacingM) {
                    secondaryButton(title: appState.uiText("返回", "Back"), icon: "chevron.left") {
                        clearSensitiveInputs()
                        step = .offlinePassword
                        message = nil
                    }
                    primaryButton(title: appState.uiText("验证答案", "Verify answer"), icon: "questionmark.circle.fill", isDisabled: isBusy) {
                        verifyRecoveryAnswer()
                    }
                }
            }
        }
    }

    private func verifyAnswer() {
        if Int(answer.trimmingCharacters(in: .whitespacesAndNewlines)) == correctAnswer {
            message = nil
            step = .checking
            routeAfterMath()
        } else {
            message = appState.uiText("答案不正确，请重试。", "That answer is incorrect. Please try again.")
            regenerateMathQuestion()
        }
    }

    private func routeAfterMath() {
        switch gateService.preferredAccessMethod() {
        case .offlinePassword:
            clearSensitiveInputs()
            step = .offlinePassword
        case .deviceOwnerAuthentication:
            step = .devicePassword
            authenticateDevice()
        case .createOfflinePassword:
            clearSensitiveInputs()
            message = appState.uiText("当前设备未设置解锁密码，请先创建本机离线密码。", "This device has no unlock passcode set. Create a local offline password first.")
            step = .createOfflinePassword
        }
    }

    private func authenticateDevice() {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await gateService.authenticateDevice(localizedReason: appState.uiText("验证家长身份后进入家长区", "Verify parent access to open Parents"))
                await MainActor.run { onPass() }
            } catch {
                await MainActor.run {
                    message = errorMessage(for: error)
                    if (error as? ParentGateServiceError) == .deviceAuthenticationUnavailable {
                        clearSensitiveInputs()
                        step = .createOfflinePassword
                    }
                    isBusy = false
                }
            }
        }
    }

    private func verifyOfflinePassword() {
        do {
            try gateService.verifyOfflinePassword(password)
            clearSensitiveInputs()
            onPass()
        } catch {
            message = errorMessage(for: error)
        }
    }

    private func saveOfflinePassword(isReset: Bool) {
        guard password == confirmPassword else {
            message = appState.uiText("两次输入的密码不一致。", "The two passwords do not match.")
            return
        }
        do {
            let payload = ParentPasswordSetupPayload(password: password, answersByQuestionId: recoveryAnswers)
            if isReset {
                try gateService.resetOfflinePassword(payload)
            } else {
                try gateService.createOfflinePassword(payload)
            }
            clearSensitiveInputs()
            onPass()
        } catch {
            message = errorMessage(for: error)
        }
    }

    private func verifyRecoveryAnswer() {
        do {
            try gateService.verifyRecoveryAnswer(recoveryAnswers)
            clearSensitiveInputs()
            message = appState.uiText("验证通过，请设置新离线密码。", "Verified. Set a new offline password.")
            step = .resetOfflinePassword
        } catch {
            message = errorMessage(for: error)
        }
    }

    private func regenerateMathQuestion() {
        num1 = Int.random(in: 10...99)
        num2 = Int.random(in: 1...9)
        answer = ""
    }

    private func clearSensitiveInputs() {
        password = ""
        confirmPassword = ""
        recoveryAnswers = [:]
    }

    private func bindingForRecoveryAnswer(_ id: String) -> Binding<String> {
        Binding(
            get: { recoveryAnswers[id, default: ""] },
            set: { recoveryAnswers[id] = $0 }
        )
    }

    private func questionText(_ question: ParentRecoveryQuestion) -> String {
        appState.localizedText(
            zhHans: question.zhHans,
            english: question.english,
            japanese: question.japanese,
            korean: question.korean,
            spanish: question.spanish
        )
    }

    @ViewBuilder
    private var gateMessage: some View {
        if let message, !message.isEmpty {
            Text(message)
                .font(AppTypography.caption)
                .foregroundColor(AppColors.error)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity)
        }
    }

    private func passwordField(_ placeholder: String, text: Binding<String>) -> some View {
        HStack(spacing: AppLayout.spacingS) {
            Group {
                if isPasswordVisible {
                    TextField(placeholder, text: text)
                } else {
                    SecureField(placeholder, text: text)
                }
            }
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .font(AppTypography.bodySmall)

            Button {
                isPasswordVisible.toggle()
            } label: {
                Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                    .font(AppTypography.scaledFont(size: 16, weight: .semibold))
                    .foregroundColor(AppColors.textSecondary)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPasswordVisible ? appState.uiText("隐藏密码", "Hide password") : appState.uiText("显示密码", "Show password"))
        }
        .padding(.leading, AppLayout.spacingM)
        .padding(.trailing, AppLayout.spacingXS)
        .padding(.vertical, AppLayout.spacingXS)
        .background(Color.white)
        .overlay(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM).stroke(AppColors.border, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM))
    }

    private func primaryButton(title: String, icon: String, isDisabled: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: AppLayout.spacingS) {
                Image(systemName: icon)
                    .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                Text(title)
                    .font(AppTypography.button)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44)
            .padding(.horizontal, AppLayout.spacingM)
            .background(isDisabled ? AppColors.textTertiary : AppColors.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.62 : 1)
    }

    private func secondaryButton(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: AppLayout.spacingS) {
                Image(systemName: icon)
                    .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                Text(title)
                    .font(AppTypography.buttonSmall)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
            .foregroundColor(AppColors.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44)
            .padding(.horizontal, AppLayout.spacingM)
            .background(Color.white)
            .overlay(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL).stroke(AppColors.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func errorMessage(for error: Error) -> String {
        switch error as? ParentGateServiceError {
        case .deviceAuthenticationUnavailable:
            return appState.uiText("当前设备无法使用系统密码验证，请创建离线密码。", "Device passcode verification is unavailable. Create an offline password.")
        case .deviceAuthenticationFailed:
            return appState.uiText("设备密码验证未通过，请重试。", "Device passcode verification failed. Please try again.")
        case .passwordTooShort:
            return appState.uiText("密码至少需要 6 位。", "Password must be at least 6 characters.")
        case .passwordNeedsLettersAndNumbers:
            return appState.uiText("密码必须同时包含数字和字母。", "Password must include both letters and numbers.")
        case .weakPassword:
            return appState.uiText("这个密码过于常见，请更换更安全的组合。", "This password is too common. Use a stronger one.")
        case .recoveryAnswersIncomplete:
            return appState.uiText("请完整填写 3 个找回问题答案。", "Please answer all 3 recovery questions.")
        case let .invalidPassword(remaining):
            return appState.uiText("密码不正确，还可尝试 \(remaining) 次。", "Incorrect password. \(remaining) attempts remaining.")
        case let .locked(until), let .recoveryLocked(until):
            return appState.uiText("尝试次数过多，请在 \(lockTimeText(until)) 后重试。", "Too many attempts. Try again after \(lockTimeText(until)).")
        case .recoveryAnswerMismatch:
            return appState.uiText("答案不匹配。任意答对一个问题即可通过。", "No answer matched. Any one correct answer will pass.")
        case .offlinePasswordMissing:
            return appState.uiText("尚未创建离线密码。", "No offline password has been created.")
        case .keychainFailure:
            return appState.uiText("本机安全存储暂时不可用，请稍后重试。", "Secure local storage is temporarily unavailable. Please try again.")
        case .none:
            return appState.uiText("验证失败，请重试。", "Verification failed. Please try again.")
        }
    }

    private func lockTimeText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh") ? "zh_Hans_CN" : "en_US")
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }
}

private struct ParentGateNumberField: UIViewRepresentable {
    let placeholder: String
    @Binding var text: String
    @Binding var isFocused: Bool
    let fontSize: CGFloat

    func makeUIView(context: Context) -> UITextField {
        let textField = UITextField(frame: .zero)
        textField.delegate = context.coordinator
        textField.keyboardType = .numberPad
        textField.textAlignment = .center
        textField.textColor = UIColor(AppColors.textPrimary)
        textField.tintColor = UIColor(AppColors.primary)
        textField.adjustsFontForContentSizeCategory = true
        textField.autocorrectionType = .no
        textField.autocapitalizationType = .none
        textField.borderStyle = .none
        textField.backgroundColor = .clear
        textField.addTarget(context.coordinator, action: #selector(Coordinator.textDidChange(_:)), for: .editingChanged)
        return textField
    }

    func updateUIView(_ textField: UITextField, context: Context) {
        if textField.text != text {
            textField.text = text
        }
        textField.placeholder = placeholder
        textField.adjustsFontForContentSizeCategory = true
        textField.font = UIFont.systemFont(ofSize: fontSize, weight: .semibold)

        if isFocused && !textField.isFirstResponder {
            textField.becomeFirstResponder()
        } else if !isFocused && textField.isFirstResponder {
            textField.resignFirstResponder()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text, isFocused: $isFocused)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        @Binding private var text: String
        @Binding private var isFocused: Bool

        init(text: Binding<String>, isFocused: Binding<Bool>) {
            _text = text
            _isFocused = isFocused
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            isFocused = true
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            isFocused = false
        }

        func textField(_ textField: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            string.allSatisfy(\.isNumber)
        }

        @objc func textDidChange(_ textField: UITextField) {
            text = String((textField.text ?? "").filter(\.isNumber).prefix(3))
            if textField.text != text {
                textField.text = text
            }
        }
    }
}

struct ParentAnswerDisplay: View {
    let text: String
    let placeholder: String
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        Text(text.isEmpty ? placeholder : text)
            .font(AppTypography.scaledFont(size: 15, weight: .semibold, design: .rounded))
            .foregroundColor(text.isEmpty ? AppColors.textTertiary : AppColors.textPrimary)
            .multilineTextAlignment(.center)
            .frame(width: width, height: height)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                    .stroke(AppColors.border, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
            .accessibilityLabel(placeholder)
            .accessibilityValue(text)
    }
}

struct ParentNumberPad: View {
    @Binding var answer: String
    let clearTitle: String
    let keyFontSize: CGFloat
    let keyHeight: CGFloat
    let spacing: CGFloat
    let rowSpacing: CGFloat

    private var rows: [[String]] {
        [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], [clearTitle, "0", "⌫"]]
    }

    var body: some View {
        VStack(spacing: rowSpacing) {
            ForEach(rows, id: \.self) { row in
                HStack(spacing: spacing) {
                    ForEach(row, id: \.self) { key in
                        Button {
                            handleKey(key)
                        } label: {
                            Text(key)
                                .font(AppTypography.scaledFont(size: keyFontSize, weight: .semibold))
                                .foregroundColor(key == clearTitle ? AppColors.textSecondary : AppColors.textPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: keyHeight)
                                .background(key == clearTitle || key == "⌫" ? AppColors.background : Color.white)
                                .overlay(
                                    RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                                        .stroke(AppColors.border, lineWidth: 1)
                                )
                                .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                                .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func handleKey(_ key: String) {
        switch key {
        case clearTitle:
            answer = ""
        case "⌫":
            if !answer.isEmpty {
                answer.removeLast()
            }
        default:
            if answer.count < 3 {
                answer.append(key)
            }
        }
    }
}

struct ParentDashboardView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.openURL) private var openURL
    let children: [ChildProfile]
    let familyUsageSummary: FamilyUsageSummary?
    let currentChildUsageSummary: ChildUsageSummary?

    @State private var appleSignInRequestContext: AppleSignInRequestContext?
    @State private var versionCardTapCount = 0

    var body: some View {
        ScrollView {
            VStack(spacing: AppLayout.spacingXL) {
                childrenSection
                usageSection
                parentGuidanceSection
                settingsSection
                authSection
                if let error = appState.errorMessage, !error.isEmpty {
                    MainCard {
                        Text(error)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.error)
                            .frame(maxWidth: .infinity, alignment: .leading)
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
            .padding(.bottom, AppLayout.bottomNavigationContentInset)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
    }

    private var authSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("账号", "Account"))
                    .font(AppTypography.headline)
                if appState.authMode == .formalAccount {
                    Text(appState.uiText("已登录 Apple 账号", "Signed in with Apple"))
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .center)
                    HStack {
                        Spacer()
                        Button {
                            Task { await appState.signOut() }
                        } label: {
                            Text(appState.uiText("退出登录", "Sign out"))
                                .font(AppTypography.buttonSmall)
                                .foregroundColor(.white)
                                .frame(minWidth: 140, minHeight: 44)
                                .background(AppColors.error)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        Spacer()
                    }
                } else {
                    Text(appState.uiText("当前未登录", "Currently signed out"))
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textPrimary)
                    SignInWithAppleButton(.signIn, onRequest: configureAppleRequest, onCompletion: handleAppleSignInCompletion)
                        .signInWithAppleButtonStyle(.black)
                        .frame(minHeight: 52)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    Text(appState.uiText("当前仅支持 Apple 登录。删除账号时仍会要求临时输入邮箱接收验证码，但不会作为登录方式长期保存。", "Only Sign in with Apple is supported now. Account deletion still requires a one-time email verification step, but email is not kept as a sign-in method."))
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                }
            }
        }
    }

    private var usageSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack(alignment: .firstTextBaseline) {
                    Text(appState.uiText("使用时长", "Usage"))
                        .font(AppTypography.headline)
                    Spacer()
                    Text(appState.uiText("今天", "Today"))
                        .font(AppTypography.caption.weight(.semibold))
                        .foregroundColor(AppColors.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(AppColors.primary.opacity(0.1))
                        .clipShape(Capsule())
                }
                HStack {
                    Text(formatTime(Double(currentChildUsageSummary?.todayDurationSeconds ?? 0)))
                        .font(AppTypography.scaledFont(size: 24, weight: .bold))
                        .foregroundColor(AppColors.primary)
                    Spacer()
                    Image(systemName: "clock.fill")
                        .font(AppTypography.scaledFont(size: 40))
                        .foregroundColor(AppColors.primary.opacity(0.3))
                }
                Text(appState.uiText("统计对象：", "Child in scope: ") + currentChildName)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
                Text(appState.uiText("当前孩子近7天使用时长：", "Current child usage in the last 7 days: ") + formatTime(Double(currentChildUsageSummary?.weeklyDurationSeconds ?? 0)))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    private var childrenSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack {
                    Text(appState.uiText("孩子管理", "Children"))
                        .font(AppTypography.headline)
                    Spacer()
                    NavigationLink {
                        ManageChildrenView()
                    } label: {
                        Text(appState.uiText("查看全部", "View all"))
                            .font(AppTypography.caption)
                            .foregroundColor(AppColors.primary)
                    }
                }
                if children.isEmpty {
                    VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                        Text(appState.uiText("当前还没有孩子档案。先建一个档案，首页和复习会按孩子分别统计。", "No child profiles yet. Create one profile so Home and Review can track each child separately."))
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.textSecondary)
                        NavigationLink {
                            ManageChildrenView()
                        } label: {
                            HStack(spacing: AppLayout.spacingS) {
                                Image(systemName: "person.crop.circle.badge.plus")
                                Text(appState.uiText("添加孩子档案", "Add child profile"))
                            }
                            .font(AppTypography.bodySmall.weight(.semibold))
                            .foregroundColor(AppColors.primary)
                        }
                        .buttonStyle(.plain)
                    }
                } else {
                    ForEach(Array(children.prefix(3))) { child in
                        ChildRow(child: child, usageSummary: appState.childUsageSummaries[child.id])
                    }
                }
            }
        }
    }

    private var noticeSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack {
                    Text(appState.uiText("通知公告", "Notices"))
                        .font(AppTypography.headline)
                    Spacer()
                    NavigationLink {
                        AnnouncementListView()
                            .environmentObject(appState)
                    } label: {
                        Text(appState.uiText("查看全部", "View all"))
                            .font(AppTypography.caption)
                            .foregroundColor(AppColors.primary)
                    }
                }

                Text(appState.uiText(
                    "当前展示期内的公告统一放在这里查看；本模块只提供入口，不直接展开公告内容。",
                    "Active notices are collected here. This card only opens the notice center and does not expand notice content inline."
                ))
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var parentGuidanceSection: some View {
        let isPremium = appState.accountState?.entitlement.backendVerifiedPremiumActive == true
        return MainCard {
            ZStack(alignment: .topTrailing) {
                VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                    HStack(alignment: .top, spacing: AppLayout.spacingM) {
                        Image(systemName: "heart.text.square.fill")
                            .font(AppTypography.scaledFont(size: 28))
                            .foregroundColor(isPremium ? AppColors.error : AppColors.primary)
                        VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                            Text(appState.uiText("家长陪读节奏", "Parent reading rhythm"))
                                .font(AppTypography.headline)
                                .foregroundColor(AppColors.textPrimary)
                            Text(parentGuidanceCopy)
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }

                    LazyVGrid(columns: [GridItem(.flexible(minimum: 0), spacing: AppLayout.spacingM), GridItem(.flexible(minimum: 0), spacing: AppLayout.spacingM)], spacing: AppLayout.spacingS) {
                        guidanceChip(icon: "calendar", text: appState.uiText("每周看一次回顾", "Check weekly"), isPremium: isPremium)
                        guidanceChip(icon: "moon.zzz.fill", text: appState.uiText("每天复习更轻松", "Review daily"), isPremium: isPremium)
                    }
                }

                Text(appState.uiText("付费用户权益", "Premium benefit"))
                    .font(AppTypography.scaledFont(size: 10, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AppColors.error)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                    .rotationEffect(.degrees(12))
                    .offset(x: 8, y: -8)
            }
        }
    }

    private var parentGuidanceCopy: String {
        let weeklySeconds = familyUsageSummary?.weeklyDurationSeconds ?? 0
        if weeklySeconds <= 0 {
            return appState.uiText(
                "近7天还没有使用记录。建议先和孩子完成一张句卡，家长区会按最近记录展示陪读节奏。",
                "No usage recorded in the last 7 days yet. Try completing one card with your child; this area will show recent reading rhythm."
            )
        }
        return appState.uiText(
            "近7天已经有陪读记录。保持短时间、多次回访即可；本区只展示近期节奏，不做成绩承诺。",
            "There has been reading activity in the last 7 days. Short, repeated sessions are enough; this area shows recent rhythm, not guaranteed outcomes."
        )
    }

    private var currentChildName: String {
        appState.selectedChild.nickname.isEmpty ? appState.uiText("当前孩子", "Current child") : appState.selectedChild.nickname
    }

    private func guidanceChip(icon: String, text: String, isPremium: Bool) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: 12, weight: .semibold))
                .foregroundColor(isPremium ? AppColors.error : AppColors.textSecondary)
            Text(text)
                .font(AppTypography.caption)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .allowsTightening(true)
        }
        .foregroundColor(isPremium ? AppColors.error : AppColors.textSecondary)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(isPremium ? AppColors.error.opacity(0.10) : AppColors.primary.opacity(0.08))
        .clipShape(Capsule())
        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
    }

    private var settingsSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("设置", "Settings"))
                    .font(AppTypography.headline)
                settingsNoticeItem
                Divider()
                NavigationLink { PaywallView() } label: {
                    SettingRow(icon: "crown.fill", title: appState.uiText("订阅管理", "Subscription"), color: AppColors.accentYellow)
                }
                .buttonStyle(.plain)
                NavigationLink { WeeklyReportView() } label: {
                    SettingRow(icon: "chart.bar.fill", title: appState.uiText("本周陪读回顾", "Weekly report"), color: AppColors.info)
                }
                .buttonStyle(.plain)
                NavigationLink { LanguagePreferenceView() } label: {
                    SettingRow(icon: "globe", title: appState.uiText("语言偏好", "Language Preferences"), color: AppColors.primary)
                }
                .buttonStyle(.plain)
                NavigationLink { TextSizeSettingsView() } label: {
                    SettingRow(
                        icon: "textformat.size",
                        title: appState.uiText("字体大小", "Text size"),
                        subtitle: appState.uiText(
                            appState.textSizeOption.title + " · " + appState.textSizeOption.subtitle,
                            appState.textSizeOption.englishTitle + " · " + appState.textSizeOption.englishSubtitle
                        ),
                        color: AppColors.secondary
                    )
                }
                .buttonStyle(.plain)
                NavigationLink { SyncSettingsView() } label: {
                    SettingRow(icon: "icloud.fill", title: appState.uiText("云同步", "Cloud Sync"), color: AppColors.info)
                }
                .buttonStyle(.plain)
                NavigationLink { EntitlementRecordsView() } label: {
                    SettingRow(icon: "list.bullet.rectangle", title: appState.uiText("权益获取记录", "Entitlement records"), color: AppColors.secondary)
                }
                .buttonStyle(.plain)
                NavigationLink { CompensationCodeView() } label: {
                    SettingRow(
                        icon: "ticket.fill",
                        title: appState.uiText("权益补偿", "Compensation"),
                        subtitle: appState.uiText("输入后端发放的补偿码", "Redeem the backend-issued code"),
                        color: AppColors.secondary
                    )
                }
                .buttonStyle(.plain)
                versionUpdateCard
                if appState.authMode == .formalAccount {
                    NavigationLink { DeleteAccountView() } label: {
                        SettingRow(icon: "trash.fill", title: appState.uiText("删除账号", "Delete account"), color: AppColors.error)
                    }
                    .buttonStyle(.plain)
                }
                NavigationLink { SupportAndPrivacyView() } label: {
                    SettingRow(icon: "questionmark.circle.fill", title: appState.uiText("隐私与支持", "Privacy & support"), color: AppColors.textSecondary)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var settingsNoticeItem: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingS) {
            HStack(alignment: .firstTextBaseline) {
                Text(appState.uiText("通知公告", "Notices"))
                    .font(AppTypography.body.weight(.semibold))
                    .foregroundColor(AppColors.textPrimary)
                Spacer()
                NavigationLink {
                    AnnouncementListView()
                        .environmentObject(appState)
                } label: {
                    Text(appState.uiText("查看全部", "View all"))
                        .font(AppTypography.caption.weight(.semibold))
                        .foregroundColor(AppColors.primary)
                        .frame(minHeight: AppLayout.minimumTapTarget)
                }
                .buttonStyle(.plain)
            }

            Text(appState.uiText(
                "当前展示期内的公告统一放在这里查看；本模块只提供入口，不直接展开公告内容。",
                "Active notices are collected here. This entry opens the notice center and does not expand notice content inline."
            ))
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 2)
    }

    private var versionUpdateCard: some View {
        Group {
            if let policy = appState.appVersionPolicy {
                VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                    HStack(spacing: AppLayout.spacingM) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(AppTypography.scaledFont(size: 18))
                            .foregroundColor(AppColors.secondary)
                            .frame(width: 28)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(appState.localizedUpdateTitle(policy.title))
                                .font(AppTypography.body)
                                .foregroundColor(AppColors.textPrimary)
                        }
                        Spacer()
                    }

                    HStack(spacing: 8) {
                        Label(
                            appState.uiText("当前：\(versionDisplay(policy.currentVersion, build: policy.currentBuild))", "Current: \(versionDisplay(policy.currentVersion, build: policy.currentBuild))"),
                            systemImage: "iphone"
                        )
                    }
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)
                    if hasVersionDifference(policy) {
                        HStack(spacing: 8) {
                            Label(
                                appState.uiText("发现：\(versionDisplay(policy.latestVersion, build: policy.latestBuild))", "Found: \(versionDisplay(policy.latestVersion, build: policy.latestBuild))"),
                                systemImage: "sparkles"
                            )
                        }
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                    }
                    if hasVersionDifference(policy) {
                        HStack {
                            Spacer()
                            Button {
                                guard let url = policy.resolvedStoreURL else { return }
                                openURL(url)
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: "arrow.up.forward.app.fill")
                                        .font(AppTypography.scaledFont(size: 11, weight: .semibold))
                                    Text(appState.localizedUpdateCTA(policy.ctaText))
                                }
                                .font(AppTypography.caption)
                                .frame(width: 120)
                                .frame(minHeight: 26)
                                .padding(.vertical, 6)
                                .foregroundColor(.white)
                                .background(policy.hasConfiguredStoreURL ? AppColors.secondary : AppColors.textTertiary)
                                .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                                .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(!policy.hasConfiguredStoreURL)
                            Spacer()
                        }
                    }

                    if hasVersionDifference(policy) && !policy.hasConfiguredStoreURL {
                        Text(appState.uiText("更新入口已由后端控制；当前环境尚未配置 App Store 下载页。", "The update entry is backend-controlled; this environment has no App Store URL configured yet."))
                            .font(AppTypography.caption)
                            .foregroundColor(AppColors.textTertiary)
                    }
                }
                .frame(minHeight: 56)
                .padding(.vertical, 8)
                .contentShape(Rectangle())
                .onTapGesture {
                    handleVersionCardTap()
                }
            }
        }
    }

    private func handleVersionCardTap() {
        versionCardTapCount += 1
        guard versionCardTapCount >= 10 else { return }
        versionCardTapCount = 0
        Task {
            await appState.performFullEntitlementSync(reason: "parent_version_card_hidden_sync")
            appState.announcementOverlayRefreshToken = UUID()
        }
    }

    private func hasVersionDifference(_ policy: AppVersionPolicy) -> Bool {
        policy.currentVersion.trimmingCharacters(in: .whitespacesAndNewlines) != policy.latestVersion.trimmingCharacters(in: .whitespacesAndNewlines)
            || policy.currentBuild.trimmingCharacters(in: .whitespacesAndNewlines) != policy.latestBuild.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func versionDisplay(_ version: String, build: String) -> String {
        let trimmedVersion = version.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedBuild = build.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedVersion.isEmpty { return trimmedBuild }
        if trimmedBuild.isEmpty || trimmedBuild == trimmedVersion { return trimmedVersion }
        return "\(trimmedVersion) (\(trimmedBuild))"
    }

    private func recentUsageTitle(days: Int?) -> String {
        let safeDays = max(days ?? appState.bootstrap.usagePolicy?.safeRecentSummaryDays ?? 7, 1)
        return appState.uiText("最近 \(safeDays) 天：", "Last \(safeDays) days: ")
    }

    private func formatDailyUsage(_ items: [DailyUsagePoint]) -> String {
        guard !items.isEmpty else { return appState.uiText("暂无记录", "No usage yet") }
        return items.suffix(7).map { item in
            let suffix = String(item.usageDate.suffix(5))
            return "\(suffix) \(formatTime(Double(item.durationSeconds)))"
        }.joined(separator: " · ")
    }

    private func formatTime(_ timeInterval: TimeInterval) -> String {
        let hours = Int(timeInterval) / 3600
        let minutes = Int(timeInterval) / 60 % 60
        if hours > 0 { return appState.uiText("\(hours)小时\(minutes)分钟", "\(hours)h \(minutes)m") }
        return appState.uiText("\(minutes)分钟", "\(minutes)m")
    }

    private func configureAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        let context = AppleSignInRequestContext.make()
        appleSignInRequestContext = context
        request.requestedScopes = [.fullName]
        request.state = context.state
        request.nonce = context.requestNonce
    }

    private func handleAppleSignInCompletion(_ result: Result<ASAuthorization, Error>) {
        defer { appleSignInRequestContext = nil }
        switch result {
        case let .success(authorization):
            guard let requestContext = appleSignInRequestContext else {
                appState.errorMessage = appState.uiText("Apple 登录请求上下文丢失，请重试。", "Apple sign-in request context was lost. Please try again.")
                return
            }
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                appState.errorMessage = appState.uiText("Apple 登录返回了无法识别的凭据类型。", "Apple sign-in returned an unsupported credential type.")
                return
            }
            guard let authorizationCodeData = credential.authorizationCode,
                  let authorizationCode = String(data: authorizationCodeData, encoding: .utf8),
                  !authorizationCode.isEmpty else {
                appState.errorMessage = appState.uiText("Apple 没有返回可用的 authorizationCode。", "Apple did not return a usable authorizationCode.")
                return
            }
            guard let identityTokenData = credential.identityToken,
                  let identityToken = String(data: identityTokenData, encoding: .utf8),
                  !identityToken.isEmpty else {
                appState.errorMessage = appState.uiText("Apple 没有返回可用的 identityToken。", "Apple did not return a usable identityToken.")
                return
            }
            Task {
                _ = await appState.completeAppleSignIn(
                    authorizationCode: authorizationCode,
                    identityToken: identityToken,
                    state: requestContext.state,
                    nonce: requestContext.backendNonce,
                    givenName: credential.fullName?.normalizedGivenName,
                    familyName: credential.fullName?.normalizedFamilyName
                )
            }
        case let .failure(error):
            if isAppleSignInCancellation(error) {
                return
            }
            appState.errorMessage = error.localizedDescription
        }
    }

    private func isAppleSignInCancellation(_ error: Error) -> Bool {
        let nsError = error as NSError
        return nsError.domain == ASAuthorizationError.errorDomain && nsError.code == ASAuthorizationError.canceled.rawValue
    }
}

struct ChildRow: View {
    @EnvironmentObject var appState: AppState
    let child: ChildProfile
    let usageSummary: ChildUsageSummary?

    var body: some View {
        HStack(spacing: AppLayout.spacingM) {
            Text(child.avatarEmoji).font(AppTypography.scaledFont(size: 40))
            VStack(alignment: .leading, spacing: 4) {
                Text(child.nickname)
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textPrimary)
                Text(languageName(for: child.learningTrackCode) + " · " + stageName(for: child.ageBand))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                if let usageSummary {
                    Text(appState.uiText("今日使用时长：", "Today's usage: ") + formatTime(usageSummary.todayDurationSeconds))
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                }
            }
            Spacer()
        }
        .frame(minHeight: 56)
        .padding(.vertical, 8)
    }

    private func formatTime(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds / 60) % 60
        if hours > 0 { return appState.uiText("\(hours)小时\(minutes)分", "\(hours)h \(minutes)m") }
        return appState.uiText("\(minutes)分钟", "\(minutes)m")
    }

    private func languageName(for code: String) -> String {
        switch code {
        case "zh_to_en": return appState.uiText("英语", "English")
        case "en_to_zh": return appState.uiText("汉语", "Chinese")
        case "bilingual": return appState.uiText("双语", "Bilingual")
        default: return LearningTrack(rawValue: code)?.displayName ?? code
        }
    }

    private func stageName(for code: String) -> String {
        switch ChildAgeBand.normalizedCode(code) {
        case "3_4": return appState.uiText("入门启蒙", "Starter")
        case "5_6": return appState.uiText("基础起步", "Foundation")
        case "7_8": return appState.uiText("稳步提升", "Growing")
        case "9_plus": return appState.uiText("进阶巩固", "Advanced")
        case "advanced_expand": return appState.uiText("高阶拓展", "Advanced Expansion")
        default: return code
        }
    }
}

private struct NoticeSummaryRow: View {
    @EnvironmentObject var appState: AppState
    let announcement: Announcement

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: AppLayout.spacingS) {
                Text(appState.localizedAnnouncementTypeName(announcement.type))
                    .font(AppTypography.scaledFont(size: 11, weight: .semibold))
                    .foregroundColor(Color(hex: announcement.type.color))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color(hex: announcement.type.backgroundColor))
                    .clipShape(Capsule())
                Text(formatDate(announcement.publishedAt))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)
                Spacer()
            }
            Text(announcement.displayTitle)
                .font(AppTypography.body.weight(.semibold))
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)
            Text(announcement.displayContent)
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textSecondary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
}

private struct AnnouncementListView: View {
    @EnvironmentObject var appState: AppState
    @State private var announcements: [Announcement] = []
    @State private var localizedAnnouncements: [Announcement] = []
    @State private var isLoading = true
    @State private var isLocalizationReady = false

    private var displayedAnnouncements: [Announcement] {
        isLocalizationReady ? localizedAnnouncements : []
    }

    private var shouldTranslate: Bool {
        !AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh")
            && announcements.contains(where: {
                $0.localizedTitle?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
                && $0.localizedContent?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
                && $0.localizedActionText?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false
            })
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                if isLoading {
                    MainCard {
                        ProgressView()
                            .frame(maxWidth: .infinity, minHeight: 120, alignment: .center)
                    }
                } else if shouldTranslate && !isLocalizationReady && !announcements.isEmpty {
                    MainCard {
                        VStack(spacing: AppLayout.spacingS) {
                            ProgressView()
                            Text(appState.uiText("正在准备公告内容...", "Preparing notices..."))
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, minHeight: 120, alignment: .center)
                    }
                } else if displayedAnnouncements.isEmpty {
                    MainCard {
                        Text(appState.uiText("当前暂无展示中的通知公告。", "No active notices."))
                            .font(AppTypography.body)
                            .foregroundColor(AppColors.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    ForEach(displayedAnnouncements.sorted { $0.publishedAt > $1.publishedAt }) { announcement in
                        NavigationLink {
                            AnnouncementDetailView(announcement: announcement)
                                .environmentObject(appState)
                        } label: {
                            MainCard {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack(spacing: AppLayout.spacingS) {
                                        Text(appState.localizedAnnouncementTypeName(announcement.type))
                                            .font(AppTypography.scaledFont(size: 11, weight: .semibold))
                                            .foregroundColor(Color(hex: announcement.type.color))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(Color(hex: announcement.type.backgroundColor))
                                            .clipShape(Capsule())
                                        Text(formatDate(announcement.publishedAt))
                                            .font(AppTypography.caption)
                                            .foregroundColor(AppColors.textTertiary)
                                        Spacer()
                                    }
                                    Text(announcement.displayTitle)
                                        .font(AppTypography.body.weight(.semibold))
                                        .foregroundColor(AppColors.textPrimary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    Text(announcement.displayContent)
                                        .font(AppTypography.footnote)
                                        .foregroundColor(AppColors.textSecondary)
                                        .lineLimit(2)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding()
            .padding(.bottom, AppLayout.bottomNavigationContentInset)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadAnnouncements()
        }
        .onChange(of: appState.interfaceLocaleCode) { _, _ in
            Task { await loadAnnouncements() }
        }
    }

    private func loadAnnouncements() async {
        await MainActor.run {
            isLoading = true
            isLocalizationReady = false
        }
        let remote = await appState.fetchRecentAnnouncements(windowDays: 30, scene: nil, activeOnly: true)
        let source = remote.isEmpty ? appState.cachedRecentAnnouncements() : remote
        let mapped = source.map(mapAnnouncement).sorted { $0.publishedAt > $1.publishedAt }
        await MainActor.run {
            announcements = mapped
            localizedAnnouncements = shouldTranslate ? [] : mapped
            isLocalizationReady = !shouldTranslate
            isLoading = shouldTranslate && !mapped.isEmpty
        }
    }

    private func mapAnnouncement(_ remote: AppAnnouncement) -> Announcement {
        Announcement(
            id: remote.announcementUuid,
            title: remote.title,
            content: remote.content,
            type: AnnouncementType(rawValue: remote.type ?? "info") ?? .info,
            priority: remote.priority ?? 0,
            publishedAt: parseAnnouncementDate(remote.createdAt) ?? parseAnnouncementDate(remote.visibleStartAt) ?? parseAnnouncementDate(remote.updatedAt) ?? Date(),
            startDate: parseAnnouncementDate(remote.visibleStartAt) ?? Date(),
            endDate: parseAnnouncementDate(remote.visibleEndAt),
            actionUrl: remote.actionUrl,
            actionText: remote.actionText,
            isDismissible: remote.dismissible ?? true,
            localizedTitle: remote.localizedTitle,
            localizedContent: remote.localizedContent,
            localizedActionText: remote.localizedActionText
        )
    }

    private func parseAnnouncementDate(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        return ISO8601DateFormatter().date(from: raw)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
}

private struct AnnouncementDetailView: View {
    @EnvironmentObject var appState: AppState
    let announcement: Announcement

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(announcement.displayTitle)
                    .font(AppTypography.title2)
                    .foregroundColor(AppColors.textPrimary)
                HStack(spacing: 8) {
                    Text(appState.localizedAnnouncementTypeName(announcement.type))
                        .font(AppTypography.caption.weight(.semibold))
                        .foregroundColor(Color(hex: announcement.type.color))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(hex: announcement.type.backgroundColor))
                        .clipShape(Capsule())
                    Text(formatDate(announcement.publishedAt))
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                    Spacer()
                }
                Text(announcement.displayContent)
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding()
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle(appState.uiText("公告详情", "Notice details"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
}

struct SettingRow: View {
    let icon: String
    let title: String
    let subtitle: String?
    let color: Color

    init(icon: String, title: String, subtitle: String? = nil, color: Color) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.color = color
    }

    var body: some View {
        HStack(spacing: AppLayout.spacingM) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: 20))
                .foregroundColor(color)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(AppTypography.body)
                    .foregroundColor(AppColors.textPrimary)
                if let subtitle {
                    Text(subtitle)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                }
            }
            Spacer()
            if subtitle == nil {
                Image(systemName: "chevron.right")
                    .font(AppTypography.scaledFont(size: 14))
                    .foregroundColor(AppColors.textTertiary)
            }
        }
        .frame(minHeight: 56)
        .padding(.vertical, 8)
    }
}

struct TextSizeSettingsView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingL) {
                VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                    Text(appState.uiText("字体大小", "Text size"))
                        .font(AppTypography.headline)
                    Text(appState.uiText("根据阅读习惯选择合适的字体档位，所有页面会同步生效。", "Choose a font size that suits your reading habits. The whole app updates together."))
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                MainCard {
                    VStack(spacing: AppLayout.spacingS) {
                        ForEach(AppTextSizeOption.allCases) { option in
                            Button {
                                appState.textSizeOption = option
                            } label: {
                                HStack(spacing: AppLayout.spacingM) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(appState.uiText(option.title, option.englishTitle))
                                            .font(AppTypography.body)
                                            .foregroundColor(AppColors.textPrimary)
                                        Text(appState.uiText(option.subtitle, option.englishSubtitle))
                                            .font(AppTypography.caption)
                                            .foregroundColor(AppColors.textSecondary)
                                    }
                                    Spacer()
                                    if appState.textSizeOption == option {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(AppTypography.scaledFont(size: 22, weight: .semibold))
                                            .foregroundColor(AppColors.secondary)
                                    } else {
                                        Image(systemName: "circle")
                                            .font(AppTypography.scaledFont(size: 22, weight: .regular))
                                            .foregroundColor(AppColors.border)
                                    }
                                }
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: 56)
                                .padding(.horizontal, AppLayout.spacingM)
                                .background(appState.textSizeOption == option ? AppColors.secondary.opacity(0.08) : Color.white)
                                .overlay(
                                    RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous)
                                        .stroke(appState.textSizeOption == option ? AppColors.secondary : AppColors.border.opacity(0.7), lineWidth: 1)
                                )
                                .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Text(appState.uiText("建议年长用户优先使用“大”或“特大”，儿童可以按习惯选择“小”或“中”。", "Older users usually read better with Large or Extra Large; children can choose Small or Medium if preferred."))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding()
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    ParentAreaView().environmentObject(AppState())
}
