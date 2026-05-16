import SwiftUI
#if os(iOS)
import UIKit
#endif

struct ManageChildrenView: View {
    @EnvironmentObject var appState: AppState
    @State private var showAddSheet = false
    @State private var editingChild: ChildProfile?
    @State private var childPendingDeletion: ChildProfile?
    @State private var isCheckingEntitlement = false
    @State private var entitlementAlertMessage: String?
    let editingChildOnAppear: ChildProfile?

    init(editingChildOnAppear: ChildProfile? = nil) {
        self.editingChildOnAppear = editingChildOnAppear
    }

    private var currentChildProfileCount: Int {
        appState.children.count
    }

    private var entitlementChildCount: Int {
        appState.accountState?.entitlement.childCount ?? 0
    }

    private var childCount: Int {
        max(entitlementChildCount, currentChildProfileCount)
    }

    private var childLimit: Int {
        appState.accountState?.entitlement.childLimit ?? max(appState.children.count, 1)
    }

    private var remainingChildSlots: Int? {
        appState.accountState?.entitlement.remainingChildSlots
    }

    private var canAddChild: Bool {
        currentChildProfileCount < childLimit
    }

    private var childLimitHint: String {
        if let entitlement = appState.accountState?.entitlement {
            return appState.uiText(
                "已添加 \(currentChildProfileCount) 个，当前套餐“\(entitlement.planName)”最多可添加 \(entitlement.childLimit) 个孩子档案。",
                "Added \(currentChildProfileCount). The current plan \"\(entitlement.planName)\" allows up to \(entitlement.childLimit) child profiles."
            )
        }
        return appState.uiText(
            "已添加 \(currentChildProfileCount) 个孩子档案，点击添加时会实时校验当前账户权益。",
            "Added \(currentChildProfileCount) child profiles. Entitlement will be checked in real time when adding."
        )
    }

