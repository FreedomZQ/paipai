import SwiftUI

struct SplashView: View {
    @State private var isAnimating = false
    @State private var showContent = false
    let onComplete: (() -> Void)?

    init(onComplete: (() -> Void)? = nil) {
        self.onComplete = onComplete
    }
    
    var body: some View {
        ZStack {
            AppGradients.primary
                .ignoresSafeArea()
            
            VStack(spacing: AppLayout.spacingXXL) {
                Spacer(minLength: AppLayout.spacingXXXL)
                
                Text("📚")
                    .font(AppTypography.scaledFont(size: 76))
                    .scaleEffect(isAnimating ? 1.0 : 0.8)
                    .opacity(isAnimating ? 1.0 : 0.0)
                
                VStack(spacing: 8) {
                    Text("拍拍伴读")
                        .font(AppTypography.scaledFont(size: 32, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("Paipai Read Along")
                        .font(AppTypography.scaledFont(size: 18, weight: .medium))
                        .foregroundColor(.white.opacity(0.9))
                }
                .opacity(showContent ? 1.0 : 0.0)
                .offset(y: showContent ? 0 : 20)
                
                Text("拍一句，听一句，慢慢会读")
                    .font(AppTypography.scaledFont(size: 16))
                    .foregroundColor(.white.opacity(0.8))
                    .opacity(showContent ? 1.0 : 0.0)
                    .offset(y: showContent ? 0 : 20)
                
                Spacer()
                
                HStack(spacing: 8) {
                    ForEach(0..<3) { index in
                        Circle()
                            .fill(Color.white)
                            .frame(width: 8, height: 8)
                            .opacity(isAnimating ? 1.0 : 0.3)
                            .animation(
                                .easeInOut(duration: 0.6)
                                .repeatForever(autoreverses: true)
                                .delay(Double(index) * 0.2),
                                value: isAnimating
                            )
                    }
                }
                .opacity(showContent ? 1.0 : 0.0)
                
                Spacer(minLength: AppLayout.spacingXXXL)
            }
            .padding(.horizontal, AppLayout.paddingScreen)
            .multilineTextAlignment(.center)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                isAnimating = true
            }
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                withAnimation(.easeOut(duration: 0.5)) {
                    showContent = true
                }
            }
            
            if let onComplete {
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    onComplete()
                }
            }
        }
    }
}

#Preview {
    SplashView()
}
