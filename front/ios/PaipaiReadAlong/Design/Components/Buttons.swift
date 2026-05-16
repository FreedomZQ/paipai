import SwiftUI

// MARK: - Primary Button
struct PrimaryButton: View {
    let title: String
    let icon: String?
    let action: () -> Void
    let isLoading: Bool
    let isDisabled: Bool
    
    init(
        title: String,
        icon: String? = nil,
        isLoading: Bool = false,
        isDisabled: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.isLoading = isLoading
        self.isDisabled = isDisabled
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: AppLayout.spacingM) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    if let icon = icon {
                        Image(systemName: icon)
                            .font(AppTypography.scaledFont(size: 20, weight: .semibold))
                    }
                    Text(title)
                        .font(AppTypography.buttonLarge)
                }
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 56)
            .padding(.horizontal, AppLayout.spacingL)
            .padding(.vertical, 16)
            .background {
                if isDisabled {
                    AppColors.textTertiary
                } else {
                    AppGradients.primary
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
            .shadow(color: isDisabled ? .clear : AppColors.secondary.opacity(0.22), radius: 10, x: 0, y: 4)
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled || isLoading)
        .opacity(isDisabled ? 0.62 : 1)
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Secondary Button
struct SecondaryButton: View {
    let title: String
    let icon: String?
    let action: () -> Void
    let isDisabled: Bool
    
    init(
        title: String,
        icon: String? = nil,
        isDisabled: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.isDisabled = isDisabled
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: AppLayout.spacingM) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(AppTypography.scaledFont(size: 18, weight: .medium))
                }
                Text(title)
                    .font(AppTypography.button)
            }
            .foregroundColor(AppColors.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 52)
            .padding(.horizontal, AppLayout.spacingL)
            .padding(.vertical, 14)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous)
                    .stroke(isDisabled ? AppColors.border : AppColors.border, lineWidth: 2)
            )
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.6 : 1)
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Icon Button
struct IconButton: View {
    let icon: String
    let size: CGFloat
    let backgroundColor: Color
    let foregroundColor: Color
    let action: () -> Void
    
    init(
        icon: String,
        size: CGFloat = 44,
        backgroundColor: Color = AppColors.cardBackground,
        foregroundColor: Color = AppColors.textPrimary,
        action: @escaping () -> Void
    ) {
        self.icon = icon
        self.size = size
        self.backgroundColor = backgroundColor
        self.foregroundColor = foregroundColor
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(AppTypography.scaledFont(size: size * 0.4, weight: .medium))
                .foregroundColor(foregroundColor)
                .frame(width: max(size, AppLayout.minimumTapTarget), height: max(size, AppLayout.minimumTapTarget))
                .background(backgroundColor)
                .clipShape(Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Close Button
struct CloseButton: View {
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(AppTypography.scaledFont(size: 16, weight: .medium))
                .foregroundColor(AppColors.textSecondary)
                .frame(width: AppLayout.minimumTapTarget, height: AppLayout.minimumTapTarget)
                .background(AppColors.cardBackground)
                .clipShape(Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Back Button
struct BackButton: View {
    let title: String
    let action: () -> Void

    init(title: String = "返回", action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: "chevron.left")
                    .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                Text(title)
                    .font(AppTypography.body)
            }
            .foregroundColor(AppColors.primary)
            .frame(minHeight: AppLayout.minimumTapTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Preview
#Preview {
    VStack(spacing: 20) {
        PrimaryButton(title: "拍一句", icon: "camera.fill") {}
        PrimaryButton(title: "加载中...", isLoading: true) {}
        PrimaryButton(title: "禁用状态", isDisabled: true) {}
        
        SecondaryButton(title: "开始复习", icon: "sparkles.rectangle.stack.fill") {}
        SecondaryButton(title: "禁用状态", isDisabled: true) {}
        
        HStack(spacing: 16) {
            IconButton(icon: "xmark") {}
            IconButton(icon: "gear", backgroundColor: AppColors.primary, foregroundColor: .white) {}
        }
        
        HStack {
            CloseButton() {}
            BackButton() {}
        }
    }
    .padding()
    .background(AppColors.background)
}
