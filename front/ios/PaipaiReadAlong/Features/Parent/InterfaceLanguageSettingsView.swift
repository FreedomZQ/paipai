import SwiftUI

/// Legacy wrapper kept only for compatibility while routing all settings through LanguagePreferenceView.
struct InterfaceLanguageSettingsView: View {
    var body: some View {
        LanguagePreferenceView()
    }
}
