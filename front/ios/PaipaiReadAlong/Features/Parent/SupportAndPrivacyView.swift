import SwiftUI

struct SupportAndPrivacyView: View {
    @EnvironmentObject var appState: AppState

    private var supportEmail: String? {
        guard let raw = appState.bootstrap.supportEmail?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }
        return raw
    }

    private var supportPageURL: URL? {
        guard let raw = appState.bootstrap.supportUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              let url = URL(string: raw),
              url.scheme?.lowercased() == "https" else { return nil }
        return url
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                MainCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(appState.uiText("支持与隐私", "Support & Privacy"))
                            .font(AppTypography.title3)
                        Text(appState.uiText("这里集中放家长最关心的几件事：联系客服、查看法律文档、了解反馈方式。", "Here are the key parent tools: contact support, open legal documents, and learn how to send feedback."))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                MainCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(appState.uiText("联系客服", "Contact support"))
                            .font(AppTypography.headline)
                        if let supportEmail, let mailURL = URL(string: "mailto:\(supportEmail)") {
                            Link(destination: mailURL) {
                                Label(supportEmail, systemImage: "envelope")
                                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            }
                        }
                        if let supportPageURL {
                            Link(destination: supportPageURL) {
                                Label(supportPageURL.absoluteString, systemImage: "link")
                                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            }
                        }
                        NavigationLink {
                            LegalDocsView(documents: appState.legalDocs)
                        } label: {
                            Label(appState.uiText("查看法律文档", "View legal documents"), systemImage: "doc.text")
                                .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                MainCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(appState.uiText("问题反馈", "Feedback"))
                            .font(AppTypography.headline)
                        Text(feedbackInstruction)
                            .font(AppTypography.body)
                            .foregroundColor(AppColors.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        if let supportEmail, let mailURL = URL(string: "mailto:\(supportEmail)?subject=\(mailSubject)") {
                            Link(destination: mailURL) {
                                Label(supportEmail, systemImage: "envelope.fill")
                                    .font(AppTypography.body.weight(.semibold))
                                    .foregroundColor(AppColors.primary)
                                    .frame(maxWidth: .infinity, minHeight: AppLayout.minimumTapTarget, alignment: .leading)
                            }
                        } else {
                            Text(appState.uiText("反馈邮箱暂未配置，请稍后再试。", "The feedback email is not configured yet. Please try again later."))
                                .font(AppTypography.footnote)
                                .foregroundColor(AppColors.textTertiary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(20)
            .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
        }
        .background(AppColors.background)
        .navigationTitle(appState.uiText("隐私与支持", "Privacy & Support"))
    }

    private var feedbackInstruction: String {
        guard let supportEmail else {
            return appState.uiText(
                "当前版本暂不提供直接填写反馈的入口。请通过邮箱向我们发送问题描述、设备型号和 App 版本，便于我们定位处理。",
                "This version does not include an in-app feedback form. Please send the issue, device model, and app version by email so we can investigate."
            )
        }
        return appState.uiText(
            "当前版本暂不提供直接填写反馈的入口。请将问题描述、设备型号和 App 版本发送至 \(supportEmail)，我们会通过邮件跟进。",
            "This version does not include an in-app feedback form. Please email the issue, device model, and app version to \(supportEmail), and we will follow up by email."
        )
    }

    private var mailSubject: String {
        appState.uiText("拍拍伴读问题反馈", "Paipai ReadAlong Feedback")
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
    }
}
