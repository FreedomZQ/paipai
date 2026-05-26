import SwiftUI

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState
    @State private var selectedProduct: CreditProduct?
    @State private var isLoading = false
    private var purchasableProducts: [CreditProduct] {
        appState.creditProducts.filter { $0.enabled }
    }

    private var hasConfiguredProducts: Bool {
        !purchasableProducts.isEmpty
    }

    private var selectedOrFirstProduct: CreditProduct? {
        selectedProduct ?? purchasableProducts.first
    }

    private var canPurchaseSelectedProduct: Bool {
        selectedOrFirstProduct?.enabled == true
            && appState.billingHealth?.purchaseAvailable == true
    }

    var body: some View {
        Group {
            if !appState.isParentGateVerified {
                NavigationStack {
                    ParentGateView {
                        appState.isParentGateVerified = true
                    }
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            CloseButton { dismiss() }
                        }
                    }
                }
            } else {
                paywallContent
            }
        }
    }

    private var paywallContent: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: AppLayout.spacingXL) {
                    headerSection
                    if hasConfiguredProducts {
                        healthSection
                    }
                    if hasConfiguredProducts {
                        productListSection
                        actionSection
                    }
                    footerSection
                }
                .padding()
                .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
            }
            .background(AppColors.background)
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    CloseButton { dismiss() }
                }
            }
            .task {
                await appState.bootstrapIfNeeded()
                await appState.refreshBillingSurface()
                selectedProduct = purchasableProducts.first
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: AppLayout.spacingM) {
            Text("💳")
                .font(AppTypography.scaledFont(size: 72))
            Text(paywallHeadline)
                .font(AppTypography.title1)
                .foregroundColor(AppColors.textPrimary)
                .multilineTextAlignment(.center)
            Text(paywallSubtitle)
                .font(AppTypography.body)
                .foregroundColor(AppColors.textSecondary)
                .multilineTextAlignment(.center)
        }
    }

    private var healthSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                Label(
                    appState.billingHealth?.purchaseAvailable == true
                        ? appState.uiText("当前可购买", "Purchasing is available")
                        : appState.uiText("暂时无法购买", "Purchasing is temporarily unavailable"),
                    systemImage: appState.billingHealth?.purchaseAvailable == true ? "checkmark.shield" : "xmark.shield"
                )
                .font(AppTypography.headline)
                .foregroundColor(appState.billingHealth?.purchaseAvailable == true ? AppColors.success : AppColors.error)
                Text(appState.billingHealth?.unavailableMessage?.isEmpty == false ? appState.billingHealth?.unavailableMessage ?? "" : healthHint)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var productListSection: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingM) {
            HStack {
                Text(appState.localizedText(
                    zhHans: "可购买本机积分",
                    english: "Local credit packages",
                    japanese: "購入可能なパック",
                    korean: "구매 가능한 패키지",
                    spanish: "Paquetes disponibles"
                ))
                    .font(AppTypography.headline)
                Spacer()
                Text(appState.localizedText(
                    zhHans: "余额仅保存在此设备",
                    english: "Stored on this device",
                    japanese: "この端末に保存",
                    korean: "이 기기에 저장",
                    spanish: "Guardado en este dispositivo"
                ))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
            ForEach(purchasableProducts) { product in
                ProductCard(
                    product: product,
                    isSelected: selectedOrFirstProduct?.productCode == product.productCode,
                    action: { selectedProduct = product }
                )
            }
        }
    }

    private var actionSection: some View {
        VStack(spacing: AppLayout.spacingM) {
            PrimaryButton(
                title: isLoading
                    ? appState.uiText("处理中...", "Processing...")
                    : purchaseButtonTitle,
                isLoading: isLoading,
                isDisabled: !canPurchaseSelectedProduct
            ) {
                Task {
                    guard let product = selectedOrFirstProduct, product.enabled else { return }
                    isLoading = true
                    let success = await appState.purchaseAppStoreProduct(product: product)
                    isLoading = false
                    if success { dismiss() }
                }
            }
            .disabled(!canPurchaseSelectedProduct)

            Button(appState.uiText("恢复/刷新购买状态", "Restore / refresh purchases")) {
                Task {
                    await appState.restorePurchases()
                }
            }
            .font(AppTypography.body)
            .foregroundColor(AppColors.primary)
            .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget)
        }
    }

    private var footerSection: some View {
        VStack(spacing: 6) {
            Text(appState.uiText("购买由 Apple App 内购买完成。本机积分不按日期过期，但使用后会扣减；消耗型积分不支持跨设备自动恢复。退款或购买争议请通过 Apple 官方购买问题渠道处理。", "Purchases are completed through Apple In-App Purchase. Local credits do not expire by date, but are consumed when used; consumable credits do not restore automatically across devices. Refunds or purchase disputes are handled through Apple."))
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textTertiary)
                .multilineTextAlignment(.center)
        }
    }

    private var paywallHeadline: String {
        let headline = appState.bootstrap.paywall.headline.trimmingCharacters(in: .whitespacesAndNewlines)
        return localizedPaywallCopy(
            headline,
            fallback: appState.localizedText(
                zhHans: "购买本机积分",
                english: "Buy local credits",
                japanese: "クレジットパックを購入",
                korean: "크레딧 패키지 구매",
                spanish: "Comprar paquetes de créditos"
            )
        )
    }

    private var paywallSubtitle: String {
        let subtitle = appState.bootstrap.paywall.subtitle.trimmingCharacters(in: .whitespacesAndNewlines)
        return localizedPaywallCopy(
            subtitle,
            fallback: appState.localizedText(
                zhHans: "本机功能积分用于识字和朗读，云端功能积分暂未开放。",
                english: "Local feature credits are used for OCR and read-aloud. Cloud feature credits are not enabled yet.",
                japanese: "本機クレジットはこの端末の OCR と読み上げに使われ、残高は開発者サーバーへ送信されません。",
                korean: "로컬 크레딧은 이 기기의 OCR 및 읽어주기에 사용되며 잔액은 개발자 서버로 업로드되지 않습니다.",
                spanish: "Los creditos locales se usan para OCR y lectura en este dispositivo. El saldo no se sube a un servidor del desarrollador."
            )
        )
    }

    private func localizedPaywallCopy(_ text: String, fallback: String) -> String {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty else { return fallback }

        switch normalizedText {
        case "本机积分":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Local credits",
                japanese: "本機クレジット",
                korean: "로컬 크레딧",
                spanish: "Creditos locales"
            )
        case "用于当前设备的本地识字和朗读。购买由 Apple 确认，余额只保存在本机 Keychain。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "For on-device OCR and read-aloud on this device. Apple confirms purchases, and balances stay in this device's Keychain.",
                japanese: "この端末の OCR と読み上げに使用します。購入は Apple が確認し、残高はこの端末の Keychain に保存されます。",
                korean: "이 기기의 OCR 및 읽어주기에 사용됩니다. 구매는 Apple이 확인하며 잔액은 이 기기의 Keychain에 저장됩니다.",
                spanish: "Para OCR y lectura en este dispositivo. Apple confirma las compras y el saldo queda en el Keychain del dispositivo."
            )
        case "购买或赠送的积分不按日期过期，使用后按页面显示的消耗值扣减。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Purchased or granted credits do not expire by date and are consumed according to the cost shown in the app.",
                japanese: "購入または付与されたクレジットは日付では期限切れにならず、画面に表示された消費量で差し引かれます。",
                korean: "구매 또는 제공된 크레딧은 날짜로 만료되지 않으며 앱에 표시된 차감량에 따라 사용됩니다.",
                spanish: "Los creditos comprados o concedidos no caducan por fecha y se consumen segun el coste mostrado."
            )
        case "学习内容和本机积分默认只保存在当前设备，不上传到开发者服务器。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Learning content and local credits stay on this device by default and are not uploaded to a developer server.",
                japanese: "学習内容と本機クレジットは標準でこの端末に保存され、開発者サーバーへ送信されません。",
                korean: "학습 콘텐츠와 로컬 크레딧은 기본적으로 이 기기에 저장되며 개발자 서버로 업로드되지 않습니다.",
                spanish: "El contenido de aprendizaje y los creditos locales permanecen en este dispositivo por defecto."
            )
        case "消耗型本机积分不支持跨设备自动恢复。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Consumable local credits do not restore automatically across devices.",
                japanese: "消耗型の本機クレジットは端末間で自動復元されません。",
                korean: "소모성 로컬 크레딧은 기기 간 자동 복원이 지원되지 않습니다.",
                spanish: "Los creditos locales consumibles no se restauran automaticamente entre dispositivos."
            )
        case "解锁家庭伴读节奏", "解锁高级版伴读节奏":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Unlock your family read-aloud rhythm",
                japanese: "家族の伴読リズムを解放",
                korean: "가족 함께 읽기 리듬을 열어 보세요",
                spanish: "Desbloquea el ritmo de lectura familiar"
            )
        case "多孩子档案、更多拍读额度和周报历史，帮助家长长期看到孩子的进步。":
            return appState.localizedText(
                zhHans: "本机积分用于当前设备的识字和朗读，不上传学习内容，也不承诺跨设备恢复。",
                english: "Local credits are for on-device OCR and read-aloud on this device. Learning content is not uploaded, and cross-device restoration is not promised.",
                japanese: "本機クレジットはこの端末の OCR と読み上げに使用します。学習内容は送信されず、端末間の復元は約束しません。",
                korean: "로컬 크레딧은 이 기기의 OCR 및 읽기 기능에 사용됩니다. 학습 콘텐츠는 업로드되지 않으며 기기 간 복원은 보장하지 않습니다.",
                spanish: "Los creditos locales son para OCR y lectura en este dispositivo. El contenido no se sube y no se promete restauracion entre dispositivos."
            )
        case "一次开通当前家庭版权益，具体扣款以 Apple 确认弹窗为准。", "一次开通当前高级版权益，具体扣款以 Apple 确认弹窗为准。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Activate the current family benefits once; Apple confirms the final charge.",
                japanese: "現在のファミリー特典を一度有効化します。最終的な請求額は Apple の確認画面に従います。",
                korean: "현재 가족 혜택을 한 번 활성화하며, 최종 결제 금액은 Apple 확인 창을 기준으로 합니다.",
                spanish: "Activa los beneficios familiares actuales una vez; Apple confirma el cargo final."
            )
        case "学习内容默认优先保存在本机。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Learning content is saved on this device by default.",
                japanese: "学習内容は標準でこの端末に保存されます。",
                korean: "학습 콘텐츠는 기본적으로 이 기기에 저장됩니다.",
                spanish: "El contenido de aprendizaje se guarda en este dispositivo por defecto."
            )
        case "账号删除、法务文档和客服入口均在 App 内可访问。":
            return appState.localizedText(
                zhHans: "法务文档、支持入口和本地数据删除入口均在家长区内。",
                english: "Legal documents, support, and local data deletion are available inside Parents.",
                japanese: "法務文書、サポート、ローカルデータ削除は保護者エリア内で利用できます。",
                korean: "법적 문서, 지원, 로컬 데이터 삭제는 부모 영역에서 이용할 수 있습니다.",
                spanish: "Los documentos legales, soporte y borrado local estan dentro del area para padres."
            )
        default:
            return normalizedText
        }
    }

    private var healthHint: String {
        appState.uiText("购买、扣款和价格确认由 Apple 完成；本机积分余额保存在当前设备 Keychain。", "Apple handles purchase, charge, and price confirmation; local credit balances are stored in this device's Keychain.")
    }

    private var purchaseButtonTitle: String {
        guard let product = selectedOrFirstProduct else {
            return appState.uiText("请选择积分包", "Select a credit package")
        }
        return appState.uiText("立即购买 ", "Buy now ") + product.displayName + " " + product.displayPrice
    }

}

