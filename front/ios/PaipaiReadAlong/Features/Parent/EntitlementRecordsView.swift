import SwiftUI

struct EntitlementRecordsView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedStatusFilter: String = "all"
    @State private var page: Int = 1
    @ScaledMetric(relativeTo: .title) private var titleSize: CGFloat = 28
    @ScaledMetric(relativeTo: .subheadline) private var segmentSize: CGFloat = 14
    @ScaledMetric(relativeTo: .headline) private var rowTitleSize: CGFloat = 17
    @ScaledMetric(relativeTo: .body) private var bodySize: CGFloat = 16
    @ScaledMetric(relativeTo: .caption) private var captionSize: CGFloat = 12

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
                creditOverview
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
        // 中文说明：权益详情页统一展示本机功能总积分，取消识字/朗读余额分类。
        [appState.entitlementDisplaySummary(serviceType: "local_device")]
    }

    private func summaryRow(_ summary: EntitlementUsageSummary) -> some View {
        MainCard {
            HStack(spacing: AppLayout.spacingS) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: segmentSize * textScale, weight: .semibold))
                    .foregroundColor(AppColors.primary)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(appState.uiText("本机功能积分", "Local feature credits"))
                        .font(.system(size: segmentSize * textScale, weight: .semibold))
                        .foregroundColor(AppColors.textPrimary)
                    Text(appState.uiText("可用 \(summary.remainingCount) 积分", "\(summary.remainingCount) credits available"))
                        .font(.system(size: captionSize * textScale, weight: .regular))
                        .foregroundColor(AppColors.textSecondary)
                }
                Spacer()
                Text(appState.uiText("已用 \(summary.usedCount)", "\(summary.usedCount) used"))
                    .font(.system(size: rowTitleSize * textScale, weight: .bold))
                    .foregroundColor(AppColors.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
    }

    private var pageHeader: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingXS) {
            Text(appState.uiText("积分权益", "Credit Benefits"))
                .font(.system(size: titleSize * textScale, weight: .bold))
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
        }
        .accessibilityAddTraits(.isHeader)
    }

    private var creditOverview: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack(alignment: .center, spacing: AppLayout.spacingM) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 20 * textScale, weight: .bold))
                        .foregroundColor(AppColors.primary)
                        .frame(width: 42, height: 42)
                        .background(AppColors.primary.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(appState.uiText("当前可用积分", "Available credits"))
                            .font(.system(size: captionSize * textScale, weight: .medium))
                            .foregroundColor(AppColors.textSecondary)
                        Text("\(totalAvailableCredits)")
                            .font(.system(size: 30 * textScale, weight: .bold, design: .rounded))
                            .foregroundColor(AppColors.textPrimary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.65)
                    }
                    Spacer()
                    Text(creditLevelTitle)
                        .font(.system(size: segmentSize * textScale, weight: .semibold))
                        .foregroundColor(AppColors.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(AppColors.primary.opacity(0.1))
                        .clipShape(Capsule())
                }
            }
        }
    }

    private var filterBar: some View {
        HStack(spacing: AppLayout.spacingS) {
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
                Text(appState.localizedText(zhHans: "当前筛选下暂无积分记录", english: "No credit records match this filter", japanese: "この条件に一致するクレジット記録はありません", korean: "이 필터에 맞는 크레딧 기록이 없습니다", spanish: "No hay registros de creditos para este filtro"))
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
                    Text(appState.uiText("剩余 \(record.remainingCount)", "\(record.remainingCount) left"))
                        .font(.system(size: captionSize * textScale, weight: .regular))
                        .foregroundColor(AppColors.primary)
                }

                infoLine(title: appState.uiText("消耗积分", "Consumed"), value: "\(record.usedCount)")
                infoLine(title: appState.uiText("总积分", "Total"), value: "\(record.totalCount)")
                infoLine(title: appState.uiText("获取时间", "Acquired"), value: formatDate(record.acquiredAt))
                infoLine(title: appState.uiText("过期时间", "Expires"), value: formatDate(record.expiresAt))
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
        case "cloud_tts", "cloud_ocr": return appState.uiText("云端功能积分", "Cloud feature credits")
        case "local_device", "local_tts", "device_tts", "local_ocr": return appState.uiText("本机功能积分", "Local feature credits")
        default: return appState.uiText("本机功能积分", "Local feature credits")
        }
    }

    private var localizedAllText: String {
        appState.localizedText(zhHans: "全部", english: "All", japanese: "すべて", korean: "전체", spanish: "Todo")
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

    private var totalAvailableCredits: Int {
        appState.entitlementDisplaySummary(serviceType: "local_device").remainingCount
    }

    private var creditLevelTitle: String {
        switch totalAvailableCredits {
        case 300...:
            return appState.uiText("积分 Lv.3", "Credit Lv.3")
        case 80...:
            return appState.uiText("积分 Lv.2", "Credit Lv.2")
        default:
            return appState.uiText("积分 Lv.1", "Credit Lv.1")
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
            serviceType: nil,
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
