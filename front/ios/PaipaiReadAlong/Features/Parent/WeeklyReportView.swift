import SwiftUI

struct WeeklyReportView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var selectedChildId: String = ""
    @State private var reports: [LocalWeeklyReportRecord] = []
    @State private var selectedReportId: String?
    @State private var isLoading = false
    private let initialChildId: String?
    private let initialReportId: String?
    private let showsCloseButton: Bool

    init(initialChildId: String? = nil, initialReportId: String? = nil, showsCloseButton: Bool = false) {
        self.initialChildId = initialChildId
        self.initialReportId = initialReportId
        self.showsCloseButton = showsCloseButton
        _selectedChildId = State(initialValue: initialChildId ?? "")
        _selectedReportId = State(initialValue: initialReportId)
    }

    private var selectedRecord: LocalWeeklyReportRecord? {
        reports.first { $0.id == selectedReportId } ?? reports.first
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                pageHeader

                if isLoading {
                    ProgressView(appState.uiText("正在加载周报...", "Loading weekly report..."))
                        .padding()
                        .frame(maxWidth: .infinity, minHeight: 220)
                } else if let selectedRecord {
                    reportContent(selectedRecord)
                } else {
                    emptyReportContent
                }
            }
            .padding(20)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle(appState.uiText("阅读周报", "Weekly Reading Report"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            if selectedChildId.isEmpty {
                selectedChildId = initialChildId ?? appState.latestUnreadWeeklyReport?.childId ?? appState.selectedChild.id
            }
            await loadReports()
        }
        .onChange(of: selectedChildId) { _, _ in
            Task { await loadReports() }
        }
        .onChange(of: selectedReportId) { _, newValue in
            guard let newValue else { return }
            Task { await appState.markWeeklyReportOpened(reportId: newValue) }
        }
    }

    private var pageHeader: some View {
        HStack {
            pageBackButton
            Spacer()
            Text(appState.uiText("阅读周报", "Weekly Reading Report"))
                .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Spacer()
            Color.clear.frame(width: 44, height: 44)
        }
    }

    private var pageBackButton: some View {
        Button {
            dismiss()
        } label: {
            Image(systemName: "chevron.left")
                .font(AppTypography.scaledFont(size: 17, weight: .semibold))
                .foregroundColor(AppColors.textPrimary)
                .frame(width: 44, height: 44)
                .background(Color.white)
                .clipShape(Circle())
                .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(showsCloseButton ? appState.uiText("关闭", "Close") : appState.uiText("返回", "Back"))
    }

    private func reportContent(_ record: LocalWeeklyReportRecord) -> some View {
        return VStack(spacing: 16) {
            reportHero(record)
            controlPanel
            overviewSection(record)
            trendSection(record.report)
            analysisSection(record)
            suggestionsSection(record.report)
        }
    }

    private func reportHero(_ record: LocalWeeklyReportRecord) -> some View {
        let report = record.report
        return GradientCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(record.weekStart) ~ \(record.weekEnd)")
                            .font(AppTypography.caption.weight(.bold))
                            .foregroundColor(.white.opacity(0.9))
                        Text(reportText(
                            "\(record.childName)的本周伴读回顾",
                            "\(record.childName)'s weekly reading recap",
                            ja: "\(record.childName)の今週の読み聞かせ振り返り",
                            ko: "\(record.childName)의 이번 주 함께 읽기 회고",
                            es: "Resumen semanal de lectura de \(record.childName)"
                        ))
                        .font(AppTypography.scaledFont(size: 28, weight: .heavy))
                        .foregroundColor(.white)
                        .lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                        Text(reportFocusText(report))
                            .font(AppTypography.footnote)
                            .foregroundColor(.white.opacity(0.94))
                            .lineSpacing(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 10)
                    Text(childAvatar(for: record))
                        .font(AppTypography.scaledFont(size: 34))
                        .foregroundColor(.white)
                        .frame(width: 58, height: 58)
                        .background(.white.opacity(0.22))
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                }

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                    heroMetric(reportText("本周伴读时长", "Read-along time", ja: "今週の読書時間", ko: "이번 주 함께 읽기 시간", es: "Tiempo de lectura"), formatMinutes(totalMinutes(report)))
                    heroMetric(reportText("活跃天数", "Active days", ja: "利用日数", ko: "활동 일수", es: "Días activos"), "\(report.stats.weeklyActiveDays)" + reportText("天", " days", ja: "日", ko: "일", es: " días"))
                    heroMetric(reportText("连续习惯", "Reading streak", ja: "連続習慣", ko: "연속 습관", es: "Racha"), "\(report.stats.currentStreakDays)" + reportText("天", " days", ja: "日", ko: "일", es: " días"))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .shadow(color: Color.black.opacity(0.12), radius: 16, x: 0, y: 4)
    }

    private func heroMetric(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(AppTypography.caption)
                .foregroundColor(.white.opacity(0.86))
            Text(value)
                .font(AppTypography.title3)
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
        .frame(maxWidth: .infinity, minHeight: 66, alignment: .leading)
        .padding(10)
        .background(.white.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var controlPanel: some View {
        ReportSectionCard(padding: 14) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 12) {
                    childSelector.frame(maxWidth: .infinity, alignment: .leading)
                    historySelector.frame(maxWidth: .infinity, alignment: .leading)
                }
                VStack(alignment: .leading, spacing: 12) {
                    childSelector
                    historySelector
                }
            }
        }
    }

    private var childSelector: some View {
        VStack(alignment: .leading, spacing: 7) {
            fieldLabel(reportText("孩子", "Child", ja: "子ども", ko: "아이", es: "Niño"))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(appState.children) { child in
                        Button {
                            selectedChildId = child.id
                        } label: {
                            HStack(spacing: 6) {
                                Text(childAvatar(for: child.id))
                                Text(child.nickname)
                                    .lineLimit(1)
                            }
                            .font(AppTypography.subheadline.weight(.bold))
                            .foregroundColor(selectedChildId == child.id ? AppColors.primary : AppColors.textSecondary)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 40)
                            .background(selectedChildId == child.id ? Color(hex: "#F0FFF9") : Color(hex: "#F5F5F5"))
                            .overlay(
                                Capsule()
                                    .stroke(selectedChildId == child.id ? AppColors.primary.opacity(0.24) : Color.clear, lineWidth: 1)
                            )
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var historySelector: some View {
        VStack(alignment: .leading, spacing: 7) {
            fieldLabel(reportText("历史周报", "Past reports", ja: "過去の週報", ko: "지난 리포트", es: "Informes anteriores"))
            Menu {
                ForEach(reports) { record in
                    Button {
                        selectedReportId = record.id
                    } label: {
                        Text("\(record.weekStart) ~ \(record.weekEnd)")
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Text(selectedRecord.map { "\($0.weekStart) ~ \($0.weekEnd)" } ?? reportText("暂无周报", "No report", ja: "週報なし", ko: "리포트 없음", es: "Sin informe"))
                        .font(AppTypography.subheadline.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.down")
                        .font(AppTypography.caption.weight(.bold))
                        .foregroundColor(AppColors.textTertiary)
                }
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(AppColors.border, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .buttonStyle(.plain)
        }
    }

    private func overviewSection(_ record: LocalWeeklyReportRecord) -> some View {
        let report = record.report
        return ReportSectionCard {
            VStack(alignment: .leading, spacing: 14) {
                sectionHeader(
                    title: reportText("报告概览", "Report overview", ja: "レポート概要", ko: "리포트 개요", es: "Resumen del informe"),
                    note: reportText("生成于 \(displayGeneratedAt(record))", "Generated \(displayGeneratedAt(record))", ja: "\(displayGeneratedAt(record)) 生成", ko: "\(displayGeneratedAt(record)) 생성", es: "Generado \(displayGeneratedAt(record))")
                )
                Text(reportSummaryText(record))
                    .font(AppTypography.bodySmall)
                    .foregroundColor(AppColors.textSecondary)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(hex: "#FFF9E6"))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 155), spacing: 12)], spacing: 12) {
                    statCard(title: reportText("伴读时长", "Reading time", ja: "読書時間", ko: "읽기 시간", es: "Tiempo"), value: formatMinutes(totalMinutes(report)), unit: "", desc: reportText("本周有效陪读累计时间", "Total effective read-along time this week", ja: "今週の有効な読み聞かせ合計時間", ko: "이번 주 유효 함께 읽기 누적 시간", es: "Tiempo total de lectura acompañada"), icon: "clock.fill")
                    statCard(title: reportText("阅读频率", "Reading frequency", ja: "読書頻度", ko: "읽기 빈도", es: "Frecuencia"), value: "\(report.stats.weeklyActiveDays)", unit: reportText("天", " days", ja: "日", ko: "일", es: " días"), desc: reportText("本周有伴读记录的天数", "Days with read-along records", ja: "読み聞かせ記録がある日数", ko: "함께 읽기 기록이 있는 날", es: "Días con registros"), icon: "calendar")
                    statCard(title: reportText("本周复习", "Reviews", ja: "今週の復習", ko: "이번 주 복습", es: "Repasos"), value: "\(report.stats.weeklyReviewCount)", unit: reportText("次", " times", ja: "回", ko: "회", es: " veces"), desc: reportText("句卡与阅读内容复习次数", "Sentence-card and reading review count", ja: "カードと読書内容の復習回数", ko: "문장 카드와 읽기 내용 복습 횟수", es: "Repasos de tarjetas y lectura"), icon: "sparkles")
                    statCard(title: reportText("新增句卡", "New cards", ja: "新規カード", ko: "새 카드", es: "Tarjetas nuevas"), value: "\(report.stats.weeklySavedCardCount ?? report.stats.displaySavedCardCount)", unit: reportText("张", "", ja: "枚", ko: "장", es: ""), desc: reportText("拍照识图后保存的重点句卡", "Key cards saved after recognition", ja: "認識後に保存した重要カード", ko: "인식 후 저장한 핵심 카드", es: "Tarjetas guardadas"), icon: "bookmark.fill")
                    statCard(title: reportText("拍照识图", "Photo recognition", ja: "写真認識", ko: "사진 인식", es: "Reconocimiento"), value: "\(captureCount(report))", unit: reportText("次", " times", ja: "回", ko: "회", es: " veces"), desc: reportText("设备端与云端 OCR 使用合计", "On-device and cloud OCR total", ja: "端末内とクラウドOCRの合計", ko: "기기와 클라우드 OCR 합계", es: "OCR local y nube"), icon: "camera.fill")
                    statCard(title: reportText("语音朗读", "Read aloud", ja: "音声読み上げ", ko: "음성 낭독", es: "Lectura en voz alta"), value: "\(speechCount(report))", unit: reportText("次", " times", ja: "回", ko: "회", es: " veces"), desc: reportText("设备端与云端语音播放合计", "On-device and cloud speech total", ja: "端末内とクラウド音声の合計", ko: "기기와 클라우드 음성 합계", es: "Voz local y nube"), icon: "speaker.wave.2.fill")
                    statCard(title: reportText("待复习", "Due cards", ja: "復習待ち", ko: "복습 대기", es: "Pendientes"), value: "\(report.stats.reviewDueCount ?? 0)", unit: reportText("张", "", ja: "枚", ko: "장", es: ""), desc: reportText("建议下周优先处理的句卡", "Cards to prioritize next week", ja: "来週優先して扱うカード", ko: "다음 주 우선 처리할 카드", es: "Tarjetas prioritarias"), icon: "checkmark.circle.fill")
                }
            }
        }
    }

    private func trendSection(_ report: WeeklyParentReport) -> some View {
        ReportSectionCard {
            VStack(alignment: .leading, spacing: 14) {
                sectionHeader(
                    title: reportText("阅读趋势", "Reading trend", ja: "読書トレンド", ko: "읽기 추세", es: "Tendencia"),
                    note: reportText("按本地周统计展示", "Shown by local week", ja: "現地週で表示", ko: "현지 주 기준 표시", es: "Semana local")
                )
                VStack(alignment: .leading, spacing: 10) {
                    dailyBarChart(report)
                    HStack(spacing: 14) {
                        legend(color: AppColors.secondary, text: reportText("每日伴读时长", "Daily reading time", ja: "毎日の読書時間", ko: "일일 읽기 시간", es: "Tiempo diario"))
                        legend(color: AppColors.warning, text: reportText("建议保持 20 分钟以上", "Target at least 20 min", ja: "20分以上を推奨", ko: "20분 이상 권장", es: "Meta: 20 min"))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(14)
                .background(Color(hex: "#F9F9F9"))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color(hex: "#F2F2F2"), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
    }

    private func analysisSection(_ record: LocalWeeklyReportRecord) -> some View {
        let report = record.report
        let previous = previousRecord(for: record)?.report
        return ReportSectionCard {
            VStack(alignment: .leading, spacing: 14) {
                sectionHeader(title: reportText("数据解读", "Data insights", ja: "データ解釈", ko: "데이터 해석", es: "Interpretación"))
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 190), spacing: 12)], spacing: 12) {
                    analysisCard(
                        label: reportText("较上周时长", "Time vs last week", ja: "前週比の時間", ko: "지난주 대비 시간", es: "Tiempo vs. semana anterior"),
                        value: signedDelta(totalMinutes(report), previous.map(totalMinutes), unit: reportText("分", " min", ja: "分", ko: "분", es: " min")),
                        text: reportText("用于判断本周陪读总量变化，避免只看单日高峰。", "Compares total reading time instead of one-day peaks.", ja: "単日のピークだけでなく合計時間の変化を見ます。", ko: "하루 최고치가 아닌 전체 읽기 시간 변화를 봅니다.", es: "Compara el total, no solo un pico diario.")
                    )
                    analysisCard(
                        label: reportText("复习变化", "Review change", ja: "復習の変化", ko: "복습 변화", es: "Cambio de repaso"),
                        value: signedDelta(report.stats.weeklyReviewCount, previous?.stats.weeklyReviewCount, unit: reportText("次", " times", ja: "回", ko: "회", es: " veces")),
                        text: reportText("复习次数反映句卡是否被持续回看，是沉淀效果的重要参考。", "Reviews show whether saved cards are revisited consistently.", ja: "保存カードが継続して見直されているかを示します。", ko: "저장한 카드를 꾸준히 다시 보는지 보여줍니다.", es: "Muestra si las tarjetas se revisan de forma constante.")
                    )
                    let rhythm = rhythmObservation(report)
                    analysisCard(
                        label: reportText("节奏稳定度", "Rhythm stability", ja: "リズムの安定度", ko: "리듬 안정도", es: "Estabilidad"),
                        value: rhythm.value,
                        text: rhythm.text
                    )
                }
            }
        }
    }

    private func suggestionsSection(_ report: WeeklyParentReport) -> some View {
        ReportSectionCard {
            VStack(alignment: .leading, spacing: 14) {
                sectionHeader(
                    title: reportText("下周重点建议", "Next-week focus", ja: "来週の重点提案", ko: "다음 주 핵심 제안", es: "Enfoque de la próxima semana"),
                    note: reportText("按本周数据规则生成", "Generated from this week's rules", ja: "今週のデータルールで生成", ko: "이번 주 데이터 규칙으로 생성", es: "Según reglas de esta semana")
                )
                VStack(spacing: 10) {
                    ForEach(Array(nextActions(report).enumerated()), id: \.offset) { _, item in
                        suggestionRow(icon: item.icon, title: item.title, text: item.text)
                    }
                }
            }
        }
    }

    private var emptyReportContent: some View {
        VStack(spacing: 16) {
            emptyHero
            emptyReasonCard
            emptyGuideCard
            emptyPreviewCard
        }
    }

    private var emptyHero: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(Color.white.opacity(0.16))
                .frame(width: 170, height: 170)
                .offset(x: 54, y: 62)

            ViewThatFits(in: .horizontal) {
                emptyHeroContent(isCompact: false)
                emptyHeroContent(isCompact: true)
            }
            .padding(AppLayout.spacingXL)
        }
        .background(AppGradients.primary)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: Color.black.opacity(0.12), radius: 16, x: 0, y: 4)
    }

    private func emptyHeroContent(isCompact: Bool) -> some View {
        let textBlock = VStack(alignment: isCompact ? .center : .leading, spacing: 10) {
            Text(reportText("本周数据准备中", "Weekly data is preparing", ja: "今週のデータを準備中", ko: "이번 주 데이터를 준비 중", es: "Preparando los datos de la semana"))
                .font(AppTypography.caption.weight(.bold))
                .foregroundColor(.white.opacity(0.92))
            Text(reportText("再读几天，就能看到第一份阅读周报", "Read for a few more days to see the first weekly reading report", ja: "あと数日読むと、最初の読書週報を確認できます", ko: "며칠 더 읽으면 첫 읽기 주간 리포트를 볼 수 있어요", es: "Lee unos días más para ver el primer informe semanal de lectura"))
                .font(AppTypography.scaledFont(size: isCompact ? 26 : 32, weight: .heavy))
                .foregroundColor(.white)
                .lineSpacing(2)
                .multilineTextAlignment(isCompact ? .center : .leading)
                .fixedSize(horizontal: false, vertical: true)
            Text(reportText("周报会在孩子完成一整周的伴读记录后生成，用来回顾阅读时长、活跃天数和复习情况。", "The report is generated after a complete week of read-along records, covering reading time, active days, and review progress.", ja: "週報は、1週間分の読み聞かせ記録がそろった後に生成され、読書時間、利用日数、復習状況を振り返ります。", ko: "주간 리포트는 한 주의 함께 읽기 기록이 쌓인 뒤 생성되며, 읽기 시간, 활동 일수, 복습 상황을 돌아봅니다.", es: "El informe se genera tras una semana completa de lectura acompañada y resume tiempo de lectura, días activos y progreso de repaso."))
                .font(AppTypography.footnote)
                .foregroundColor(.white.opacity(0.94))
                .lineSpacing(2)
                .multilineTextAlignment(isCompact ? .center : .leading)
                .fixedSize(horizontal: false, vertical: true)
        }

        let mascot = Image("PaipaiLoadingIcon")
            .resizable()
            .scaledToFit()
            .frame(width: isCompact ? 78 : 100, height: isCompact ? 78 : 100)
            .padding(isCompact ? 14 : 16)
            .background(Color.white.opacity(0.22))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .shadow(color: AppColors.info.opacity(0.18), radius: 20, x: 0, y: 8)

        return Group {
            if isCompact {
                VStack(spacing: 18) {
                    mascot
                    textBlock
                }
                .frame(maxWidth: .infinity)
            } else {
                HStack(spacing: 18) {
                    textBlock
                        .frame(maxWidth: 520, alignment: .leading)
                    Spacer(minLength: 16)
                    mascot
                }
            }
        }
    }

    private var emptyReasonCard: some View {
        MainCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "info")
                    .font(AppTypography.scaledFont(size: 20, weight: .bold))
                    .foregroundColor(AppColors.info)
                    .frame(width: 44, height: 44)
                    .background(Color(hex: "#FFF9E6"))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                VStack(alignment: .leading, spacing: 6) {
                    Text(reportText("为什么现在还没有周报？", "Why is there no report yet?", ja: "なぜまだ週報がないのですか？", ko: "왜 아직 리포트가 없나요?", es: "¿Por qué aún no hay informe?"))
                        .font(AppTypography.headline)
                        .foregroundColor(AppColors.textPrimary)
                    Text(reportText("这是你使用拍拍伴读的第一周，当前还没有完整的自然周阅读数据。系统需要先累计一周内的伴读、复习和句卡记录，才能生成可参考的阅读周报。", "This is your first week using Paipai Read Along, so there is not a complete calendar week of reading data yet. The app first needs a week of read-along, review, and card records before it can generate a useful weekly reading report.", ja: "拍拍伴读を使い始めて最初の週のため、まだ自然週の読書データがそろっていません。参考になる読書週報を作成するには、1週間分の読み聞かせ、復習、カード記録が必要です。", ko: "拍拍伴读을 사용하는 첫 주라 아직 완전한 자연 주간 읽기 데이터가 없습니다. 참고할 수 있는 읽기 주간 리포트를 만들려면 한 주 동안의 함께 읽기, 복습, 문장 카드 기록이 먼저 쌓여야 합니다.", es: "Es tu primera semana usando Paipai Read Along, por lo que todavía no hay datos de una semana natural completa. La app necesita acumular lectura acompañada, repasos y tarjetas durante una semana para generar un informe semanal útil."))
                        .font(AppTypography.footnote)
                        .foregroundColor(AppColors.textSecondary)
                        .lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var emptyGuideCard: some View {
        MainCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(reportText("如何获得周报", "How to get a report", ja: "週報を受け取るには", ko: "리포트를 받는 방법", es: "Cómo obtener el informe"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 190), spacing: 10)], spacing: 10) {
                    emptyStep(index: 1, title: reportText("每天完成伴读", "Read together each day", ja: "毎日読み聞かせを完了", ko: "매일 함께 읽기 완료", es: "Completa la lectura diaria"), text: reportText("进入“拍拍伴读”拍照识图，陪孩子完成当天阅读内容。", "Open Capture & Recognize and complete the day's reading with your child.", ja: "「拍拍伴读」で写真から文字を認識し、その日の読書を一緒に完了します。", ko: "“拍拍伴读”에서 사진으로 글자를 인식하고 아이와 그날의 읽기를 완료하세요.", es: "Abre Captura y reconocimiento y completa la lectura del día con tu hijo."))
                    emptyStep(index: 2, title: reportText("保留学习记录", "Keep learning records", ja: "学習記録を残す", ko: "학습 기록 남기기", es: "Conserva los registros"), text: reportText("朗读、保存句卡和复习都会成为周报分析的基础数据。", "Read-aloud, saved cards, and reviews all become the basis for the report.", ja: "読み上げ、カード保存、復習が週報分析の基礎データになります。", ko: "낭독, 문장 카드 저장, 복습이 모두 리포트 분석의 기본 데이터가 됩니다.", es: "La lectura en voz alta, las tarjetas guardadas y los repasos alimentan el informe."))
                    emptyStep(index: 3, title: reportText("完成一周后查看", "Check after one week", ja: "1週間後に確認", ko: "한 주 후 확인하기", es: "Revísalo tras una semana"), text: reportText("一周结束后再次进入“阅读周报”，即可查看孩子的伴读回顾。", "After the week ends, open Weekly Reading Report again to view the read-along recap.", ja: "1週間が終わったら「読書週報」を再度開き、お子さまの読み聞かせ振り返りを確認できます。", ko: "한 주가 끝난 뒤 “읽기 주간 리포트”에 다시 들어가면 아이의 함께 읽기 회고를 볼 수 있습니다.", es: "Al terminar la semana, vuelve a abrir el informe semanal para ver el resumen."))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func emptyStep(index: Int, title: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(index)")
                .font(AppTypography.caption.weight(.heavy))
                .foregroundColor(AppColors.secondary)
                .frame(width: 32, height: 32)
                .background(Color(hex: "#F0FFF9"))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(AppTypography.subheadline.weight(.bold))
                    .foregroundColor(AppColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(text)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 92, alignment: .topLeading)
        .padding(12)
        .background(Color(hex: "#F9F9F9"))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(hex: "#F2F2F2"), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var emptyPreviewCard: some View {
        MainCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(reportText("周报将展示", "The report will show", ja: "週報に表示される内容", ko: "리포트에 표시되는 내용", es: "El informe mostrará"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                    emptyPreview(icon: "⏱", text: reportText("本周伴读时长与活跃天数", "Read-along time and active days", ja: "今週の読書時間と利用日数", ko: "이번 주 함께 읽기 시간과 활동 일수", es: "Tiempo de lectura y días activos"), color: Color(hex: "#E8F5FF"))
                    emptyPreview(icon: "📈", text: reportText("每天阅读节奏变化", "Daily reading rhythm changes", ja: "日ごとの読書リズムの変化", ko: "매일 읽기 리듬 변화", es: "Cambios diarios del ritmo de lectura"), color: Color(hex: "#F0FFF9"))
                    emptyPreview(icon: "✨", text: reportText("复习表现和下周建议", "Review progress and next-week suggestions", ja: "復習状況と翌週の提案", ko: "복습 성과와 다음 주 제안", es: "Progreso de repaso y sugerencias"), color: Color(hex: "#FFF9E6"))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func emptyPreview(icon: String, text: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Spacer(minLength: 0)
                Text(icon)
                    .font(AppTypography.scaledFont(size: 18))
                    .frame(width: 24, height: 24, alignment: .center)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, minHeight: 24, alignment: .center)
            Text(text)
                .font(AppTypography.caption.weight(.bold))
                .foregroundColor(AppColors.textSecondary)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .topLeading)
        .padding(12)
        .background(color)
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(AppColors.info.opacity(0.08), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func fieldLabel(_ text: String) -> some View {
        Text(text)
            .font(AppTypography.caption.weight(.semibold))
            .foregroundColor(AppColors.textSecondary)
    }

    private func sectionHeader(title: String, note: String? = nil) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(title)
                    .font(AppTypography.headline.weight(.heavy))
                    .foregroundColor(AppColors.textPrimary)
                Spacer(minLength: 12)
                if let note {
                    Text(note)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(AppTypography.headline.weight(.heavy))
                    .foregroundColor(AppColors.textPrimary)
                if let note {
                    Text(note)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func statCard(title: String, value: String, unit: String, desc: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                Text(title)
                    .font(AppTypography.caption.weight(.bold))
                    .foregroundColor(AppColors.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                Spacer(minLength: 8)
                Image(systemName: icon)
                    .font(AppTypography.caption.weight(.bold))
                    .foregroundColor(AppColors.primary)
                    .frame(width: 28, height: 28)
                    .background(Color(hex: "#F0FFF9"))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value)
                    .font(AppTypography.scaledFont(size: 24, weight: .heavy))
                    .foregroundColor(AppColors.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                if !unit.isEmpty {
                    Text(unit)
                        .font(AppTypography.caption.weight(.bold))
                        .foregroundColor(AppColors.textSecondary)
                        .lineLimit(1)
                }
            }
            Text(desc)
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textTertiary)
                .lineSpacing(1)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
        .padding(14)
        .background(Color(hex: "#F9F9F9"))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(hex: "#F2F2F2"), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func dailyBarChart(_ report: WeeklyParentReport) -> some View {
        let values = paddedDailyMinutes(report)
        let maxValue = max(values.max() ?? 0, 20, 1)
        let labels = dayLabels()
        return ZStack(alignment: .topLeading) {
            GeometryReader { proxy in
                let lineY = max(0, proxy.size.height - (proxy.size.height * CGFloat(20) / CGFloat(maxValue)))
                Path { path in
                    path.move(to: CGPoint(x: 0, y: lineY))
                    path.addLine(to: CGPoint(x: proxy.size.width, y: lineY))
                }
                .stroke(AppColors.warning, style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
            }
            .frame(height: 154)

            HStack(alignment: .bottom, spacing: 8) {
                ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                    VStack(spacing: 7) {
                        Text(value > 0 ? "\(value)" + reportText("分", "m", ja: "分", ko: "분", es: "m") : "-")
                            .font(AppTypography.caption.weight(.bold))
                            .foregroundColor(AppColors.textSecondary)
                            .frame(height: 16)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        GeometryReader { proxy in
                            VStack(spacing: 0) {
                                Spacer(minLength: 0)
                                RoundedRectangle(cornerRadius: 8, style: .continuous)
                                    .fill(value >= 20 ? AppColors.secondary : AppColors.warning)
                                    .frame(height: max(4, proxy.size.height * CGFloat(value) / CGFloat(maxValue)))
                            }
                        }
                        .frame(height: 122)
                        Text(labels.indices.contains(index) ? labels[index] : "")
                            .font(AppTypography.caption.weight(.bold))
                            .foregroundColor(AppColors.textTertiary)
                            .frame(height: 14)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 180)
        }
        .frame(height: 180)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(reportText("每日伴读时长柱状图", "Daily reading time bar chart", ja: "毎日の読書時間の棒グラフ", ko: "일일 읽기 시간 막대 차트", es: "Gráfico de tiempo diario"))
    }

    private func legend(color: Color, text: String) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 9, height: 9)
            Text(text)
                .font(AppTypography.caption)
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func analysisCard(label: String, value: String, text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(AppTypography.caption.weight(.heavy))
                .foregroundColor(AppColors.textSecondary)
            Text(value)
                .font(AppTypography.scaledFont(size: 22, weight: .heavy))
                .foregroundColor(deltaColor(value))
                .lineLimit(1)
                .minimumScaleFactor(0.68)
            Text(text)
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 126, alignment: .topLeading)
        .padding(14)
        .background(Color(hex: "#F9F9F9"))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(hex: "#F2F2F2"), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func suggestionRow(icon: String, title: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(icon)
                .font(AppTypography.scaledFont(size: 16))
                .frame(width: 32, height: 32)
                .background(Color(hex: "#F0FFF9"))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(AppTypography.subheadline.weight(.heavy))
                    .foregroundColor(AppColors.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(text)
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textSecondary)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .padding(12)
        .background(Color(hex: "#F9F9F9"))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color(hex: "#F2F2F2"), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func totalMinutes(_ report: WeeklyParentReport) -> Int {
        paddedDailyMinutes(report).reduce(0, +)
    }

    private func paddedDailyMinutes(_ report: WeeklyParentReport) -> [Int] {
        let values = report.modules.first(where: { $0.code == "daily_minutes" })?.payload["values"]?.intArray ?? []
        if values.count >= 7 { return Array(values.prefix(7)) }
        return values + Array(repeating: 0, count: 7 - values.count)
    }

    private func captureCount(_ report: WeeklyParentReport) -> Int {
        moduleInt(report, keys: ["captures", "capture_count", "ocr_count", "photo_recognition_count"])
    }

    private func speechCount(_ report: WeeklyParentReport) -> Int {
        moduleInt(report, keys: ["speech", "speech_count", "tts_count", "read_aloud_count"])
    }

    private func moduleInt(_ report: WeeklyParentReport, keys: [String]) -> Int {
        for module in report.modules {
            for key in keys {
                if let value = module.payload[key]?.intValue { return value }
            }
        }
        return 0
    }

    private func previousRecord(for record: LocalWeeklyReportRecord) -> LocalWeeklyReportRecord? {
        guard let index = reports.firstIndex(where: { $0.id == record.id }),
              reports.indices.contains(index + 1) else {
            return nil
        }
        return reports[index + 1]
    }

    private func signedDelta(_ current: Int, _ previous: Int?, unit: String) -> String {
        guard let previous else {
            return reportText("暂无上周数据", "No last-week data", ja: "前週データなし", ko: "지난주 데이터 없음", es: "Sin datos previos")
        }
        let delta = current - previous
        if delta == 0 {
            return reportText("与上周持平", "Same as last week", ja: "前週と同じ", ko: "지난주와 같음", es: "Igual que la semana anterior")
        }
        return "\(delta > 0 ? "+" : "")\(delta)\(unit)"
    }

    private func rhythmObservation(_ report: WeeklyParentReport) -> (value: String, text: String) {
        if report.stats.weeklyActiveDays >= 5 && report.stats.currentStreakDays >= 3 {
            return (
                reportText("节奏连续", "Consistent rhythm", ja: "リズムが継続", ko: "연속적인 리듬", es: "Ritmo constante"),
                reportText("本周多天有伴读记录，且连续陪读保持较好，适合继续沿用当前固定时段。", "Reading happened across several days and the streak is strong, so the current fixed time works well.", ja: "複数日に記録があり、連続性も良好です。現在の固定時間を続けるのに適しています。", ko: "여러 날 기록이 있고 연속성도 좋아 현재의 고정 시간을 유지하기 좋습니다.", es: "Hubo lectura varios días y una buena racha; conviene mantener el horario actual.")
            )
        }
        if report.stats.weeklyActiveDays >= 3 {
            return (
                reportText("节奏形成中", "Rhythm forming", ja: "リズム形成中", ko: "리듬 형성 중", es: "Ritmo en formación"),
                reportText("本周已有基础伴读频率，建议先稳定固定天数，再逐步增加时长。", "A basic rhythm is forming; stabilize the days first, then increase duration.", ja: "基本的な頻度ができています。まず日数を安定させ、その後時間を増やしましょう。", ko: "기본 빈도가 만들어지고 있으니 먼저 요일을 안정시키고 시간을 늘리세요.", es: "Ya hay una base; estabiliza los días antes de aumentar tiempo.")
            )
        }
        return (
            reportText("需要重启节奏", "Needs a restart", ja: "リズム再開が必要", ko: "리듬 재시작 필요", es: "Necesita reinicio"),
            reportText("本周伴读天数偏少，建议先安排短时、低压力的陪读任务。", "Reading days were limited; start with short, low-pressure sessions.", ja: "日数が少なめです。短く負担の少ない読み聞かせから始めましょう。", ko: "읽기 일수가 적어 짧고 부담 낮은 활동부터 시작하세요.", es: "Hubo pocos días; empieza con sesiones breves y ligeras.")
        )
    }

    private func reportFocusText(_ report: WeeklyParentReport) -> String {
        if (report.stats.reviewDueCount ?? 0) >= 10 {
            return reportText("当前重点是减少待复习积压，建议采用“复习在前、新内容在后”的陪读结构。", "Focus on reducing due-card backlog: review first, new reading after.", ja: "復習待ちを減らすことが重点です。復習を先に、新しい内容を後にしましょう。", ko: "복습 대기를 줄이는 것이 핵심입니다. 복습 먼저, 새 내용은 뒤에 진행하세요.", es: "Reduce tarjetas pendientes: primero repaso, luego lectura nueva.")
        }
        if report.stats.currentStreakDays >= 3 {
            return reportText("当前重点是保持连续陪读节奏，用固定时段帮助孩子形成稳定预期。", "Focus on keeping the streak with a fixed reading time.", ja: "固定時間で連続した読み聞かせリズムを保ちましょう。", ko: "고정 시간으로 연속 읽기 리듬을 유지하세요.", es: "Mantén la racha con un horario fijo.")
        }
        return reportText("当前重点是恢复参与感，先从短片段识图和语音朗读开始。", "Focus on rebuilding participation with short recognition and read-aloud moments.", ja: "短い認識と読み上げから参加感を戻しましょう。", ko: "짧은 인식과 음성 낭독으로 참여감을 회복하세요.", es: "Recupera participación con lectura breve y reconocimiento.")
    }

    private func reportSummaryText(_ record: LocalWeeklyReportRecord) -> String {
        let report = record.report
        let active = report.stats.weeklyActiveDays
        let reviews = report.stats.weeklyReviewCount
        let cards = report.stats.displaySavedCardCount
        let minutes = formatMinutes(totalMinutes(report))
        return reportText(
            "\(record.childName) 本周伴读 \(minutes)，有 \(active) 天保持记录，共完成 \(reviews) 次复习，句卡累计 \(cards) 张。\(reportFocusText(report))",
            "\(record.childName) read for \(minutes) this week, stayed active on \(active) day\(active == 1 ? "" : "s"), completed \(reviews) review\(reviews == 1 ? "" : "s"), and has \(cards) saved card\(cards == 1 ? "" : "s"). \(reportFocusText(report))",
            ja: "\(record.childName)は今週 \(minutes) 読み、\(active)日記録を残し、\(reviews)回復習し、カードは\(cards)枚です。\(reportFocusText(report))",
            ko: "\(record.childName)은 이번 주 \(minutes) 읽고, \(active)일 활동했으며, \(reviews)회 복습했고, 저장 카드가 \(cards)장입니다. \(reportFocusText(report))",
            es: "\(record.childName) leyó \(minutes) esta semana, estuvo activo \(active) día\(active == 1 ? "" : "s"), completó \(reviews) repaso\(reviews == 1 ? "" : "s") y tiene \(cards) tarjeta\(cards == 1 ? "" : "s") guardada\(cards == 1 ? "" : "s"). \(reportFocusText(report))"
        )
    }

    private func nextActions(_ report: WeeklyParentReport) -> [(title: String, text: String, icon: String)] {
        if report.stats.weeklyActiveDays <= 2 {
            return [
                (reportText("先恢复节奏", "Restart rhythm", ja: "リズムを再開", ko: "리듬 회복", es: "Recupera ritmo"), reportText("下周先安排 3 次轻量陪读，每次 10 到 15 分钟。", "Plan three light sessions next week, 10 to 15 minutes each.", ja: "来週は10〜15分の軽い読み聞かせを3回入れましょう。", ko: "다음 주 10-15분 가벼운 읽기를 3회 계획하세요.", es: "Programa tres sesiones ligeras de 10 a 15 minutos."), "🕘"),
                (reportText("降低任务门槛", "Lower the bar", ja: "ハードルを下げる", ko: "문턱 낮추기", es: "Baja la exigencia"), reportText("每次只完成 1 个短片段和 1 张句卡，先让孩子重新熟悉流程。", "Do one short passage and one card per session to rebuild familiarity.", ja: "毎回短い一節とカード1枚だけにして流れに慣れましょう。", ko: "매번 짧은 구절 1개와 카드 1장만 진행하세요.", es: "Haz un fragmento breve y una tarjeta por sesión."), "📖"),
                (reportText("观察参与方式", "Observe preference", ja: "参加方法を観察", ko: "참여 방식 관찰", es: "Observa preferencias"), reportText("记录孩子更愿意听、读还是复习，连续 2 周后再增加任务量。", "Track whether listening, reading, or review works best before increasing load.", ja: "聞く、読む、復習のどれが合うか2週間見てから増やしましょう。", ko: "듣기, 읽기, 복습 중 무엇을 좋아하는지 2주 관찰하세요.", es: "Observa si prefiere escuchar, leer o repasar antes de aumentar."), "💬")
            ]
        }
        return [
            (reportText("保持节奏", "Keep rhythm", ja: "リズム維持", ko: "리듬 유지", es: "Mantén ritmo"), reportText("安排 4 到 5 天陪读，每次控制在 15 到 25 分钟。", "Read together 4 to 5 days, 15 to 25 minutes each.", ja: "4〜5日、各15〜25分を目安にしましょう。", ko: "4-5일, 매번 15-25분 함께 읽으세요.", es: "Lee 4 a 5 días, 15 a 25 minutos cada vez."), "🕘"),
            (reportText("复习优先", "Review first", ja: "復習優先", ko: "복습 우선", es: "Repaso primero"), reportText("每次结束前复习 2 到 3 张句卡，让新内容及时沉淀。", "Review two or three cards before ending each session.", ja: "毎回終わる前にカードを2〜3枚復習しましょう。", ko: "매번 끝나기 전 카드 2-3장을 복습하세요.", es: "Repasa dos o tres tarjetas al final."), "✅"),
            (reportText("平衡使用", "Balance use", ja: "使い方を調整", ko: "균형 있게 사용", es: "Equilibra uso"), reportText("下周可搭配识图、朗读和复习交替进行。", "Alternate recognition, read-aloud, and review next week.", ja: "来週は認識、読み上げ、復習を交互に使いましょう。", ko: "다음 주 인식, 낭독, 복습을 번갈아 진행하세요.", es: "Alterna reconocimiento, lectura y repaso."), "📖")
        ]
    }

    private func formatMinutes(_ minutes: Int) -> String {
        let hours = minutes / 60
        let rest = minutes % 60
        if hours == 0 {
            return "\(rest)" + reportText("分钟", " min", ja: "分", ko: "분", es: " min")
        }
        if rest == 0 {
            return "\(hours)" + reportText("小时", " hr", ja: "時間", ko: "시간", es: " h")
        }
        return "\(hours)" + reportText("小时", " hr ", ja: "時間", ko: "시간", es: " h ") + "\(rest)" + reportText("分", "min", ja: "分", ko: "분", es: "min")
    }

    private func dayLabels() -> [String] {
        [
            reportText("周一", "Mon", ja: "月", ko: "월", es: "Lun"),
            reportText("周二", "Tue", ja: "火", ko: "화", es: "Mar"),
            reportText("周三", "Wed", ja: "水", ko: "수", es: "Mié"),
            reportText("周四", "Thu", ja: "木", ko: "목", es: "Jue"),
            reportText("周五", "Fri", ja: "金", ko: "금", es: "Vie"),
            reportText("周六", "Sat", ja: "土", ko: "토", es: "Sáb"),
            reportText("周日", "Sun", ja: "日", ko: "일", es: "Dom")
        ]
    }

    private func displayGeneratedAt(_ record: LocalWeeklyReportRecord) -> String {
        record.report.generatedAt?.isEmpty == false ? (record.report.generatedAt ?? record.generatedAt) : record.generatedAt
    }

    private func childAvatar(for record: LocalWeeklyReportRecord) -> String {
        childAvatar(for: record.childId)
    }

    private func childAvatar(for childId: String) -> String {
        let avatars = ["🌟", "🚀", "🎈", "📚", "✨", "🌈"]
        let ids = appState.children.map(\.id)
        let index = ids.firstIndex(of: childId) ?? abs(childId.hashValue % avatars.count)
        return avatars[index % avatars.count]
    }

    private func deltaColor(_ value: String) -> Color {
        if value.hasPrefix("+") { return AppColors.secondary }
        if value.hasPrefix("-") { return AppColors.error }
        return AppColors.textPrimary
    }

    private func reportText(_ zhHans: String, _ english: String, ja: String, ko: String, es: String) -> String {
        appState.localizedText(zhHans: zhHans, english: english, japanese: ja, korean: ko, spanish: es)
    }

    private func loadReports() async {
        isLoading = true
        defer { isLoading = false }
        reports = await appState.localWeeklyReportHistory(childId: selectedChildId)
        if let current = selectedReportId, reports.contains(where: { $0.id == current }) {
            selectedReportId = current
        } else if let initialReportId, reports.contains(where: { $0.id == initialReportId }) {
            selectedReportId = initialReportId
        } else {
            selectedReportId = reports.first?.id
        }
        if let id = selectedReportId {
            await appState.markWeeklyReportOpened(reportId: id)
        }
    }
}

private extension PowerSyncPayloadValue {
    var intArray: [Int] {
        guard case let .array(values) = self else { return [] }
        return values.compactMap { value in
            switch value {
            case let .int(raw): return raw
            case let .double(raw): return Int(raw)
            default: return nil
            }
        }
    }

    var intValue: Int? {
        switch self {
        case let .int(raw): return raw
        case let .double(raw): return Int(raw)
        default: return nil
        }
    }
}

private struct ReportSectionCard<Content: View>: View {
    let padding: CGFloat
    let content: Content

    init(padding: CGFloat = 18, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 2)
    }
}