struct ProductCard: View {
    @EnvironmentObject var appState: AppState
    let product: CreditProduct
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: AppLayout.spacingM) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(product.displayName)
                            .font(AppTypography.headline)
                            .foregroundColor(AppColors.textPrimary)
                        if isSelected {
                            Text(appState.uiText("已选", "Selected"))
                                .font(AppTypography.caption.weight(.semibold))
                                .foregroundColor(AppColors.primary)
                        }
                    }
                    Text("\(product.amount) " + quantityUnitText)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text(product.displayPrice)
                        .font(AppTypography.scaledFont(size: 24, weight: .bold))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                    Text(appState.uiText("Apple 确认", "Apple confirmed"))
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                }
            }
            .padding()
            .background(isSelected ? AppColors.primary.opacity(0.1) : Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous)
                    .stroke(isSelected ? AppColors.primary : AppColors.border, lineWidth: 2)
            )
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusL, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var quantityUnitText: String {
        switch product.quantityUnit ?? "count" {
        case "child":
            return appState.localizedText(zhHans: "个孩子", english: "child slots", japanese: "子ども枠", korean: "자녀 슬롯", spanish: "hijos")
        case "card":
            return appState.localizedText(zhHans: "条句卡", english: "cards", japanese: "カード", korean: "카드", spanish: "tarjetas")
        default:
            return appState.localizedText(zhHans: "积分", english: "credits", japanese: "クレジット", korean: "크레딧", spanish: "creditos")
        }
    }
}

#Preview {
    PaywallView().environmentObject(AppState())
}
