import SwiftUI

struct EntitlementRecordsView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedServiceType: String = "all"
    @State private var selectedStatusFilter: String = "all"
    @State private var page: Int = 1
    @ScaledMetric(relativeTo: .title) private var titleSize: CGFloat = 28
    @ScaledMetric(relativeTo: .subheadline) private var segmentSize: CGFloat = 14
    @ScaledMetric(relativeTo: .headline) private var rowTitleSize: CGFloat = 17
    @ScaledMetric(relativeTo: .body) private var bodySize: CGFloat = 16
    @ScaledMetric(relativeTo: .caption) private var captionSize: CGFloat = 12
    @ScaledMetric(relativeTo: .footnote) private var footnoteSize: CGFloat = 13

    private var currentRecords: [EntitlementRecord] {
        appState.entitlementRecordPage?.records ?? []
    }

    private var textScale: CGFloat {
        appState.textSizeOption.multiplier
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                pageHeader
                filterBar
                retentionNotice
                summarySection
                if currentRecords.isEmpty {
                    emptyState
                } else {
                    ForEach(currentRecords) { record in
                        recordRow(record)
                    }
                    paginationBar
                }
            }
            .padding()
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await loadRecords(reset: true, forceBackendSync: true) }
                } label: {
                    Image(systemName: appState.isEntitlementRecordSyncing ? "arrow.triangle.2.circlepath" : "arrow.clockwise")
                }
                .disabled(appState.isEntitlementRecordSyncing)
                .accessibilityLabel(appState.uiText("刷新权益", "Refresh entitlements"))
            }
        }
        .task {
            await loadRecords(reset: true, forceBackendSync: true)
        }
        .onChange(of: selectedServiceType) { _, _ in
            page = 1
            Task { await loadRecords(reset: true) }
        }
        .onChange(of: selectedStatusFilter) { _, _ in
            page = 1
            Task { await loadRecords(reset: true) }
        }
    }

    private var summarySection: some View {
        let summaries = visibleSummaries
        return VStack(spacing: AppLayout.spacingS) {
            ForEach(summaries, id: \.serviceType) { summary in
                summaryRow(summary)
            }
        }
    }

    private var visibleSummaries: [EntitlementUsageSummary] {
        switch selectedServiceType {
        case "capture":
            return [appState.entitlementDisplaySummary(serviceType: "capture")]
        case "speech":
            return [appState.entitlementDisplaySummary(serviceType: "speech")]
        default:
            return [
                appState.entitlementDisplaySummary(serviceType: "capture"),
                appState.entitlementDisplaySummary(serviceType: "speech")
            ]
        }
    }

    private func summaryRow(_ summary: EntitlementUsageSummary) -> some View {
        MainCard {
            HStack(spacing: AppLayout.spacingS) {
                Image(systemName: summary.serviceType == "speech" ? "speaker.wave.2.fill" : "camera.fill")
                    .font(.system(size: segmentSize * textScale, weight: .semibold))
                    .foregroundColor(AppColors.primary)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(categoryName(for: summary.serviceType))
                        .font(.system(size: segmentSize * textScale, weight: .semibold))
                        .foregroundColor(AppColors.textPrimary)
                    Text(appState.uiText("剩余 \(summary.remainingCount) 次", "\(summary.remainingCount) left"))
                        .font(.system(size: captionSize * textScale, weight: .regular))
                        .foregroundColor(AppColors.textSecondary)
                }
                Spacer()
                Text("\(summary.usedCount)/\(summary.totalCount)")
                    .font(.system(size: rowTitleSize * textScale, weight: .bold))
                    .foregroundColor(AppColors.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
    }

    private var pageHeader: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingXS) {
            Text(appState.uiText("权益信息", "Entitlements"))
                .font(.system(size: titleSize * textScale, weight: .bold))
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
            Text(appState.uiText("查看文字识别、语音朗读等权益的发放、使用和过期记录。", "Review grant, usage, and expiry records for OCR, read-aloud, and related entitlements."))
                .font(.system(size: footnoteSize * textScale, weight: .regular))
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityAddTraits(.isHeader)
    }

    private var retentionNotice: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                Label(appState.localizedText(zhHans: "记录说明", english: "Record notes", japanese: "記録について", korean: "기록 안내", spanish: "Notas de registros"), systemImage: "doc.text.magnifyingglass")
                    .font(.system(size: rowTitleSize * textScale, weight: .semibold))
                    .foregroundColor(AppColors.textPrimary)
                Text(appState.localizedText(
                    zhHans: "这里展示权益、同意和购买摘要相关记录。必要购买凭证仅以哈希和最小账务字段保留，用于退款、税务、反欺诈或争议处理。",
                    english: "This page shows entitlement, consent, and purchase summary records. Necessary purchase evidence is retained only as hashes and minimal accounting fields for refunds, tax, anti-fraud, or disputes.",
                    japanese: "このページには権益、同意、購入概要に関する記録が表示されます。必要な購入証跡のみ、返金、税務、不正防止、紛争対応のためにハッシュと最小会計項目で保持されます。",
                    korean: "이 페이지에는 권한, 동의, 구매 요약 관련 기록이 표시됩니다. 필요한 구매 증빙만 환불, 세무, 부정 방지, 분쟁 처리를 위해 해시와 최소 회계 항목으로 보관됩니다.",
                    spanish: "Esta pagina muestra registros de beneficios, consentimientos y resumenes de compra. Las pruebas de compra necesarias se conservan solo como hashes y campos contables minimos para reembolsos, impuestos, antifraude o disputas."
                ))
                .font(.system(size: footnoteSize * textScale, weight: .regular))
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var filterBar: some View {
        HStack(spacing: AppLayout.spacingS) {
            filterMenu(
                title: appState.localizedText(zhHans: "类型", english: "Type", japanese: "タイプ", korean: "유형", spanish: "Tipo"),
                selectionTitle: serviceFilterTitle,
                systemImage: "square.grid.2x2",
                options: [
                    ("all", localizedAllText),
                    ("capture", ocrText),
                    ("speech", ttsText)
                ],
                selection: $selectedServiceType
            )
            filterMenu(
                title: appState.localizedText(zhHans: "状态", english: "Status", japanese: "状態", korean: "상태", spanish: "Estado"),
                selectionTitle: statusFilterTitle,
                systemImage: "line.3.horizontal.decrease.circle",
                options: [
                    ("all", localizedAllText),
                    ("active", activeStatusText),
                    ("invalid", invalidStatusText)
                ],
                selection: $selectedStatusFilter
            )
        }
    }

    private func filterMenu(title: String, selectionTitle: String, systemImage: String, options: [(String, String)], selection: Binding<String>) -> some View {
        Menu {
            ForEach(options, id: \.0) { value, optionTitle in
                Button {
                    selection.wrappedValue = value
                } label: {
                    if selection.wrappedValue == value {
                        Label(optionTitle, systemImage: "checkmark")
                    } else {
                        Text(optionTitle)
                    }
                }
            }
        } label: {
            HStack(spacing: AppLayout.spacingXS) {
                Image(systemName: systemImage)
                    .font(.system(size: captionSize * textScale, weight: .semibold))
                    .foregroundColor(AppColors.primary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: captionSize * textScale, weight: .regular))
                        .foregroundColor(AppColors.textSecondary)
                    Text(selectionTitle)
                        .font(.system(size: segmentSize * textScale, weight: .semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
                Spacer(minLength: AppLayout.spacingXS)
                Image(systemName: "chevron.down")
                    .font(.system(size: captionSize * textScale, weight: .semibold))
                    .foregroundColor(AppColors.textSecondary)
            }
            .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget)
            .padding(.horizontal, AppLayout.spacingS)
            .padding(.vertical, 6)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                    .stroke(AppColors.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var emptyState: some View {
        MainCard {
            VStack(spacing: AppLayout.spacingS) {
                if appState.isEntitlementRecordSyncing {
                    ProgressView()
                }
                Text(appState.localizedText(zhHans: "当前筛选下暂无权益记录", english: "No entitlement records match this filter", japanese: "この条件に一致する権益記録はありません", korean: "이 필터에 맞는 권한 기록이 없습니다", spanish: "No hay registros de beneficios para este filtro"))
                    .font(.system(size: bodySize * textScale, weight: .regular))
                    .foregroundColor(AppColors.textSecondary)
            }
            .frame(maxWidth: .infinity, minHeight: 120)
        }
    }

    private func recordRow(_ record: EntitlementRecord) -> some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingS) {
                HStack {
                    Text(categoryName(for: record.serviceType))
                        .font(.system(size: rowTitleSize * textScale, weight: .semibold))
                    if isExpired(record) {
                        Text(invalidStatusText)
                            .font(.system(size: captionSize * textScale, weight: .regular))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(AppColors.textSecondary)
                            .clipShape(Capsule())
                    }
                    Spacer()
                    Text("\(record.usedCount)/\(record.totalCount)")
                        .font(.system(size: captionSize * textScale, weight: .regular))
                        .foregroundColor(AppColors.primary)
                }

                Text(localizedAcquireMethod(record.acquireMethod))
                    .font(.system(size: bodySize * textScale, weight: .regular))
                    .foregroundColor(AppColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                infoLine(title: appState.uiText("获取时间", "Acquired"), value: formatDate(record.acquiredAt))
                infoLine(title: appState.uiText("过期时间", "Expires"), value: formatDate(record.expiresAt))
                infoLine(title: appState.uiText("剩余次数", "Remaining"), value: "\(record.remainingCount)")
                if shouldShowProductCode(for: record) {
                    let productCode = record.productCode ?? ""
                    infoLine(title: appState.uiText("商品编码", "Product"), value: productCode)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var paginationBar: some View {
        HStack(spacing: AppLayout.spacingS) {
            Button {
                page = max(page - 1, 1)
                Task { await loadRecords(reset: false) }
            } label: {
                Label(appState.uiText("上一页", "Previous"), systemImage: "chevron.left")
                    .font(.system(size: captionSize * textScale, weight: .regular))
                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget)
            }
            .buttonStyle(.bordered)
            .disabled(page <= 1)

            Text(appState.uiText("第 \(page) 页", "Page \(page)"))
                .font(.system(size: captionSize * textScale, weight: .regular))
                .foregroundColor(AppColors.textSecondary)
                .frame(minWidth: 64)

            Button {
                guard appState.entitlementRecordPage?.hasMore == true else { return }
                page += 1
                Task { await loadRecords(reset: false) }
            } label: {
                Label(appState.uiText("下一页", "Next"), systemImage: "chevron.right")
                    .font(.system(size: captionSize * textScale, weight: .regular))
                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget)
            }
            .buttonStyle(.bordered)
            .disabled(appState.entitlementRecordPage?.hasMore != true)
        }
    }

    private func infoLine(title: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.system(size: captionSize * textScale, weight: .regular))
                .foregroundColor(AppColors.textSecondary)
            Spacer()
            Text(value)
                .font(.system(size: captionSize * textScale, weight: .regular))
                .foregroundColor(AppColors.textPrimary)
                .multilineTextAlignment(.trailing)
        }
    }

    private func categoryName(for serviceType: String) -> String {
        switch serviceType {
        case "cloud_tts": return ttsText + " · " + appState.uiText("云端", "Cloud")
        case "local_tts", "device_tts", "speech": return ttsText + " · " + appState.uiText("本地", "Local")
        case "cloud_ocr": return ocrText + " · " + appState.uiText("云端", "Cloud")
        case "local_ocr", "capture": return ocrText + " · " + appState.uiText("本地", "Local")
        default: return ocrText
        }
    }

    private var localizedAllText: String {
        appState.localizedText(zhHans: "全部", english: "All", japanese: "すべて", korean: "전체", spanish: "Todo")
    }

    private var serviceFilterTitle: String {
        switch selectedServiceType {
        case "capture": return ocrText
        case "speech": return ttsText
        default: return localizedAllText
        }
    }

    private var statusFilterTitle: String {
        switch selectedStatusFilter {
        case "active": return activeStatusText
        case "invalid": return invalidStatusText
        default: return localizedAllText
        }
    }

    private var activeStatusText: String {
        appState.localizedText(zhHans: "有效", english: "Valid", japanese: "有効", korean: "유효", spanish: "Vigente")
    }

    private var invalidStatusText: String {
        appState.localizedText(zhHans: "失效", english: "Invalid", japanese: "無効", korean: "만료", spanish: "No vigente")
    }

    private var ocrText: String {
        appState.localizedText(zhHans: "文字识别", english: "OCR", japanese: "文字認識", korean: "문자 인식", spanish: "OCR")
    }

    private var ttsText: String {
        appState.localizedText(zhHans: "语音朗读", english: "TTS", japanese: "音声読み上げ", korean: "음성 읽기", spanish: "Lectura en voz")
    }

    private func localizedAcquireMethod(_ acquireMethod: String) -> String {
        switch acquireMethod {
        case "每日赠送":
            return appState.localizedText(zhHans: "每日赠送", english: "Daily grant", japanese: "毎日の付与", korean: "일일 지급", spanish: "Concesion diaria")
        case "内部购买":
            return appState.localizedText(zhHans: "内部购买", english: "In-app purchase", japanese: "アプリ内購入", korean: "앱 내 구매", spanish: "Compra en la app")
        case "后台赠送":
            return appState.localizedText(zhHans: "后台赠送", english: "Admin grant", japanese: "管理者付与", korean: "관리자 지급", spanish: "Concesion administrativa")
        case "权益赠送":
            return appState.localizedText(zhHans: "权益赠送", english: "Entitlement grant", japanese: "権益付与", korean: "권한 지급", spanish: "Concesion de beneficio")
        default:
            return acquireMethod
        }
    }

    private func isExpired(_ record: EntitlementRecord) -> Bool {
        guard let date = parsedDate(record.expiresAt) else { return false }
        return date <= Date()
    }

    private func shouldShowProductCode(for record: EntitlementRecord) -> Bool {
        guard let productCode = record.productCode, !productCode.isEmpty else { return false }
        return record.grantType != "daily_grant" && record.grantType != "daily_gift" && productCode != "daily_free_quota"
    }

    private func formatDate(_ raw: String) -> String {
        guard !raw.isEmpty else { return appState.uiText("未知", "Unknown") }
        guard let date = parsedDate(raw) else {
            return raw.replacingOccurrences(of: "T", with: " ").replacingOccurrences(of: "Z", with: "")
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: appState.isEnglishInterface ? "en_US" : "zh_Hans_CN")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: date)
    }

    private func parsedDate(_ raw: String) -> Date? {
        let normalized = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        if let date = fractional.date(from: normalized) ?? plain.date(from: normalized) {
            return date
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        for format in [
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXXXX",
            "yyyy-MM-dd'T'HH:mmXXXXX",
            "yyyy-MM-dd HH:mm:ssXXXXX",
            "yyyy-MM-dd HH:mmXXXXX"
        ] {
            formatter.dateFormat = format
            if let date = formatter.date(from: normalized) {
                return date
            }
        }
        return nil
    }

    private func loadRecords(reset: Bool, forceBackendSync: Bool = false) async {
        if reset {
            page = 1
        }
        await appState.refreshEntitlementRecords(
            serviceType: selectedServiceType == "all" ? nil : selectedServiceType,
            statusFilter: selectedStatusFilter == "all" ? nil : selectedStatusFilter,
            page: page,
            pageSize: 20,
            forceBackendSync: forceBackendSync
        )
    }
}

#Preview {
    NavigationStack { EntitlementRecordsView().environmentObject(AppState()) }
}