    private var limitReachedHint: String {
        if let entitlement = appState.accountState?.entitlement {
            return appState.uiText(
                "当前套餐“\(entitlement.planName)”的孩子名额已用完，如需更多档案请升级套餐或等待权益刷新。",
                "The child slots for plan \"\(entitlement.planName)\" are full. Upgrade the plan or wait for entitlements to refresh."
            )
        }
        return appState.uiText(
            "当前权益下孩子数量已达上限，如需更多档案请升级套餐。",
            "You have reached the child limit for the current plan. Please upgrade first."
        )
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text(appState.uiText("当前孩子数：", "Children: ") + "\(childCount) / \(childLimit)")
                        .font(AppTypography.headline)
                    Text(childLimitHint)
                        .font(AppTypography.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            Section(appState.uiText("当前孩子", "Children")) {
                if appState.children.isEmpty {
                    Text(appState.uiText("还没有孩子档案", "No child profiles yet"))
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(appState.children) { child in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top, spacing: 12) {
                                childHeader(child)
                                Spacer(minLength: 12)
                                VStack(alignment: .trailing, spacing: 8) {
                                    selectedChildMark(child)
                                    childActionButtons(child)
                                }
                            }
                        }
                        .frame(minHeight: 60)
                        .contentShape(Rectangle())
                        .simultaneousGesture(TapGesture().onEnded {
                            appState.selectedChild = child
                        })
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(appState.uiText("删除", "Delete"), role: .destructive) {
                                childPendingDeletion = child
                            }
                            Button(appState.uiText("编辑", "Edit")) {
                                presentEditingChild(child)
                            }
                            .tint(.blue)
                        }
                    }
                }
            }

            Section {
                Button {
                    Task { await handleAddChildTapped() }
                } label: {
                    if isCheckingEntitlement {
                        HStack(spacing: AppLayout.spacingS) {
                            ProgressView()
                            Text(appState.uiText("正在校验权益", "Checking entitlement"))
                        }
                    } else {
                        Label(appState.uiText("添加孩子档案", "Add child profile"), systemImage: "plus.circle.fill")
                    }
                }
                .disabled(isCheckingEntitlement)
                if !canAddChild {
                    Text(limitReachedHint)
                        .font(AppTypography.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(AppColors.background)
        .navigationTitle(appState.uiText("孩子档案", "Child Profiles"))
        .task {
            await appState.refreshParentData()
            if editingChild == nil, let editingChildOnAppear {
                editingChild = appState.children.first(where: { $0.id == editingChildOnAppear.id }) ?? editingChildOnAppear
            }
        }
        .sheet(isPresented: $showAddSheet) {
            ChildProfileSheet(
                title: appState.uiText("添加孩子", "Add Child"),
                submitTitle: appState.uiText("保存", "Save"),
                existingChild: nil
            )
                .presentationDetents([.height(560), .large])
                .presentationDragIndicator(.visible)
                .environmentObject(appState)
        }
        .sheet(item: $editingChild) { child in
            ChildProfileSheet(title: appState.uiText("编辑孩子", "Edit Child"), submitTitle: appState.uiText("保存修改", "Save Changes"), existingChild: child)
                .presentationDetents([.height(560), .large])
                .presentationDragIndicator(.visible)
                .environmentObject(appState)
        }
        .alert(appState.uiText("删除孩子档案？", "Delete child profile?"), isPresented: Binding(get: { childPendingDeletion != nil }, set: { if !$0 { childPendingDeletion = nil } })) {
            Button(appState.uiText("取消", "Cancel"), role: .cancel) { childPendingDeletion = nil }
            Button(appState.uiText("删除", "Delete"), role: .destructive) {
                guard let child = childPendingDeletion else { return }
                Task {
                    _ = await appState.deleteChildProfile(childId: child.id)
                    childPendingDeletion = nil
                }
            }
        } message: {
            Text(appState.uiText("删除后该孩子档案会从当前列表移除，并在同步后更新到后端。此操作需要二次确认。", "The profile will be removed from this list and synced to the backend. This action requires confirmation."))
        }
        .alert(appState.uiText("无法添加孩子档案", "Cannot add child profile"), isPresented: Binding(get: { entitlementAlertMessage != nil }, set: { if !$0 { entitlementAlertMessage = nil } })) {
            Button(appState.uiText("知道了", "Got it")) {
                entitlementAlertMessage = nil
            }
        } message: {
            Text(entitlementAlertMessage ?? "")
        }
    }

    private func handleAddChildTapped() async {
        isCheckingEntitlement = true
        await appState.refreshAccountState()
        await appState.refreshParentData()
        let latestLimit = appState.accountState?.entitlement.childLimit ?? max(appState.children.count + 1, 1)
        let latestCount = appState.children.count
        isCheckingEntitlement = false
        guard latestCount < latestLimit else {
            if let entitlement = appState.accountState?.entitlement {
                entitlementAlertMessage = appState.uiText(
                    "当前已添加 \(latestCount) 个孩子档案，套餐“\(entitlement.planName)”最多可添加 \(latestLimit) 个。如需添加更多孩子，请升级权益。",
                    "You have added \(latestCount) child profiles. Plan \"\(entitlement.planName)\" allows up to \(latestLimit). Please upgrade to add more children."
                )
            } else {
                entitlementAlertMessage = appState.uiText(
                    "当前已添加 \(latestCount) 个孩子档案，已达到可添加上限。如需添加更多孩子，请升级权益。",
                    "You have added \(latestCount) child profiles and reached the current limit. Please upgrade to add more children."
                )
            }
            return
        }
        showAddSheet = true
    }

    private func childHeader(_ child: ChildProfile) -> some View {
        HStack(spacing: 12) {
            Text(child.avatarEmoji)
                .font(AppTypography.title2)
            VStack(alignment: .leading, spacing: 4) {
                Text(child.nickname)
                    .font(AppTypography.headline)
                Text(languageName(for: child.learningTrackCode) + " · " + stageName(for: child.ageBand))
                    .font(AppTypography.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
        }
    }

    @ViewBuilder
    private func selectedChildMark(_ child: ChildProfile) -> some View {
        if appState.selectedChild.id == child.id {
            Label(appState.uiText("当前", "Current"), systemImage: "checkmark.circle.fill")
                .font(AppTypography.caption.weight(.semibold))
                .foregroundStyle(AppColors.primary)
        }
    }

    private func childActionButtons(_ child: ChildProfile) -> some View {
        HStack(spacing: 8) {
            Button {
                presentEditingChild(child)
            } label: {
                Image(systemName: "pencil")
                    .font(AppTypography.scaledFont(size: 15, weight: .semibold))
                    .foregroundColor(AppColors.primary)
                    .frame(width: 36, height: 36)
                    .background(Color(red: 0.91, green: 0.97, blue: 1.0))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)

            Button {
                childPendingDeletion = child
            } label: {
                Image(systemName: "trash")
                    .font(AppTypography.scaledFont(size: 15, weight: .semibold))
                    .foregroundColor(AppColors.error)
                    .frame(width: 36, height: 36)
                    .background(Color(red: 1.0, green: 0.91, blue: 0.91))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }

    private func presentEditingChild(_ child: ChildProfile) {
        editingChild = appState.children.first(where: { $0.id == child.id }) ?? child
    }

    private func learningTrackTitle(_ code: String) -> String {
        appState.bootstrap.learningTracks.first(where: { $0.code == code })?.label
            ?? LearningTrack(rawValue: code)?.displayName
            ?? code
    }

    private func languageName(for code: String) -> String {
        switch code {
        case "zh_to_en": return appState.uiText("英语", "English")
        case "en_to_zh": return appState.uiText("中文", "Chinese")
        case "bilingual": return appState.uiText("双语", "Bilingual")
        default: return appState.learningTrackDisplayName(code)
        }
    }

    private func languageIcon(for learningTrackCode: String) -> String {
        let target = learningTrackCode
            .replacingOccurrences(of: "-", with: "_")
            .lowercased()
            .components(separatedBy: "_to_")
            .last ?? learningTrackCode.lowercased()
        if target.hasPrefix("zh") { return "🇨🇳" }
        if target.hasPrefix("en") { return "🇺🇸" }
        if target.hasPrefix("ja") { return "🇯🇵" }
        if target.hasPrefix("ko") { return "🇰🇷" }
        if target.hasPrefix("es") { return "🇪🇸" }
        if target.hasPrefix("fr") { return "🇫🇷" }
        if target.hasPrefix("de") { return "🇩🇪" }
        if target.hasPrefix("it") { return "🇮🇹" }
        if target.hasPrefix("pt") { return "🇵🇹" }
        return "🌐"
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

    private func formatTime(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds / 60) % 60
        if hours > 0 { return appState.uiText("\(hours)小时\(minutes)分", "\(hours)h \(minutes)m") }
        return appState.uiText("\(minutes)分钟", "\(minutes)m")
    }

    private func recentUsageText(_ points: [DailyUsagePoint]) -> String {
        let days = max(appState.bootstrap.usagePolicy?.safeRecentSummaryDays ?? points.count, 1)
        let ordered = points.suffix(days)
        let segments = ordered.map { point in
            let suffix = String(point.usageDate.suffix(5))
            return "\(suffix): \(formatTime(point.durationSeconds))"
        }
        return appState.uiText("近 \(days) 天：", "Last \(days) days: ") + segments.joined(separator: " · ")
    }

    private func usageChip(_ title: String, _ value: String) -> some View {
        HStack(spacing: 4) {
            Text(title)
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(AppTypography.caption.weight(.semibold))
        }
        .frame(maxWidth: .infinity, minHeight: 32, alignment: .leading)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(AppColors.primary.opacity(0.1))
        .clipShape(Capsule())
    }
}

private struct PlainNicknameField: View {
    let placeholder: String
    @Binding var text: String

    var body: some View {
        #if os(iOS)
        PlainNicknameUIKitTextField(placeholder: placeholder, text: $text)
        #else
        TextField(placeholder, text: $text)
            .textFieldStyle(.plain)
            .font(AppTypography.scaledFont(size: 16))
            .padding(.horizontal, 16)
        #endif
    }
}

#if os(iOS)
private struct PlainNicknameUIKitTextField: UIViewRepresentable {
    let placeholder: String
    @Binding var text: String

    func makeUIView(context: Context) -> UITextField {
        let textField = UITextField(frame: .zero)
        textField.placeholder = placeholder
        textField.text = text
        textField.delegate = context.coordinator
        textField.borderStyle = .none
        textField.backgroundColor = .clear
        textField.adjustsFontForContentSizeCategory = true
        textField.font = .preferredFont(forTextStyle: .body)
        textField.textColor = UIColor.label
        textField.keyboardType = .default
        textField.textContentType = .nickname
        textField.autocapitalizationType = .words
        textField.autocorrectionType = .yes
        textField.returnKeyType = .done
        textField.inputAccessoryView = nil
        textField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 16, height: 1))
        textField.leftViewMode = .always
        textField.rightView = UIView(frame: CGRect(x: 0, y: 0, width: 16, height: 1))
        textField.rightViewMode = .always
        textField.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textField.setContentHuggingPriority(.defaultLow, for: .horizontal)
        textField.addTarget(context.coordinator, action: #selector(Coordinator.textDidChange(_:)), for: .editingChanged)
        return textField
    }

    func updateUIView(_ uiView: UITextField, context: Context) {
        if uiView.text != text {
            uiView.text = text
        }
        uiView.placeholder = placeholder
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        @Binding private var text: String

        init(text: Binding<String>) {
            _text = text
        }

        @objc func textDidChange(_ sender: UITextField) {
            text = sender.text ?? ""
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            textField.resignFirstResponder()
            return true
        }
    }
}
#endif

private struct ChildProfileSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState

    let title: String
    let submitTitle: String
    let existingChild: ChildProfile?

    @State private var nickname: String
    @State private var ageBand: String
    @State private var learningTrackCode: String
    @State private var showSaveConfirmation = false
    @State private var showLanguagePackConfirmation = false

    init(title: String, submitTitle: String, existingChild: ChildProfile?) {
        self.title = title
        self.submitTitle = submitTitle
        self.existingChild = existingChild
        _nickname = State(initialValue: existingChild?.nickname ?? "")
        _ageBand = State(initialValue: existingChild?.ageBand ?? ChildAgeBand.defaultCode)
        _learningTrackCode = State(initialValue: existingChild?.learningTrackCode ?? "zh_to_en")
    }

    private var canSubmit: Bool {
        !nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !learningTrackCode.isEmpty && !ageBand.isEmpty
    }

    private var languageOptions: [(code: String, icon: String, title: String)] {
        // 当前版本只开放中文、英文两个学习语言；日语、西班牙语等后续语种保留在能力映射中，不在这里展示。
        [
            ("en_to_zh", languageIcon(for: "en_to_zh"), appState.uiText("中文", "Chinese")),
            ("zh_to_en", languageIcon(for: "zh_to_en"), appState.uiText("英文", "English"))
        ]
    }

    private func languageIcon(for learningTrackCode: String) -> String {
        let target = learningTrackCode
            .replacingOccurrences(of: "-", with: "_")
            .lowercased()
            .components(separatedBy: "_to_")
            .last ?? learningTrackCode.lowercased()
        if target.hasPrefix("zh") { return "🇨🇳" }
        if target.hasPrefix("en") { return "🇺🇸" }
        if target.hasPrefix("ja") { return "🇯🇵" }
        if target.hasPrefix("ko") { return "🇰🇷" }
        if target.hasPrefix("es") { return "🇪🇸" }
        if target.hasPrefix("fr") { return "🇫🇷" }
        if target.hasPrefix("de") { return "🇩🇪" }
        if target.hasPrefix("it") { return "🇮🇹" }
        if target.hasPrefix("pt") { return "🇵🇹" }
        return "🌐"
    }

    private var stageOptions: [(code: String, title: String)] {
        [
            ("3_4", appState.uiText("入门启蒙", "Starter")),
            ("5_6", appState.uiText("基础起步", "Foundation")),
            ("7_8", appState.uiText("稳步提升", "Growing")),
            ("9_plus", appState.uiText("进阶巩固", "Advanced")),
            ("advanced_expand", appState.uiText("高阶拓展", "Advanced Expansion"))
        ]
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                ScrollView {
                    VStack(spacing: AppLayout.spacingL) {
                        Text(title)
                            .font(AppTypography.headline)
                            .foregroundColor(AppColors.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .center)

                        VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                            Text(appState.uiText("孩子昵称", "Child nickname"))
                                .font(AppTypography.caption)
                                .font(AppTypography.caption.weight(.medium))
                                .foregroundColor(AppColors.textSecondary)
                            PlainNicknameField(
                                placeholder: appState.uiText("请输入孩子昵称", "Enter child nickname"),
                                text: $nickname
                            )
                            .frame(height: 48)
                            .background(Color.white)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(AppColors.border, lineWidth: 1)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }

                        optionGroup(title: appState.uiText("学习语种", "Learning language")) {
                            ForEach(languageOptions, id: \.code) { option in
                                optionPill(title: option.title, isSelected: learningTrackCode == option.code) {
                                    learningTrackCode = option.code
                                }
                            }
                        }

                        optionGroup(title: appState.uiText("学习阶段", "Learning stage")) {
                            ForEach(stageOptions, id: \.code) { option in
                                optionPill(title: option.title, isSelected: ageBand == option.code) {
                                    ageBand = option.code
                                }
                            }
                        }

                        HStack(spacing: 12) {
                            Button {
                                if shouldConfirmLanguagePackDownload {
                                    showLanguagePackConfirmation = true
                                } else if existingChild == nil {
                                    Task { await saveProfile() }
                                } else {
                                    showSaveConfirmation = true
                                }
                            } label: {
                                Text(submitTitle)
                                    .font(AppTypography.scaledFont(size: 15, weight: .semibold))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 44)
                                    .background(canSubmit ? AnyShapeStyle(AppGradients.primary) : AnyShapeStyle(AppColors.textTertiary))
                                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(!canSubmit)

                            Button {
                                dismiss()
                            } label: {
                                Text(appState.uiText("取消", "Cancel"))
                                    .font(AppTypography.scaledFont(size: 15, weight: .semibold))
                                    .foregroundColor(AppColors.textPrimary)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 44)
                                    .background(AppColors.border)
                                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(24)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .shadow(color: Color.black.opacity(0.15), radius: 20, x: 0, y: 4)
                    .padding(.horizontal, AppLayout.paddingScreen)
                    .padding(.vertical, AppLayout.spacingL)
                    .adaptiveContentFrame(maxWidth: 400)
                }
            }
            .appScrollDismissesKeyboardInteractively()
            .alert(appState.uiText("确认保存修改？", "Save changes?"), isPresented: $showSaveConfirmation) {
                Button(appState.uiText("取消", "Cancel"), role: .cancel) {}
                Button(appState.uiText("保存", "Save")) {
                    Task { await saveProfile() }
                }
            } message: {
                Text(appState.uiText("将更新孩子姓名、学习语种和学习阶段。", "This will update the child's name, language, and learning stage."))
            }
            .alert(appState.uiText("下载学习语言包", "Download learning language pack"), isPresented: $showLanguagePackConfirmation) {
                Button(appState.uiText("取消", "Cancel"), role: .cancel) {}
                Button(appState.uiText("确认", "Confirm")) {
                    Task { await saveProfile() }
                }
            } message: {
                Text(appState.uiText("保存后将自动下载\(selectedLanguageDisplayName)学习语言包，该操作可能产生流量费用，是否继续？", "After saving, the \(selectedLanguageDisplayName) learning language pack will be downloaded automatically. Data charges may apply. Continue?"))
            }
        }
    }

    private var shouldConfirmLanguagePackDownload: Bool {
        if let existingChild, existingChild.learningTrackCode == learningTrackCode {
            return false
        }
        return !appState.isLanguagePackDownloaded(for: learningTrackCode)
    }

    private var selectedLanguageDisplayName: String {
        languageOptions.first(where: { $0.code == learningTrackCode })?.title ?? appState.learningTrackDisplayName(learningTrackCode)
    }

    private func saveProfile() async {
        let trimmed = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        let success: Bool
        if let existingChild {
            success = await appState.updateChildProfile(childId: existingChild.id, nickname: trimmed, ageBand: ageBand, learningTrackCode: learningTrackCode)
        } else {
            success = await appState.createChildProfile(nickname: trimmed, ageBand: ageBand, learningTrackCode: learningTrackCode)
        }
        if success { dismiss() }
    }

    private func optionGroup<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingS) {
            Text(title)
                .font(AppTypography.caption)
                .font(AppTypography.caption.weight(.medium))
                .foregroundColor(AppColors.textSecondary)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                content()
            }
        }
    }

    private func optionPill(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(AppTypography.scaledFont(size: 12, weight: .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .foregroundColor(isSelected ? .white : AppColors.textPrimary)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 6)
                .padding(.vertical, 8)
                .background(isSelected ? AppColors.primary : Color.white)
                .overlay(
                    Capsule()
                        .stroke(isSelected ? AppColors.primary : AppColors.border, lineWidth: 1)
                )
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
