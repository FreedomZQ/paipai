import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject var appState: AppState
    @State private var currentPage = 0
    @State private var showPrivacyConsent = false

    private var pages: [OnboardingPage] {
        [
            OnboardingPage(
                icon: "🌍",
                title: appState.uiText("先选择界面语言", "Choose your display language first"),
                description: appState.uiText(
                    "后续引导页、家长区和主要功能页会优先使用这里选择的展示语言。",
                    "Onboarding, parent settings, and main app screens will use this selected display language."
                ),
                kind: .language
            ),
            OnboardingPage(
                icon: "📚",
                title: appState.uiText("让纸上的一句话，变成今天会读的一句话", "Turn one printed sentence into one your child can read today"),
                description: appState.uiText("一拍一听，一记一复习。适合家长陪孩子把绘本、教材和日常短句慢慢读顺。", "Capture, listen, save, and review. Designed for parents helping children practice picture books, textbooks, and everyday sentences."),
                kind: .features([
                    OnboardingFeature(icon: "📷", title: appState.uiText("拍一句", "Capture"), description: appState.uiText("拍摄绘本或教材中的一句/一小段，自动识别文字。", "Capture a sentence or short paragraph and recognize the text automatically.")),
                    OnboardingFeature(icon: "🔊", title: appState.uiText("听一句", "Listen"), description: appState.uiText("朗读原文与译文，帮助孩子跟读。", "Hear the original and translation for follow-along practice.")),
                    OnboardingFeature(icon: "✨", title: appState.uiText("复习一句", "Review"), description: appState.uiText("把常用句子存成句卡，按节奏复习。", "Save useful sentences as review cards and revisit them steadily."))
                ])
            ),
            OnboardingPage(
                icon: "🔒",
                title: appState.uiText("我们重视您和孩子的隐私", "We value your and your child's privacy"),
                description: appState.uiText(
                    "遵循「最小必要」原则，保护您和孩子的个人信息。",
                    "We follow the principle of minimal necessity to protect personal information for you and your child."
                ),
                kind: .features([
                    OnboardingFeature(icon: "📋", title: appState.uiText("最小必要", "Minimal collection"), description: appState.uiText("默认本地使用，不上传孩子图片、原文、录音或诊断日志。", "Local use is the default; child images, text, audio, and diagnostics are not uploaded.")),
                    OnboardingFeature(icon: "📱", title: appState.uiText("仅当前设备", "This device only"), description: appState.uiText("学习数据和本机积分默认保存在此设备，不同步到开发者服务器。", "Learning data and local credits stay on this device by default and are not synced to a developer server.")),
                    OnboardingFeature(icon: "🛡️", title: appState.uiText("无广告跟踪", "No ad tracking"), description: appState.uiText("不接入第三方广告、第三方分析、IDFA 或跨 App 跟踪。", "No third-party ads, analytics, IDFA, or cross-app tracking are integrated.")),
                    OnboardingFeature(icon: "⚙️", title: appState.uiText("自主可控", "Full control"), description: appState.uiText("家长可在本机删除学习数据，或重置本机积分钱包。", "Parents can delete local learning data or reset the local credit wallet on device."))
                ])
            ),
            OnboardingPage(
                icon: "👨‍👩‍👧‍👦",
                title: appState.uiText("请和孩子一起使用", "Use together with your child"),
                description: appState.uiText(
                    "这是家长主导的陪读工具，共同参与效果更好。",
                    "This is a parent-led read-along tool—participation together yields the best results."
                ),
                kind: .features([
                    OnboardingFeature(icon: "👨‍💻", title: appState.uiText("家长主导", "Parent-led"), description: appState.uiText("设置、购买、恢复和数据重置需要家长进入。", "Settings, purchases, restore, and data reset require parent access.")),
                    OnboardingFeature(icon: "👧", title: appState.uiText("孩子使用", "Child use"), description: appState.uiText("孩子主要使用学习练习相关功能。", "Children mainly use learning and practice features.")),
                    OnboardingFeature(icon: "❤️", title: appState.uiText("亲子互动", "Family interaction"), description: appState.uiText("家长可以参与孩子的学习过程，共同进步。", "Parents can participate in the child's learning journey and grow together."))
                ])
            ),
            OnboardingPage(
                icon: "🔒",
                title: appState.uiText("低打扰、低收集，本地优先", "Low interruption, low collection, local-first"),
                description: appState.uiText(
                    "相机/相册只用于你主动拍摄或选择内容；识字、朗读和学习内容默认在设备本地处理。",
                    "Camera/photo access is used only when you capture or select content. OCR, read-aloud, and learning content are processed locally by default."
                ),
                kind: .features([
                    OnboardingFeature(icon: "📱", title: appState.uiText("多设备适配", "Adaptive layout"), description: appState.uiText("页面按 iPhone、iPad 和 Mac 窗口宽度自动收拢或居中展示。", "Screens adapt to iPhone, iPad, and Mac window widths.")),
                    OnboardingFeature(icon: "🛡️", title: appState.uiText("家长门", "Parental gate"), description: appState.uiText("购买、恢复、外链和数据重置都需要家长验证。", "Purchases, restore, external links, and data reset require parent verification.")),
                    OnboardingFeature(icon: "💳", title: appState.uiText("本机积分", "Local credits"), description: appState.uiText("购买由 Apple 确认，消耗型本机积分不支持跨设备自动恢复。", "Apple confirms purchases; consumable local credits do not restore automatically across devices."))
                ])
            )
        ]
    }

    var body: some View {
        GeometryReader { proxy in
            let isWide = proxy.size.width >= 700

            ZStack {
                OnboardingBackground()

                TabView(selection: $currentPage) {
                    ForEach(pages.indices, id: \.self) { index in
                        OnboardingPageView(
                            page: pages[index],
                            availableWidth: proxy.size.width,
                            bottomInset: bottomControlInset(for: proxy.size.width)
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.22), value: currentPage)

                VStack {
                    Spacer()
                    VStack(spacing: isWide ? 10 : 8) {
                        pageDots
                        bottomButtons

                        if currentPage == pages.count - 1 {
                            HStack(spacing: 3) {
                                Image(systemName: "apple.logo")
                                    .font(AppTypography.scaledFont(size: isWide ? 12 : 10, weight: .semibold))
                                Text(appState.uiText("完成告知确认后即可本地使用，无需创建账号", "After confirming the notices, you can use the app locally without creating an account"))
                                    .font(AppTypography.scaledFont(size: isWide ? 11 : 10, weight: .medium))
                                    .lineLimit(2)
                            }
                            .foregroundColor(AppColors.textSecondary)
                            .multilineTextAlignment(.center)
                        }
                    }
                    .padding(.horizontal, isWide ? 44 : 20)
                    .padding(.top, isWide ? 10 : 8)
                    .padding(.bottom, max(proxy.safeAreaInsets.bottom, isWide ? 10 : 6))
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial)
                }
                .ignoresSafeArea(edges: .bottom)
            }
        }
        .sheet(isPresented: $showPrivacyConsent) {
            PrivacyConsentView {
                appState.hasCompletedOnboarding = true
                appState.announcementOverlayRefreshToken = UUID()
            }
            .environmentObject(appState)
        }
        .task {
            await appState.bootstrapIfNeeded()
        }
    }



    private var pageDots: some View {
        HStack(spacing: 8) {
            ForEach(0..<pages.count, id: \.self) { index in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        currentPage = index
                    }
                } label: {
                    Capsule()
                        .fill(currentPage == index ? AppColors.primary : AppColors.border)
                        .frame(width: currentPage == index ? 20 : 10, height: 10)
                        .frame(minWidth: 28, minHeight: 28)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .animation(.easeInOut(duration: 0.2), value: currentPage)
                .accessibilityLabel(appState.uiText("第\(index + 1)页", "Page \(index + 1)"))
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(appState.uiText("引导进度：第\(currentPage + 1)页，共\(pages.count)页", "Onboarding progress: page \(currentPage + 1) of \(pages.count)"))
    }

    @ViewBuilder
    private var bottomButtons: some View {
        HStack(spacing: 8) {
            if currentPage > 0 {
                Button(action: retreat) {
                    Text(appState.uiText("上一步", "Back"))
                        .font(AppTypography.scaledFont(size: 14, weight: .semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .frame(minWidth: 56, minHeight: 38)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .background(Color.white.opacity(0.92))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                                .stroke(AppColors.border.opacity(0.75), lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                        .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .transition(.opacity.combined(with: .scale(scale: 0.96)))
            }

            Button(action: advance) {
                HStack(spacing: 4) {
                    Text(currentPage < pages.count - 1 ? appState.uiText("下一步", "Next") : appState.uiText("查看并同意", "Review & Agree"))
                        .lineLimit(1)
                    Image(systemName: currentPage < pages.count - 1 ? "arrow.right" : "checkmark")
                        .font(AppTypography.scaledFont(size: 11, weight: .bold))
                }
                .font(AppTypography.scaledFont(size: 15, weight: .semibold))
                .foregroundColor(.white)
                .frame(minHeight: 40)
                .padding(.vertical, 8)
                .padding(.horizontal, 14)
                .background(AppGradients.primary)
                .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .animation(.easeInOut(duration: 0.18), value: currentPage)
    }

    private func advance() {
        if currentPage < pages.count - 1 {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                currentPage += 1
            }
        } else {
            showPrivacyConsent = true
        }
    }

    private func retreat() {
        guard currentPage > 0 else { return }
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            currentPage -= 1
        }
    }

    private func horizontalPadding(for width: CGFloat) -> CGFloat {
        width >= 700 ? 48 : AppLayout.paddingScreen
    }

    private func contentMaxWidth(for width: CGFloat) -> CGFloat {
        width >= 900 ? 760 : 560
    }

    private func bottomControlInset(for width: CGFloat) -> CGFloat {
        let isWide = width >= 700
        return isWide ? 84 : 72
    }
}

private struct OnboardingBackground: View {
    var body: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()
            LinearGradient(
                colors: [AppColors.gradientStart.opacity(0.28), AppColors.gradientEnd.opacity(0.16), Color.white.opacity(0.2)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            Circle()
                .fill(AppColors.accentYellow.opacity(0.24))
                .frame(width: 280, height: 280)
                .blur(radius: 28)
                .offset(x: -160, y: -260)
            Circle()
                .fill(AppColors.accentMint.opacity(0.24))
                .frame(width: 320, height: 320)
                .blur(radius: 32)
                .offset(x: 180, y: 260)
        }
    }
}

struct OnboardingPage {
    enum Kind {
        case language
        case features([OnboardingFeature])
        case steps([String])
    }

    let icon: String
    let title: String
    let description: String
    let kind: Kind
}

struct OnboardingFeature: Identifiable {
    let id = UUID()
    let icon: String
    let title: String
    let description: String
}

struct OnboardingPageView: View {
    @EnvironmentObject var appState: AppState
    let page: OnboardingPage
    let availableWidth: CGFloat
    var bottomInset: CGFloat = 0

    private var isWide: Bool { availableWidth >= 700 }
    private var titleFontSize: CGFloat { isWide ? 34 : 28 }
    private var descriptionFontSize: CGFloat { isWide ? 24 : 20 }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(showsIndicators: true) {
                VStack(spacing: isWide ? 14 : 10) {
                    Text(page.icon)
                        .font(AppTypography.scaledFont(size: isWide ? 56 : 40))
                        .accessibilityHidden(true)

                    Text(page.title)
                        .font(AppTypography.scaledFont(size: titleFontSize, weight: .semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(1)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(page.description)
                        .font(AppTypography.scaledFont(size: descriptionFontSize, weight: .regular))
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(isWide ? 4 : 3)
                        .fixedSize(horizontal: false, vertical: true)

                    switch page.kind {
                    case .language:
                        LanguageSelectionCard()
                    case let .features(features):
                        OnboardingFeatureGrid(features: features, isWide: isWide)
                    case let .steps(steps):
                        OnboardingStepsCard(steps: steps)
                    }
                }
                .frame(maxWidth: isWide ? 720 : 480)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, availableWidth >= 700 ? 48 : 20)
                .padding(.top, isWide ? 20 : 12)
                .padding(.bottom, bottomInset + 16)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

private struct OnboardingLanguageOption: Identifiable {
    let locale: String
    let title: String
    let subtitle: String

    var id: String { locale }
}

private struct LanguageSelectionCard: View {
    @EnvironmentObject var appState: AppState

    private let options = [
        OnboardingLanguageOption(locale: "zh-Hans", title: "汉语", subtitle: "简体中文"),
        OnboardingLanguageOption(locale: "en", title: "English", subtitle: "English")
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(options) { option in
                let selected = isSelected(option)
                Button {
                    appState.setOnboardingInterfaceLocale(option.locale)
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(option.title)
                                .font(AppTypography.headline)
                            Text(option.subtitle)
                                .font(AppTypography.caption)
                                .foregroundColor(AppColors.textSecondary)
                        }
                        Spacer()
                        Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                            .font(AppTypography.scaledFont(size: 22, weight: .semibold))
                            .foregroundColor(selected ? AppColors.secondary : AppColors.border)
                    }
                    .foregroundColor(AppColors.textPrimary)
                    .frame(minHeight: 58)
                    .padding(16)
                    .background(selected ? AppColors.secondary.opacity(0.12) : Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous)
                            .stroke(selected ? AppColors.secondary : AppColors.border.opacity(0.6), lineWidth: 1.2)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
                }
                .buttonStyle(.plain)
            }

            Text(appState.uiText("非中文展示语言会先使用英文界面兜底，通知公告会按所选语言实时翻译展示。", "For non-Chinese display languages, the app UI uses English fallback while notices are translated into the selected language."))
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
        }
        .padding(16)
        .background(Color.white.opacity(0.92))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
        .shadow(color: Color.black.opacity(0.08), radius: 18, x: 0, y: 8)
    }

    private func isSelected(_ option: OnboardingLanguageOption) -> Bool {
        let selectedLanguage = appState.interfaceLocaleCode.lowercased()
        let optionLanguage = String(option.locale.lowercased().prefix(2))
        return selectedLanguage.hasPrefix(optionLanguage)
    }
}

private struct OnboardingFeatureGrid: View {
    let features: [OnboardingFeature]
    let isWide: Bool

    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 8), count: isWide ? min(features.count, 3) : 1)
    }

    var body: some View {
        LazyVGrid(columns: columns, alignment: .center, spacing: 8) {
            ForEach(Array(features.enumerated()), id: \.offset) { _, feature in
                FeatureCell(feature: feature)
            }
        }
    }
}

private struct FeatureCell: View {
    let feature: OnboardingFeature

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                Text(feature.icon)
                    .font(AppTypography.scaledFont(size: 18))
                    .frame(width: 28, height: 28)
                    .background(AppColors.accentYellow.opacity(0.18))
                    .clipShape(Circle())
                Text(feature.title)
                    .font(AppTypography.scaledFont(size: 14, weight: .medium))
                    .foregroundColor(AppColors.textPrimary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, alignment: .center)
            
            Text(feature.description)
                .font(AppTypography.scaledFont(size: 11, weight: .regular))
                .foregroundColor(AppColors.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(10)
        .background(Color.white.opacity(0.92))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        .shadow(color: Color.black.opacity(0.04), radius: 10, x: 0, y: 4)
    }
}

private struct OnboardingStepsCard: View {
    let steps: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(steps.indices, id: \.self) { index in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(index + 1)")
                        .font(AppTypography.scaledFont(size: 11, weight: .regular))
                        .foregroundColor(.white)
                        .frame(width: 20, height: 20)
                        .background(AppGradients.primary)
                        .clipShape(Circle())
                    Text(steps[index])
                        .font(AppTypography.scaledFont(size: 12, weight: .regular))
                        .foregroundColor(AppColors.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.92))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        .shadow(color: Color.black.opacity(0.04), radius: 10, x: 0, y: 4)
    }
}

