import SwiftUI
#if os(iOS)
import UIKit
#endif

enum AppTextSizeOption: String, CaseIterable, Identifiable {
    case small
    case medium
    case large
    case extraLarge

    var id: String { rawValue }

    var title: String {
        switch self {
        case .small: return "小"
        case .medium: return "中"
        case .large: return "大"
        case .extraLarge: return "特大"
        }
    }

    var englishTitle: String {
        switch self {
        case .small: return "Small"
        case .medium: return "Medium"
        case .large: return "Large"
        case .extraLarge: return "Extra Large"
        }
    }

    var subtitle: String {
        switch self {
        case .small: return "更紧凑"
        case .medium: return "标准"
        case .large: return "更清晰"
        case .extraLarge: return "适合长辈"
        }
    }

    var englishSubtitle: String {
        switch self {
        case .small: return "Compact"
        case .medium: return "Standard"
        case .large: return "Clearer"
        case .extraLarge: return "For older readers"
        }
    }

    var multiplier: CGFloat {
        switch self {
        case .small: return 1.22
        case .medium: return 1.36
        case .large: return 1.52
        case .extraLarge: return 1.72
        }
    }

    var dynamicTypeSize: DynamicTypeSize {
        switch self {
        case .small: return .large
        case .medium: return .xLarge
        case .large: return .xxLarge
        case .extraLarge: return .xxxLarge
        }
    }

    #if os(iOS)
    var contentSizeCategory: ContentSizeCategory {
        switch self {
        case .small: return .large
        case .medium: return .extraLarge
        case .large: return .extraExtraLarge
        case .extraLarge: return .extraExtraExtraLarge
        }
    }
    #endif
}

enum AppTypographyScale {
    static var multiplier: CGFloat = AppTextSizeOption.medium.multiplier
}

// MARK: - Color Palette
enum AppColors {
    // Primary Gradient Colors
    static let gradientStart = Color(hex: "#FFD166")
    static let gradientEnd = Color(hex: "#06D6A0")
    
    // Primary Colors
    static let primary = Color(hex: "#4ECDC4")
    static let secondary = Color(hex: "#06D6A0")
    
    // Background Colors
    static let background = Color(hex: "#FAFAFA")
    static let cardBackground = Color(hex: "#FFF9E6")
    
    // Text Colors
    static let textPrimary = Color(hex: "#333333")
    static let textSecondary = Color(hex: "#666666")
    static let textTertiary = Color(hex: "#999999")
    
    // Border & Divider
    static let border = Color(hex: "#E0E0E0")
    
    // Status Colors
    static let success = Color(hex: "#06D6A0")
    static let warning = Color(hex: "#FFD166")
    static let error = Color(hex: "#EF476F")
    static let info = Color(hex: "#118AB2")
    
    // Accent Colors
    static let accentPink = Color(hex: "#FF6B9D")
    static let accentMint = Color(hex: "#95E1D3")
    static let accentYellow = Color(hex: "#FFD166")
}

// MARK: - Gradients
enum AppGradients {
    static let primary = LinearGradient(
        colors: [AppColors.gradientStart, AppColors.gradientEnd],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
    
    static let card = LinearGradient(
        colors: [AppColors.cardBackground.opacity(0.8), AppColors.cardBackground],
        startPoint: .top,
        endPoint: .bottom
    )
}

// MARK: - Shadows
enum AppShadows {
    static let small = ShadowStyle(
        color: Color.black.opacity(0.06),
        radius: 10,
        x: 0,
        y: 2
    )
    
    static let medium = ShadowStyle(
        color: Color.black.opacity(0.08),
        radius: 16,
        x: 0,
        y: 5
    )
    
    static let large = ShadowStyle(
        color: Color.black.opacity(0.12),
        radius: 28,
        x: 0,
        y: 8
    )
}

struct ShadowStyle {
    let color: Color
    let radius: CGFloat
    let x: CGFloat
    let y: CGFloat
}

// MARK: - Typography
enum AppTypography {
    // Large Titles
    static var largeTitle: Font { scaledFont(size: 34, weight: .bold) }
    static var title1: Font { scaledFont(size: 28, weight: .bold) }
    static var title2: Font { scaledFont(size: 22, weight: .bold) }
    static var title3: Font { scaledFont(size: 20, weight: .semibold) }
    
    // Body Text
    static var bodyLarge: Font { scaledFont(size: 17, weight: .regular) }
    static var body: Font { scaledFont(size: 16, weight: .regular) }
    static var bodySmall: Font { scaledFont(size: 14, weight: .regular) }
    
    // Special Text
    static var headline: Font { scaledFont(size: 17, weight: .semibold) }
    static var subheadline: Font { scaledFont(size: 15, weight: .regular) }
    static var footnote: Font { scaledFont(size: 13, weight: .regular) }
    static var caption: Font { scaledFont(size: 12, weight: .regular) }
    
