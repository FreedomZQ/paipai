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
    }

    private let gateService = ParentGateService.shared
    @State private var step: Step = .math
    @State private var answer = ""
    @State private var num1 = Int.random(in: 10...99)
    @State private var num2 = Int.random(in: 1...9)
    @State private var message: String?
    @State private var password = ""
    @State private var confirmPassword = ""
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

                    Text(appState.uiText("请先完成算题。答对后会使用当前设备密码验证；如果设备没有密码，请设置本机家长密码。", "Solve one quick math question first. Then use the current device passcode, or set a local parent password if this device has no passcode."))
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

                    Text(appState.uiText("先完成算题，再输入 iPhone 密码或本机家长密码。", "Solve the math question first, then enter the iPhone passcode or local parent password."))
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
                Text(appState.uiText("输入 iPhone 密码", "Enter iPhone passcode"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("系统会验证当前设备的解锁密码。当前设备有密码时，家长区使用这个设备密码进入。", "iOS will verify this device's unlock passcode. When this device has a passcode, Parents uses that passcode."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                gateMessage
                primaryButton(title: appState.uiText("输入 iPhone 密码", "Enter iPhone passcode"), icon: "lock.shield", isDisabled: isBusy) {
                    authenticateDevice()
                }
            }
        }
    }

    private func offlinePasswordCard(isWide: Bool) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("输入本机家长密码", "Enter local parent password"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("此设备曾在没有系统密码时设置过本机家长密码。之后即使设备又设置了 iPhone 密码，也仍使用这个密码进入家长区。", "This device already has a local parent password from when no system passcode was set. Even if an iPhone passcode is added later, this password is still used for Parents."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                passwordField(appState.uiText("本机家长密码", "Local parent password"), text: $password)
                gateMessage
                primaryButton(title: appState.uiText("进入家长区", "Open Parents"), icon: "checkmark.circle.fill", isDisabled: password.isEmpty || isBusy) {
                    verifyOfflinePassword()
                }
            }
        }
    }

    private func createPasswordCard(isWide: Bool, isReset: Bool) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("设置本机家长密码", "Set local parent password"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.uiText("当前设备没有 iPhone 解锁密码。请连续输入两次本机家长密码；之后家长区会一直使用这个密码，即使以后设备设置了 iPhone 密码也不切换。", "This device has no iPhone unlock passcode. Enter a local parent password twice; Parents will keep using this password even if an iPhone passcode is added later."))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                passwordField(appState.uiText("设置密码", "Set password"), text: $password)
                passwordField(appState.uiText("确认密码", "Confirm password"), text: $confirmPassword)
                gateMessage
                primaryButton(title: appState.uiText("保存并进入", "Save and continue"), icon: "key.fill", isDisabled: isBusy) {
                    saveOfflinePassword()
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
            message = appState.uiText("当前设备未设置 iPhone 密码，请先设置本机家长密码并确认一次。", "This device has no iPhone passcode set. Set and confirm a local parent password first.")
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
                    if (error as? ParentGateServiceError) == .deviceAuthenticationCancelled {
                        message = nil
                        step = .devicePassword
                        isBusy = false
                        return
                    }
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

    private func saveOfflinePassword() {
        guard password == confirmPassword else {
            message = appState.uiText("两次输入的密码不一致。", "The two passwords do not match.")
            return
        }
        do {
            try gateService.createOfflinePassword(ParentPasswordSetupPayload(password: password))
            clearSensitiveInputs()
            onPass()
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
            return appState.uiText("当前设备无法使用系统密码验证，请设置本机家长密码。", "Device passcode verification is unavailable. Set a local parent password.")
        case .deviceAuthenticationCancelled:
            return ""
        case .deviceAuthenticationFailed:
            return appState.uiText("设备密码验证未通过，请重试。", "Device passcode verification failed. Please try again.")
        case .passwordTooShort:
            return appState.uiText("密码至少需要 4 位。", "Password must be at least 4 characters.")
        case .passwordNeedsLettersAndNumbers:
            return appState.uiText("密码格式不符合要求。", "Password format is invalid.")
        case .weakPassword:
            return appState.uiText("这个密码过于常见，请更换更安全的组合。", "This password is too common. Use a stronger one.")
        case .recoveryAnswersIncomplete:
            return appState.uiText("请重新设置本机家长密码。", "Please set the local parent password again.")
        case let .invalidPassword(remaining):
            return appState.uiText("密码不正确，还可尝试 \(remaining) 次。", "Incorrect password. \(remaining) attempts remaining.")
        case let .locked(until), let .recoveryLocked(until):
            return appState.uiText("尝试次数过多，请在 \(lockTimeText(until)) 后重试。", "Too many attempts. Try again after \(lockTimeText(until)).")
        case .recoveryAnswerMismatch:
            return appState.uiText("验证未通过。", "Verification failed.")
        case .offlinePasswordMissing:
            return appState.uiText("尚未设置本机家长密码。", "No local parent password has been set.")
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
    let currentChildUsageSummary: ChildUsageSummary?

    @State private var versionCardTapCount = 0

    var body: some View {
        ScrollView {
            VStack(spacing: AppLayout.spacingXL) {
                childrenSection
                usageSection
                settingsSection
                if let error = appState.errorMessage, !error.isEmpty {
                    MainCard {
                        Text(error)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.error)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding()
            .padding(.bottom, AppLayout.bottomNavigationContentInset)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
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

    private var currentChildName: String {
        appState.selectedChild.nickname.isEmpty ? appState.uiText("当前孩子", "Current child") : appState.selectedChild.nickname
    }

    private var settingsSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("设置", "Settings"))
                    .font(AppTypography.headline)
                settingsNoticeItem
                Divider()
                SettingsNavigationLink {
                    PaywallView()
                } label: {
                    SettingRow(icon: "crown.fill", title: appState.uiText("订阅管理", "Subscription"), color: AppColors.accentYellow)
                }
                SettingsNavigationLink {
                    LanguagePreferenceView()
                } label: {
                    SettingRow(icon: "globe", title: appState.uiText("语言偏好", "Language Preferences"), color: AppColors.primary)
                }
                SettingsNavigationLink {
                    TextSizeSettingsView()
                } label: {
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
                SettingsNavigationLink {
                    SyncSettingsView()
                } label: {
                    SettingRow(icon: "icloud.fill", title: appState.uiText("云同步", "Cloud Sync"), color: AppColors.info)
                }
                SettingsNavigationLink {
                    EntitlementRecordsView()
                } label: {
                    SettingRow(icon: "list.bullet.rectangle", title: appState.uiText("权益获取记录", "Entitlement records"), color: AppColors.secondary)
                }
                SettingsNavigationLink {
                    CompensationCodeView()
                } label: {
                    SettingRow(
                        icon: "ticket.fill",
                        title: appState.uiText("权益补偿", "Compensation"),
                        subtitle: "输入发放的补偿码",
                        color: AppColors.secondary
                    )
                }
                versionUpdateCard
                SettingsNavigationLink {
                    SupportAndPrivacyView()
                } label: {
                    SettingRow(icon: "questionmark.circle.fill", title: appState.uiText("隐私与支持", "Privacy & support"), color: AppColors.textSecondary)
                }
            }
        }
    }

    private var settingsNoticeItem: some View {
        NavigationLink {
            AnnouncementListView()
                .environmentObject(appState)
        } label: {
            HStack(alignment: .center, spacing: AppLayout.spacingM) {
                Text(appState.uiText("通知公告", "Notices"))
                    .font(AppTypography.body.weight(.semibold))
                    .foregroundColor(AppColors.textPrimary)
                Spacer()
                Text(appState.uiText("查看全部", "View all"))
                    .font(AppTypography.caption.weight(.semibold))
                    .foregroundColor(AppColors.primary)
                Image(systemName: "chevron.right")
                    .font(AppTypography.scaledFont(size: 14))
                    .foregroundColor(AppColors.textTertiary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: 56)
            .padding(.vertical, 2)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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

    private func formatTime(_ timeInterval: TimeInterval) -> String {
        let hours = Int(timeInterval) / 3600
        let minutes = Int(timeInterval) / 60 % 60
        if hours > 0 { return appState.uiText("\(hours)小时\(minutes)分钟", "\(hours)h \(minutes)m") }
        return appState.uiText("\(minutes)分钟", "\(minutes)m")
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
            Image(systemName: "chevron.right")
                .font(AppTypography.scaledFont(size: 14))
                .foregroundColor(AppColors.textTertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 56)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }
}

private struct SettingsNavigationLink<Destination: View, Label: View>: View {
    let destination: () -> Destination
    let label: () -> Label

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            label()
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