struct PrivacyConsentView: View {
    let onAccept: () -> Void
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var selectedLegalDocument: LegalDocument?

    private var privacyDocument: LegalDocument? {
        let preferredLanguage = AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh") ? "zh" : "en"
        return appState.legalDocs.first(where: { $0.type == "privacy" && $0.locale.lowercased().contains(preferredLanguage) && $0.resolvedURL != nil })
            ?? appState.legalDocs.first(where: { $0.type == "privacy" && $0.resolvedURL != nil })
    }

    private var termsDocument: LegalDocument? {
        let preferredLanguage = AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh") ? "zh" : "en"
        return appState.legalDocs.first(where: { $0.type == "terms" && $0.locale.lowercased().contains(preferredLanguage) && $0.resolvedURL != nil })
            ?? appState.legalDocs.first(where: { $0.type == "terms" && $0.resolvedURL != nil })
    }

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                ScrollView {
                    VStack(spacing: 24) {
                        Text("🔒")
                            .font(AppTypography.scaledFont(size: proxy.size.width >= 700 ? 70 : 56))
                            .padding(.top, 20)

                        Text(appState.uiText("隐私政策、用户协议与使用告知", "Privacy, Terms & Usage Notices"))
                            .font(proxy.size.width >= 700 ? AppTypography.title1 : AppTypography.title2)
                            .foregroundColor(AppColors.textPrimary)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)

                        VStack(alignment: .leading, spacing: 16) {
                            SectionView(
                                title: appState.uiText("数据收集说明", "What we collect"),
                                content: appState.uiText(
                                    "默认本地模式不创建后端账号，不上传孩子图片、OCR 原文、录音、句卡正文或诊断日志。\n\n购买由 Apple App 内购买处理；本 App 只在当前设备 Keychain 保存本机积分余额和已处理交易的本地记录。",
                                    "The default local mode does not create a backend account or upload child images, OCR text, audio, card text, or diagnostics.\n\nPurchases are handled by Apple In-App Purchase; this app stores local credit balances and processed transaction records only in this device's Keychain."
                                )
                            )

                            SectionView(
                                title: appState.uiText("数据存储", "Storage"),
                                content: appState.uiText(
                                    "• 学习内容默认保存在本机\n• 本机积分保存在当前设备 Keychain\n• 家长可在家长区删除学习数据或重置本机积分钱包",
                                    "• Learning content is saved on this device by default\n• Local credits are stored in this device's Keychain\n• Parents can delete learning data or reset the local credit wallet in Parents"
                                )
                            )

                            SectionView(
                                title: appState.uiText("离线与权益", "Offline & entitlements"),
                                content: appState.uiText(
                                    "• 默认使用设备端 OCR、翻译与朗读能力\n• 今日免费次数先扣减，本机积分其次\n• 本机积分不按日期过期，但使用后会扣减\n• 消耗型本机积分不支持跨设备自动恢复，扣款金额以 Apple 确认弹窗为准",
                                    "• On-device OCR, translation, and speech are used by default\n• Free daily uses are consumed first, then local credits\n• Local credits do not expire by date, but are consumed when used\n• Consumable local credits do not restore automatically across devices; Apple confirms the final charge"
                                )
                            )
                        }
                        .padding(.horizontal)

                        VStack(spacing: 12) {
                            Button(action: {
                                let appDefaults = AppScopedDefaults()
                                appDefaults.set(true, forKey: AppDefaultKey.privacyPolicyAccepted)
                                appDefaults.set(Date(), forKey: AppDefaultKey.privacyConsentDate)
                                Task { await appState.acceptPrivacyConsent() }
                                dismiss()
                                onAccept()
                            }) {
                                Text(appState.uiText("同意并开始本地使用", "Agree & Start Local Use"))
                                    .font(AppTypography.buttonLarge)
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(minHeight: 56)
                                    .padding(.vertical, 14)
                                    .background(AppGradients.primary)
                                    .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
                                    .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
                            }
                            .buttonStyle(.plain)

                            HStack(spacing: 14) {
                                if let privacyDocument {
                                    Button {
                                        selectedLegalDocument = privacyDocument
                                    } label: {
                                        Text(appState.uiText("隐私政策", "Privacy Policy"))
                                            .font(AppTypography.body)
                                            .foregroundColor(AppColors.primary)
                                            .frame(minHeight: AppLayout.minimumTapTarget)
                                            .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                }
                                if let termsDocument {
                                    Button {
                                        selectedLegalDocument = termsDocument
                                    } label: {
                                        Text(appState.uiText("用户协议", "Terms"))
                                            .font(AppTypography.body)
                                            .foregroundColor(AppColors.primary)
                                            .frame(minHeight: AppLayout.minimumTapTarget)
                                            .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 30)
                    }
                    .frame(maxWidth: proxy.size.width >= 700 ? 680 : 480)
                    .frame(maxWidth: .infinity)
                }
            }
            .background(AppColors.background)
            .navigationDestination(item: $selectedLegalDocument) { document in
                if let url = document.resolvedURL {
                    LegalDocumentWebView(url: url)
                        .navigationTitle(legalDocumentTitle(for: document.type))
                        .navigationBarTitleDisplayMode(.inline)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(appState.uiText("关闭", "Close")) {
                        dismiss()
                    }
                    .frame(minHeight: AppLayout.minimumTapTarget)
                    .contentShape(Rectangle())
                }
            }
        }
    }

    private func legalDocumentTitle(for type: String) -> String {
        switch type {
        case "privacy": return appState.uiText("隐私政策", "Privacy Policy")
        case "terms": return appState.uiText("用户协议", "Terms of Service")
        case "child_data": return appState.uiText("儿童信息处理说明", "Child Data Policy")
        default: return type
        }
    }
}

struct SectionView: View {
    let title: String
    let content: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(AppTypography.headline)
                .foregroundColor(AppColors.textPrimary)

            Text(content)
                .font(AppTypography.body)
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.white.opacity(0.82))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
    }
}

#Preview {
    OnboardingView()
        .environmentObject(AppState())
}
