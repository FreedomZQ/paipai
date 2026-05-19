import AuthenticationServices
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
                    "后续引导页、登录页、家长区和主要功能页会优先使用这里选择的展示语言。",
                    "Onboarding, sign-in, parent settings, and main app screens will use this selected display language."
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
                    OnboardingFeature(icon: "☁️", title: appState.uiText("家长开启云能力", "Parent-enabled cloud"), description: appState.uiText("购买恢复和可选云端识图/朗读需要家长授权后才启用。", "Purchase restore and optional cloud OCR/speech require parent authorization.")),
                    OnboardingFeature(icon: "🛡️", title: appState.uiText("第三方处理受控", "Controlled processors"), description: appState.uiText("可选云端处理会按能力单独同意，并避免业务后端保存儿童内容。", "Optional cloud processing requires separate consent and avoids storing child content on the business backend.")),
                    OnboardingFeature(icon: "⚙️", title: appState.uiText("自主可控", "Full control"), description: appState.uiText("家长可查看和删除账号数据；本机学习数据由设备内删除入口控制。", "Parents can view and delete account data; local learning data is controlled on device."))
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
                    OnboardingFeature(icon: "👨‍💻", title: appState.uiText("家长主导", "Parent-led"), description: appState.uiText("设置、购买、账号相关操作需要家长进入。", "Settings, purchases, and account operations require parent access.")),
                    OnboardingFeature(icon: "👧", title: appState.uiText("孩子使用", "Child use"), description: appState.uiText("孩子主要使用学习练习相关功能。", "Children mainly use learning and practice features.")),
                    OnboardingFeature(icon: "❤️", title: appState.uiText("亲子互动", "Family interaction"), description: appState.uiText("家长可以参与孩子的学习过程，共同进步。", "Parents can participate in the child's learning journey and grow together."))
                ])
            ),
            OnboardingPage(
                icon: "🔒",
                title: appState.uiText("低打扰、低收集，本地优先", "Low interruption, low collection, local-first"),
                description: appState.uiText(
                    "相机/相册只用于你主动拍摄或选择内容；学习内容优先保存在设备本地。云端 OCR 和会员权益都会经过后端校验。",
                    "Camera/photo access is used only when you capture or select content. Learning content is local-first. Cloud OCR and paid entitlements are verified by the backend."
                ),
                kind: .features([
                    OnboardingFeature(icon: "📱", title: appState.uiText("多设备适配", "Adaptive layout"), description: appState.uiText("页面按 iPhone、iPad 和 Mac 窗口宽度自动收拢或居中展示。", "Screens adapt to iPhone, iPad, and Mac window widths.")),
                    OnboardingFeature(icon: "🛡️", title: appState.uiText("家长账号", "Parent account"), description: appState.uiText("本地使用和补偿权益无需登录；删除云端账号时再使用 Apple 登录。", "Local use and compensation benefits do not require sign-in; Apple sign-in is only used for cloud account deletion.")),
                    OnboardingFeature(icon: "💳", title: appState.uiText("权益校验", "Entitlements"), description: appState.uiText("免费/付费权益以后端返回为准，扣款金额以 Apple 确认弹窗为准。", "Free/paid entitlements follow the backend result; Apple confirms final charges."))
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
                                Text(appState.uiText("完成告知确认后，需要使用 Apple ID 登录才能进入 App", "Sign in with Apple is required after confirming the notices"))
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

    private var privacyURL: URL? {
        let preferredLanguage = AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh") ? "zh" : "en"
        return appState.legalDocs.first(where: { $0.type == "privacy" && $0.locale.lowercased().contains(preferredLanguage) })?.resolvedURL
            ?? appState.legalDocs.first(where: { $0.type == "privacy" })?.resolvedURL
    }

    private var termsURL: URL? {
        let preferredLanguage = AppLocaleCatalog.normalize(appState.interfaceLocaleCode).hasPrefix("zh") ? "zh" : "en"
        return appState.legalDocs.first(where: { $0.type == "terms" && $0.locale.lowercased().contains(preferredLanguage) })?.resolvedURL
            ?? appState.legalDocs.first(where: { $0.type == "terms" })?.resolvedURL
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
                                    "默认本地模式不创建后端账号，不上传孩子图片、OCR 原文、录音、句卡正文或诊断日志。\n\n家长开启账号能力后，仅为账号、安全、权益、购买恢复、删除请求处理必要数据；低敏诊断需要家长单独开启。",
                                    "The default local mode does not create a backend account or upload child images, OCR text, audio, card text, or diagnostics.\n\nAfter a parent enables account features, only data needed for account, security, entitlements, purchase restore, deletion requests is processed. Low-sensitivity diagnostics require separate parent opt-in."
                                )
                            )

                            SectionView(
                                title: appState.uiText("数据存储", "Storage"),
                                content: appState.uiText(
                                    "• 学习内容默认保存在本机\n• Apple 登录只代表家长账号，不创建孩子登录身份\n• 删除账号不会自动擦除本机学习数据，可在设备内单独清理",
                                    "• Learning content is saved on this device by default\n• Apple sign-in represents the parent account, not a child identity\n• Account deletion does not automatically erase local learning data; clear it separately on device"
                                )
                            )

                            SectionView(
                                title: appState.uiText("离线与权益", "Offline & entitlements"),
                                content: appState.uiText(
                                    "• 默认使用设备端 OCR、翻译与朗读能力\n• 后端不可用时，本地识别、朗读、句卡和本机记录仍可使用\n• 云端 OCR / 云端朗读首发暂未开放；未来必须先经家长同意和后端能力令牌\n• 购买和恢复购买以后端校验为准，扣款金额以 Apple 确认弹窗为准",
                                    "• On-device OCR, translation, and speech are used by default\n• If the backend is unavailable, local OCR, speech, cards, and records continue to work\n• Cloud OCR / cloud speech are not enabled at launch; future use requires parent consent and backend capability tokens\n• Purchases and restores follow backend verification; Apple confirms the final charge"
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
                                if let privacyURL {
                                    Link(destination: privacyURL) {
                                        Text(appState.uiText("隐私政策", "Privacy Policy"))
                                            .font(AppTypography.body)
                                            .foregroundColor(AppColors.primary)
                                            .frame(minHeight: AppLayout.minimumTapTarget)
                                            .contentShape(Rectangle())
                                    }
                                }
                                if let termsURL {
                                    Link(destination: termsURL) {
                                        Text(appState.uiText("用户协议", "Terms"))
                                            .font(AppTypography.body)
                                            .foregroundColor(AppColors.primary)
                                            .frame(minHeight: AppLayout.minimumTapTarget)
                                            .contentShape(Rectangle())
                                    }
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
}

struct AppleSignInRequiredView: View {
    @EnvironmentObject var appState: AppState
    @State private var appleSignInRequestContext: AppleSignInRequestContext?
    @State private var isSigningIn = false

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                OnboardingBackground()
                ScrollView(showsIndicators: false) {
                    VStack(spacing: proxy.size.width >= 700 ? 24 : 18) {
                        VStack(spacing: 12) {
                            Text(appState.uiText("本地可直接使用，家长能力再登录", "Use locally first; sign in for parent features"))
                                .font(proxy.size.width >= 700 ? AppTypography.title1 : AppTypography.title2)
                                .foregroundColor(AppColors.textPrimary)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)

                            Text(appState.uiText(
                                "拍拍伴读默认使用本地匿名模式，不上传儿童内容。补偿权益无需登录；删除云端账号时，再由家长使用 Apple 登录授权。",
                                "Paipai uses local anonymous mode by default and does not upload child content. Compensation benefits do not require sign-in; Apple sign-in is only used for cloud account deletion."
                            ))
                            .font(AppTypography.bodyLarge)
                            .foregroundColor(AppColors.textSecondary)
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)
                        }

                        VStack(alignment: .leading, spacing: 12) {
                            Label(appState.uiText("Apple 登录成功后自动拉取账号状态和免费权益。", "After Apple sign-in, account state and free entitlement are fetched automatically."), systemImage: "checkmark.shield.fill")
                            Label(appState.uiText("购买、恢复购买和云端能力继续以后端校验结果为准。", "Purchases, restores, and cloud features continue to follow backend verification."), systemImage: "server.rack")
                            Label(appState.uiText("iPhone、iPad 和 Mac 窗口都会使用自适应安全布局。", "iPhone, iPad, and Mac windows use adaptive safe-area layouts."), systemImage: "rectangle.3.group")
                        }
                        .font(AppTypography.bodySmall)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(18)
                        .background(Color.white.opacity(0.92))
                        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
                        .shadow(color: Color.black.opacity(0.08), radius: 18, x: 0, y: 8)

                        SignInWithAppleButton(.signIn, onRequest: configureAppleRequest, onCompletion: handleAppleSignInCompletion)
                            .signInWithAppleButtonStyle(.black)
                            .frame(height: 52)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .disabled(isSigningIn)

                        #if DEBUG
                        Button {
                            isSigningIn = true
                            Task {
                                _ = await appState.completeDevelopmentSignIn()
                                isSigningIn = false
                            }
                        } label: {
                            Text(appState.uiText("本地开发登录（模拟器）", "Local development sign-in"))
                                .font(AppTypography.button)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .background(AppColors.primary)
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .disabled(isSigningIn)
                        #endif

                        Button {
                            appState.hasCompletedOnboarding = false
                        } label: {
                            Text(appState.uiText("重新选择语言 / 查看说明", "Choose language / review intro"))
                                .font(AppTypography.buttonSmall)
                                .foregroundColor(AppColors.textSecondary)
                                .frame(minHeight: AppLayout.minimumTapTarget)
                                .padding(.horizontal, 14)
                                .background(Color.white.opacity(0.82))
                                .clipShape(Capsule())
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)

                        if isSigningIn {
                            ProgressView(appState.uiText("正在登录并初始化权益…", "Signing in and initializing entitlement…"))
                                .font(AppTypography.caption)
                                .foregroundColor(AppColors.textSecondary)
                        }

                        if let error = appState.errorMessage, !error.isEmpty {
                            Text(error)
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.error)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding(12)
                                .background(Color.white.opacity(0.9))
                                .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                        }
                    }
                    .frame(maxWidth: proxy.size.width >= 700 ? 620 : 430)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, proxy.size.width >= 700 ? 48 : 20)
                    .padding(.top, proxy.size.width >= 700 ? 40 : 24)
                    .padding(.bottom, max(proxy.safeAreaInsets.bottom + 24, 42))
                }
            }
        }
        .task {
            await appState.bootstrapIfNeeded()
        }
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
            isSigningIn = true
            Task {
                _ = await appState.completeAppleSignIn(
                    authorizationCode: authorizationCode,
                    identityToken: identityToken,
                    state: requestContext.state,
                    nonce: requestContext.backendNonce,
                    givenName: credential.fullName?.normalizedGivenName,
                    familyName: credential.fullName?.normalizedFamilyName
                )
                isSigningIn = false
            }
        case let .failure(error):
            if isAppleSignInCancellation(error) {
                isSigningIn = false
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
