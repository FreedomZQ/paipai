import SwiftUI
import WebKit

struct LegalDocsView: View {
    @EnvironmentObject var appState: AppState
    let documents: [LegalDocument]

    private var visibleDocuments: [LegalDocument] {
        documents.filter { $0.resolvedURL != nil }
    }

    var body: some View {
        Group {
            if visibleDocuments.isEmpty {
                ScrollView {
                    MainCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(appState.uiText("法律文档", "Legal documents"))
                                .font(AppTypography.title3)
                            Text(appState.uiText("法律文档暂时无法打开，请稍后再试。", "Legal documents are temporarily unavailable. Please try again later."))
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(20)
                    .adaptiveContentFrame(maxWidth: AppLayout.readableMaxWidth)
                }
                .background(AppColors.background)
            } else {
                List(visibleDocuments) { document in
                    if let url = document.resolvedURL {
                        NavigationLink {
                            LegalDocumentWebView(url: url)
                                .navigationTitle(title(for: document.type))
                                .navigationBarTitleDisplayMode(.inline)
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(title(for: document.type))
                                    .font(AppTypography.headline)
                                Text(subtitle(for: document))
                                    .font(AppTypography.footnote)
                                    .foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                            .padding(.vertical, 6)
                            .contentShape(Rectangle())
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
                .background(AppColors.background)
            }
        }
        .navigationTitle(appState.uiText("法律文档", "Legal Documents"))
    }

    private func subtitle(for document: LegalDocument) -> String {
        if document.url.hasPrefix("bundle://legal/") {
            return appState.uiText("App 内置版本，联网失败时仍可查看", "Bundled version, available offline")
        }
        return document.url
    }

    private func title(for type: String) -> String {
        switch type {
        case "privacy": return appState.uiText("隐私政策", "Privacy Policy")
        case "terms": return appState.uiText("用户协议", "Terms of Service")
        case "child_data": return appState.uiText("儿童信息处理说明", "Child Data Policy")
        default: return type
        }
    }
}

#if os(iOS)
struct LegalDocumentWebView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.allowsBackForwardNavigationGestures = true
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        load(url, in: webView)
    }
}
#elseif os(macOS)
struct LegalDocumentWebView: NSViewRepresentable {
    let url: URL

    func makeNSView(context: Context) -> WKWebView {
        WKWebView()
    }

    func updateNSView(_ webView: WKWebView, context: Context) {
        load(url, in: webView)
    }
}
#endif

private func load(_ url: URL, in webView: WKWebView) {
    if url.isFileURL {
        webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
    } else {
        webView.load(URLRequest(url: url))
    }
}
