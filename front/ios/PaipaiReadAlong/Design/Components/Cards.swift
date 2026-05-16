import SwiftUI

// MARK: - Main Card
struct MainCard<Content: View>: View {
    let content: Content
    
    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    var body: some View {
        content
            .padding(AppLayout.paddingCard)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
            .shadow(
                color: Color.black.opacity(0.06),
                radius: 10,
                x: 0,
                y: 2
            )
    }
}

// MARK: - Gradient Card
struct GradientCard<Content: View>: View {
    let content: Content
    
    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    var body: some View {
        content
            .padding(AppLayout.paddingCard)
            .background(AppGradients.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
    }
}

// MARK: - Info Card
struct InfoCard: View {
    let icon: String
    let title: String
    let subtitle: String?
    let value: String
    let color: Color
    
    init(
        icon: String,
        title: String,
        subtitle: String? = nil,
        value: String,
        color: Color = AppColors.primary
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.value = value
        self.color = color
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingXS) {
            HStack(spacing: AppLayout.spacingS) {
                Image(systemName: icon)
                    .font(AppTypography.scaledFont(size: 14))
                    .foregroundColor(color)
                Text(title)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
            
            Text(value)
                .font(AppTypography.headline)
                .foregroundColor(color)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
            
            if let subtitle = subtitle {
                Text(subtitle)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
        .background(color.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
    }
}

// MARK: - Quote Card
struct QuoteCard: View {
    let text: String
    let translation: String?
    let isCompact: Bool
    let isPlaybackEnabled: Bool
    let onPlay: (() -> Void)?
    let onPlayTranslation: (() -> Void)?
    
    init(
        text: String,
        translation: String? = nil,
        isCompact: Bool = false,
        isPlaybackEnabled: Bool = true,
        onPlay: (() -> Void)? = nil,
        onPlayTranslation: (() -> Void)? = nil
    ) {
        self.text = text
        self.translation = translation
        self.isCompact = isCompact
        self.isPlaybackEnabled = isPlaybackEnabled
        self.onPlay = onPlay
        self.onPlayTranslation = onPlayTranslation
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: isCompact ? AppLayout.spacingXS : AppLayout.spacingM) {
            HStack(alignment: .center, spacing: AppLayout.spacingS) {
                Text(text)
                    .font(isCompact ? AppTypography.footnote : AppTypography.body)
                    .lineSpacing(isCompact ? 1 : 4)
                    .lineLimit(isCompact ? 2 : nil)
                    .foregroundColor(AppColors.textPrimary)
                    .frame(maxWidth: .infinity, minHeight: isCompact ? 34 : 0, alignment: .leading)
                
                Spacer(minLength: AppLayout.spacingS)
                
                if let onPlay = onPlay {
                    SpeakerButton(isCompact: isCompact, tint: AppColors.primary, action: onPlay)
                        .disabled(!isPlaybackEnabled)
                        .opacity(isPlaybackEnabled ? 1 : 0.45)
                }
            }
            
            if let translation = translation {
                HStack(alignment: .center, spacing: AppLayout.spacingS) {
                    Text(translation)
                        .font(isCompact ? AppTypography.caption : AppTypography.footnote)
                        .lineSpacing(isCompact ? 1 : 3)
                        .lineLimit(isCompact ? 1 : nil)
                        .foregroundColor(AppColors.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: isCompact ? 34 : 0, alignment: .leading)

                    Spacer(minLength: AppLayout.spacingS)

                    if let onPlayTranslation = onPlayTranslation {
                        SpeakerButton(isCompact: isCompact, tint: AppColors.secondary, action: onPlayTranslation)
                            .disabled(!isPlaybackEnabled)
                            .opacity(isPlaybackEnabled ? 1 : 0.45)
                    }
                }
            }
        }
        .padding(isCompact ? AppLayout.spacingS : AppLayout.paddingCard)
        .background(AppColors.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
    }
}

struct SpeakerButton: View {
    let isCompact: Bool
    let tint: Color
    let action: () -> Void

    init(isCompact: Bool = true, tint: Color = AppColors.primary, action: @escaping () -> Void) {
        self.isCompact = isCompact
        self.tint = tint
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: "speaker.wave.2.fill")
                .font(AppTypography.scaledFont(size: isCompact ? 15 : 18))
                .foregroundColor(tint)
                .frame(width: isCompact ? 34 : AppLayout.minimumTapTarget, height: isCompact ? 34 : AppLayout.minimumTapTarget)
                .background(tint.opacity(0.1))
                .clipShape(Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Membership Badge
struct MembershipBadge: View {
    let status: String
    let expiryDate: String?
    let textScale: CGFloat
    @ScaledMetric(relativeTo: .caption) private var iconSize: CGFloat = 10
    @ScaledMetric(relativeTo: .caption) private var statusSize: CGFloat = 12
    @ScaledMetric(relativeTo: .caption2) private var expirySize: CGFloat = 9

    init(status: String, expiryDate: String?, textScale: CGFloat = 1) {
        self.status = status
        self.expiryDate = expiryDate
        self.textScale = textScale
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: AppLayout.spacingS) {
                Image(systemName: "crown.fill")
                    .font(.system(size: iconSize * textScale, weight: .regular))
                Text(status)
                    .font(.system(size: statusSize * textScale, weight: .semibold))
            }
            if let expiryDate = formattedExpiryDate(expiryDate), !expiryDate.isEmpty {
                Text(expiryDate)
                    .font(.system(size: expirySize * textScale, weight: .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
        }
        .foregroundColor(.white)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.2))
        .clipShape(Capsule())
    }

    private func formattedExpiryDate(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallback = ISO8601DateFormatter()
        let date = iso.date(from: raw) ?? fallback.date(from: raw)
        guard let date else { return raw }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_Hans_CN")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: date)
    }
}

// MARK: - Usage Item
struct UsageItem: View {
    let icon: String
    let label: String
    let used: Int
    let total: Int
    
    var body: some View {
        HStack(spacing: AppLayout.spacingS) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: 16))
                .foregroundColor(.white)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(AppTypography.scaledFont(size: 10))
                    .foregroundColor(.white.opacity(0.9))
                Text("\(used)/\(total)")
                    .font(AppTypography.scaledFont(size: 12, weight: .semibold))
                    .foregroundColor(.white)
            }
            
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
        .background(Color.white.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusS, style: .continuous))
    }
}

// MARK: - Child Chip
struct ChildChip: View {
    let emoji: String
    let name: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: AppLayout.spacingS) {
                Text(emoji)
                    .font(AppTypography.scaledFont(size: 24))
                Text(name)
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textPrimary)
                
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(AppTypography.scaledFont(size: 16))
                        .foregroundColor(AppColors.primary)
                }
            }
            .padding(.horizontal, 12)
            .frame(minHeight: AppLayout.minimumTapTarget)
            .padding(.vertical, 8)
            .background(isSelected ? AppColors.primary.opacity(0.15) : AppColors.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Preview
#Preview {
    ScrollView {
        VStack(spacing: 20) {
            MainCard {
                VStack(alignment: .leading) {
                    Text("主卡片")
                        .font(AppTypography.headline)
                    Text("这是主卡片的内容")
                        .font(AppTypography.body)
                        .foregroundColor(AppColors.textSecondary)
                }
            }
            
            GradientCard {
                VStack(alignment: .leading) {
                    Text("渐变卡片")
                        .font(AppTypography.headline)
                        .foregroundColor(.white)
                    Text("这是渐变卡片的内容")
                        .font(AppTypography.body)
                        .foregroundColor(.white.opacity(0.9))
                }
            }
            
            HStack(spacing: 12) {
                InfoCard(
                    icon: "book",
                    title: "学习中",
                    value: "5"
                )
                InfoCard(
                    icon: "checkmark.circle",
                    title: "今日完成",
                    value: "3"
                )
                InfoCard(
                    icon: "square.stack",
                    title: "已保存",
                    value: "12"
                )
            }
            
            QuoteCard(
                text: "The cat is sleeping on the sofa.",
                translation: "猫正在沙发上睡觉",
                onPlay: {}
            )
            
            MembershipBadge(status: "高级会员", expiryDate: nil)
            
            HStack(spacing: 8) {
                UsageItem(icon: "camera", label: "文字识别", used: 10, total: 30)
                UsageItem(icon: "speaker.wave.2", label: "朗读功能", used: 15, total: 50)
            }
            
            HStack(spacing: 12) {
                ChildChip(emoji: "👨‍🎓", name: "小明", isSelected: true) {}
                ChildChip(emoji: "👩‍💼", name: "小红", isSelected: false) {}
            }
        }
        .padding()
    }
    .background(AppColors.background)
}
