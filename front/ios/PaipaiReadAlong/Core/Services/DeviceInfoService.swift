import Foundation
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
import Darwin
#endif

// MARK: - Device Type
enum DeviceType: String, CaseIterable {
    case iPhone = "iPhone"
    case iPad = "iPad"
    case mac = "Mac"
    
    var displayName: String {
        switch self {
        case .iPhone:
            return "iPhone"
        case .iPad:
            return "iPad"
        case .mac:
            return "Mac"
        }
    }
}

// MARK: - Device Info
struct DeviceInfo {
    let deviceType: DeviceType
    let model: String
    let systemVersion: String
    let systemName: String
    let appVersion: String
    let buildNumber: String
    
    var fullDescription: String {
        return "\(deviceType.displayName) - \(model) - \(systemName) \(systemVersion)"
    }
}

// MARK: - Device Info Service
final class DeviceInfoService {
    
    var currentDeviceInfo: DeviceInfo {
        DeviceInfo(
            deviceType: getDeviceType(),
            model: getModel(),
            systemVersion: getSystemVersion(),
            systemName: getSystemName(),
            appVersion: getAppVersion(),
            buildNumber: getBuildNumber()
        )
    }
    
    // MARK: - Private Methods
    
    private func getDeviceType() -> DeviceType {
        #if os(iOS)
        if UIDevice.current.userInterfaceIdiom == .pad {
            return .iPad
        } else {
            return .iPhone
        }
        #elseif os(macOS)
        return .mac
        #else
        return .iPhone
        #endif
    }
    
    private func getModel() -> String {
        #if os(iOS)
        return UIDevice.current.model
        #elseif os(macOS)
        var size = 0
        sysctlbyname("hw.model", nil, &size, nil, 0)
        var model = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.model", &model, &size, nil, 0)
        return String(cString: model)
        #else
        return "Unknown"
        #endif
    }
    
    private func getSystemVersion() -> String {
        #if os(iOS)
        return UIDevice.current.systemVersion
        #elseif os(macOS)
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
        #else
        return "Unknown"
        #endif
    }
    
    private func getSystemName() -> String {
        #if os(iOS)
        return UIDevice.current.systemName
        #elseif os(macOS)
        return "macOS"
        #else
        return "Unknown"
        #endif
    }
    
    private func getAppVersion() -> String {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
    
    private func getBuildNumber() -> String {
        return Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }
    
    // MARK: - Privacy Consent
    
    private let appDefaults = AppScopedDefaults()
    
    var hasAcceptedPrivacyConsent: Bool {
        get {
            appDefaults.bool(forKey: AppDefaultKey.deviceInfoCollectionAccepted)
        }
        set {
            appDefaults.set(newValue, forKey: AppDefaultKey.deviceInfoCollectionAccepted)
        }
    }
    
    func requestPrivacyConsent() async -> Bool {
        if let accepted = appDefaults.object(forKey: AppDefaultKey.privacyPolicyAccepted) as? Bool {
            hasAcceptedPrivacyConsent = accepted
            return accepted
        }
        return hasAcceptedPrivacyConsent
    }
}
