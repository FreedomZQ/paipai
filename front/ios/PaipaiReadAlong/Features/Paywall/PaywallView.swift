import SwiftUI

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState
    @State private var selectedProduct: CreditProduct?
    @State private var isLoading = false

    /// 前端购买页只展示数据库判定为激活且可购买的选项。
    /// 后端仍会返回 disabled 商品供接口层保留精细化禁购能力，但 App 页面不展示这些置灰项。
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
        // 付款按钮必须同时满足：后端在线、商品未被数据库禁用、当前是正式家长账号。
        selectedOrFirstProduct?.enabled == true
            && appState.billingHealth?.purchaseAvailable == true
            && appState.authMode == .formalAccount
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: AppLayout.spacingXL) {
                    headerSection
                    introSection
                    if hasConfiguredProducts {
                        healthSection
                    }
                    if hasConfiguredProducts && !localizedTrustBullets.isEmpty {
                        trustCard
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

    private var introSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.localizedText(
                    zhHans: "资源包能力",
                    english: "Resource Pack Features",
                    japanese: "リソースパック機能",
                    korean: "리소스 팩 기능",
                    spanish: "Funciones de paquetes"
                ))
                .font(AppTypography.headline)
                .foregroundColor(AppColors.textPrimary)
                ForEach(Array(resourceIntroItems.enumerated()), id: \.offset) { _, item in
                    HStack(alignment: .top, spacing: AppLayout.spacingS) {
                        Image(systemName: item.icon)
                            .font(AppTypography.scaledFont(size: 16, weight: .semibold))
                            .foregroundColor(AppColors.primary)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title)
                                .font(AppTypography.bodySmall.weight(.semibold))
                                .foregroundColor(AppColors.textPrimary)
                            Text(item.subtitle)
                                .font(AppTypography.caption)
                                .foregroundColor(AppColors.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                if !hasConfiguredProducts {
                    Text(appState.localizedText(
                        zhHans: "当前后端未启用任何可购买资源包，页面仅展示功能介绍。",
                        english: "No purchasable resource packs are enabled by the backend, so this page only shows feature information.",
                        japanese: "バックエンドで購入可能なリソースパックが有効化されていないため、機能紹介のみ表示します。",
                        korean: "백엔드에서 구매 가능한 리소스 팩이 활성화되지 않아 기능 소개만 표시합니다.",
                        spanish: "El backend no tiene paquetes comprables activos, por lo que esta página solo muestra información."
                    ))
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
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

    private var trustCard: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                Text(appState.uiText("购买说明", "Purchase notes"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                ForEach(localizedTrustBullets.prefix(4), id: \.self) { item in
                    HStack(alignment: .top, spacing: AppLayout.spacingS) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(AppTypography.scaledFont(size: 14))
                            .foregroundColor(AppColors.success)
                            .padding(.top, 2)
                        Text(item)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var productListSection: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingM) {
            HStack {
                Text(appState.localizedText(
                    zhHans: "可购买资源包",
                    english: "Purchasable packages",
                    japanese: "購入可能なパック",
                    korean: "구매 가능한 패키지",
                    spanish: "Paquetes disponibles"
                ))
                    .font(AppTypography.headline)
                Spacer()
                Text(appState.localizedText(
                    zhHans: "同一类别每天最多购买 5 次",
                    english: "Up to 5 purchases per category per day",
                    japanese: "同じカテゴリは1日5回まで購入できます",
                    korean: "같은 카테고리는 하루 최대 5회 구매할 수 있습니다",
                    spanish: "Hasta 5 compras por categoría al día"
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
                    let success = await appState.purchaseInternal(product: product)
                    isLoading = false
                    if success { dismiss() }
                }
            }
            .disabled(!canPurchaseSelectedProduct)

            Button(appState.uiText("刷新权益", "Refresh entitlements")) {
                Task {
                    await appState.performFullEntitlementSync(reason: "purchase_page_refresh")
                }
            }
            .font(AppTypography.body)
            .foregroundColor(AppColors.primary)
            .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget)
        }
    }

    private var footerSection: some View {
        VStack(spacing: 6) {
            Text(footerText)
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textTertiary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(appState.uiText("购买成功后会按商品类型发放本地或云端权益。删除云端账号会停止账号权益并清理可删除数据；必要购买凭证按 Apple、退款、税务和反欺诈规则最小保留，退款仍需走 App Store 流程。", "After purchase, local or cloud entitlements are granted according to the product type. Deleting the cloud account stops account entitlements and clears deletable data; necessary purchase evidence is minimally retained under Apple, refund, tax, and anti-fraud rules. Refunds still follow the App Store process."))
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
                zhHans: "购买次数包",
                english: "Buy credit packages",
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
                zhHans: "商品、额度和有效期都由后端实时配置。",
                english: "Products, amounts, and expiry are configured by the backend.",
                japanese: "商品、回数、有効期限はバックエンドでリアルタイムに設定されます。",
                korean: "상품, 수량, 유효기간은 백엔드에서 실시간으로 설정됩니다.",
                spanish: "Los productos, cantidades y vencimientos se configuran desde el backend."
            )
        )
    }

    private var localizedTrustBullets: [String] {
        appState.bootstrap.paywall.trustBullets
            .map { localizedPaywallCopy($0, fallback: $0) }
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private func localizedPaywallCopy(_ text: String, fallback: String) -> String {
        let normalizedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty else { return fallback }

        switch normalizedText {
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
                zhHans: normalizedText,
                english: "Child profiles, more capture credits and weekly report history help parents follow each child's progress over time.",
                japanese: "子どもプロフィール、より多くの撮影読取枠、クラウド同期、週報履歴で、保護者が長期的な成長を確認できます。",
                korean: "자녀 프로필, 더 많은 촬영 읽기 한도, 클라우드 동기화, 주간 리포트 기록으로 부모가 아이의 성장을 꾸준히 확인할 수 있습니다.",
                spanish: "Los perfiles infantiles, más créditos de captura, la sincronización en la nube y el historial semanal ayudan a seguir el progreso de cada niño."
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
                japanese: "学習内容は標準でこの端末に保存され、クラウド同期は保護者が有効にします。",
                korean: "학습 콘텐츠는 기본적으로 이 기기에 저장되며, 클라우드 동기화는 부모가 직접 켭니다.",
                spanish: "El contenido de aprendizaje se guarda en este dispositivo por defecto; la sincronización en la nube la activa un adulto."
            )
        case "账号删除、法务文档和客服入口均在 App 内可访问。":
            return appState.localizedText(
                zhHans: normalizedText,
                english: "Account deletion, legal documents, and support are all available in the app.",
                japanese: "アカウント削除、法務文書、サポート窓口はすべてアプリ内から利用できます。",
                korean: "계정 삭제, 법적 문서, 고객 지원은 모두 앱 안에서 이용할 수 있습니다.",
                spanish: "La eliminación de cuenta, los documentos legales y el soporte están disponibles dentro de la app."
            )
        default:
            return normalizedText
        }
    }

    private var healthHint: String {
        appState.uiText("后端不可用时仅限制购买，不影响已获权益的正常使用。", "When the backend is unavailable, only purchasing is blocked; existing entitlements still work.")
    }

    private var purchaseButtonTitle: String {
        guard let product = selectedOrFirstProduct else {
            return appState.uiText("请选择次数包", "Select a package")
        }
        return appState.uiText("立即购买 ", "Buy now ") + product.displayName + " " + product.displayPrice
    }

    private var footerText: String {
        if !hasConfiguredProducts {
            return appState.localizedText(
                zhHans: "后端启用资源包后，本页会自动展示购买项目。",
                english: "When resource packs are enabled by the backend, purchasable items will appear here automatically.",
                japanese: "バックエンドでリソースパックが有効になると、購入項目が自動的に表示されます。",
                korean: "백엔드에서 리소스 팩이 활성화되면 구매 항목이 자동으로 표시됩니다.",
                spanish: "Cuando el backend active paquetes, aparecerán aquí automáticamente."
            )
        }
        if appState.billingHealth?.purchaseAvailable != true {
            return appState.billingHealth?.unavailableMessage ?? appState.uiText("暂时无法购买", "Purchasing is temporarily unavailable.")
        }
        if appState.authMode != .formalAccount {
            return appState.uiText("请先在家长区完成登录，再发起购买。", "Please sign in from the parent area before purchasing.")
        }
        return appState.uiText("购买会先经过后端校验，再立即发放权益。", "Purchases are checked by the backend first, then granted immediately.")
    }

    private var resourceIntroItems: [(icon: String, title: String, subtitle: String)] {
        [
            (
                "speaker.wave.2.fill",
                appState.localizedText(zhHans: "语音次数包", english: "Voice credits", japanese: "音声クレジット", korean: "음성 크레딧", spanish: "Créditos de voz"),
                appState.localizedText(zhHans: "补充朗读次数，适合高频跟读练习。", english: "Add read-aloud credits for frequent practice.", japanese: "音読練習用の回数を追加します。", korean: "반복 읽기 연습용 횟수를 추가합니다.", spanish: "Añade créditos para practicar lectura en voz alta.")
            ),
            (
                "text.viewfinder",
                appState.localizedText(zhHans: "图片识别次数包", english: "Image recognition credits", japanese: "画像認識クレジット", korean: "이미지 인식 크레딧", spanish: "Créditos OCR"),
                appState.localizedText(zhHans: "补充拍照识别次数，方便保存新句子。", english: "Add OCR credits for capturing new sentences.", japanese: "新しい文を保存するための画像認識回数を追加します。", korean: "새 문장을 저장하기 위한 인식 횟수를 추가합니다.", spanish: "Añade créditos OCR para guardar nuevas frases.")
            ),
            (
                "person.2.fill",
                appState.localizedText(zhHans: "孩子个数扩展", english: "Child slot extensions", japanese: "子ども枠拡張", korean: "자녀 슬롯 확장", spanish: "Extensión de hijos"),
                appState.localizedText(zhHans: "扩展可管理的孩子档案数量。", english: "Increase the number of child profiles you can manage.", japanese: "管理できる子どもプロフィール数を増やします。", korean: "관리 가능한 자녀 프로필 수를 늘립니다.", spanish: "Aumenta la cantidad de perfiles de hijos.")
            ),
            (
                "rectangle.stack.fill",
                appState.localizedText(zhHans: "句卡容量扩展", english: "Sentence card storage", japanese: "フレーズカード容量", korean: "문장 카드 용량", spanish: "Capacidad de tarjetas"),
                appState.localizedText(zhHans: "扩展句卡记录上限，保留更多复习内容。", english: "Increase the card limit to keep more review content.", japanese: "復習カードの保存上限を増やします。", korean: "더 많은 복습 내용을 저장할 수 있도록 한도를 늘립니다.", spanish: "Aumenta el límite para guardar más contenido.")
            )
        ]
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
                    Text(product.displayDescription?.isEmpty == false ? product.displayDescription ?? "" : product.localizedCategoryName)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(product.amount) " + quantityUnitText + appState.uiText(" · 有效期 ", " · Valid for ") + "\(product.validDays)" + appState.uiText(" 天", " days"))
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
                    Text(appState.uiText("后端配置价", "Backend configured"))
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
            return appState.localizedText(zhHans: "次", english: "credits", japanese: "回", korean: "회", spanish: "créditos")
        }
    }
}

#Preview {
    PaywallView().environmentObject(AppState())
}