    // Button Text
    static var buttonLarge: Font { scaledFont(size: 18, weight: .semibold) }
    static var button: Font { scaledFont(size: 16, weight: .semibold) }
    static var buttonSmall: Font { scaledFont(size: 14, weight: .medium) }

    static func scaledFont(size: CGFloat, weight: Font.Weight = .regular, design: Font.Design? = nil) -> Font {
        let scaledSize = max(9, size * AppTypographyScale.multiplier)
        if let design {
            return .system(size: scaledSize, weight: weight, design: design)
        }
        return .system(size: scaledSize, weight: weight)
    }
}

// MARK: - Layout
enum AppLayout {
    // Spacing
    static let spacingXS: CGFloat = 4
    static let spacingS: CGFloat = 8
    static let spacingM: CGFloat = 12
    static let spacingL: CGFloat = 16
    static let spacingXL: CGFloat = 20
    static let spacingXXL: CGFloat = 24
    static let spacingXXXL: CGFloat = 32
    
    // Corner Radius
    static let cornerRadiusS: CGFloat = 8
    static let cornerRadiusM: CGFloat = 12
    static let cornerRadiusL: CGFloat = 16
    static let cornerRadiusXL: CGFloat = 20
    static let cornerRadiusXXL: CGFloat = 24
    
    // Padding
    static let paddingScreen: CGFloat = 20
    static let paddingCard: CGFloat = 16
    static let paddingButton: CGFloat = 16

    // Adaptive layout
    static let readableMaxWidth: CGFloat = 760
    static let wideReadableMaxWidth: CGFloat = 960
    static let minimumTapTarget: CGFloat = 44
    static let inputMinHeight: CGFloat = 48
    static let bottomNavigationHeight: CGFloat = 64
    static let bottomNavigationItemHeight: CGFloat = 48
    static let bottomNavigationIconSize: CGFloat = 17
    static let bottomNavigationTextSize: CGFloat = 14
    static let bottomNavigationSpacing: CGFloat = 5
    static let bottomNavigationMaxWidth: CGFloat = 720
    static let bottomNavigationContentInset: CGFloat = 104
}

// MARK: - Color Extension
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - View Modifiers
struct PrimaryButtonStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(AppTypography.buttonLarge)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 56)
            .padding(.horizontal, AppLayout.spacingL)
            .padding(.vertical, 16)
            .background(AppGradients.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
            .shadow(
                color: AppColors.secondary.opacity(0.22),
                radius: 10,
                x: 0,
                y: 4
            )
    }
}

struct SecondaryButtonStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(AppTypography.button)
            .foregroundColor(AppColors.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 52)
            .padding(.horizontal, AppLayout.spacingL)
            .padding(.vertical, 14)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous)
                    .stroke(AppColors.border, lineWidth: 2)
            )
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
    }
}

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(AppLayout.paddingCard)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
            .shadow(
                color: AppShadows.small.color,
                radius: AppShadows.small.radius,
                x: AppShadows.small.x,
                y: AppShadows.small.y
            )
    }
}

// MARK: - View Extensions
extension View {
    func primaryButton() -> some View {
        modifier(PrimaryButtonStyle())
    }
    
    func secondaryButton() -> some View {
        modifier(SecondaryButtonStyle())
    }
    
    func card() -> some View {
        modifier(CardStyle())
    }

    /// Keeps content comfortable on iPhone, iPad and resizable Mac windows.
    func adaptiveContentFrame(maxWidth: CGFloat = AppLayout.readableMaxWidth, alignment: Alignment = .center) -> some View {
        self
            .frame(maxWidth: maxWidth, alignment: alignment)
            .frame(maxWidth: .infinity, alignment: alignment)
    }

    /// Standard app input surface with a large tap target and visible focus area.
    func appInputSurface(minHeight: CGFloat = AppLayout.inputMinHeight) -> some View {
        self
            .padding(.horizontal, AppLayout.spacingM)
            .padding(.vertical, AppLayout.spacingS)
            .frame(minHeight: minHeight)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                    .stroke(AppColors.border, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
    }

    /// Standard multi-line input surface for TextEditor blocks.
    func appTextEditorSurface(minHeight: CGFloat = 120) -> some View {
        self
            .frame(minHeight: minHeight)
            .padding(AppLayout.spacingS)
            .scrollContentBackground(.hidden)
            .background(Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                    .stroke(AppColors.border, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
    }

    @ViewBuilder
    func appScrollDismissesKeyboardInteractively() -> some View {
        #if os(iOS)
        self.scrollDismissesKeyboard(.interactively)
        #else
        self
        #endif
    }

    @ViewBuilder
    func appKeyboardDoneToolbar(doneTitle: String = "完成") -> some View {
        #if os(iOS)
        self.toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(doneTitle) {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
                .font(AppTypography.buttonSmall)
            }
        }
        #else
        self
        #endif
    }
}
