import Foundation
import os
#if canImport(AVFoundation)
import AVFoundation
#endif

enum TTSPlaybackMode {
    case device
    case cloud
}

struct TTSPlaybackResult {
    let mode: TTSPlaybackMode
    let fellBackToDevice: Bool
    let cloudReceipt: CloudSpeechReceipt?
}

enum TTSServiceError: LocalizedError {
    case backendUnavailable
    case cloudQuotaBlocked(String)
    case cloudSynthesisFailed(String)
    case missingCloudAudio
    case invalidCloudAudio
    case audioSessionConfigurationFailed(String)

    var errorDescription: String? {
        switch self {
        case .backendUnavailable:
            return "Cloud TTS requires an authenticated backend session."
        case let .cloudQuotaBlocked(message):
            return message
        case let .cloudSynthesisFailed(message):
            return message
        case .missingCloudAudio:
            return "Cloud TTS succeeded but no audio payload was returned."
        case .invalidCloudAudio:
            return "The cloud TTS audio payload is invalid and could not be played."
        case let .audioSessionConfigurationFailed(message):
            return "Audio session configuration failed: \(message)"
        }
    }
}

enum AudioSessionError: LocalizedError {
    case categorySetupFailed(Error?)
    case activationFailed(Error?)
    case interruptionOccurred
    case configurationConflict

    var errorDescription: String? {
        switch self {
        case .categorySetupFailed(let error):
            return "Failed to configure audio session: \(error?.localizedDescription ?? "Unknown error")"
        case .activationFailed(let error):
            return "Failed to activate audio session: \(error?.localizedDescription ?? "Unknown error")"
        case .interruptionOccurred:
            return "Audio session was interrupted by another process."
        case .configurationConflict:
            return "Audio session configuration conflict - another app may be using audio."
        }
    }
}

@MainActor
final class AudioSessionManager: ObservableObject {
    static let shared = AudioSessionManager()

    #if canImport(AVFoundation)
    private let audioSession = AVAudioSession.sharedInstance()
    #endif

    @Published private(set) var isAudioSessionActive = false
    @Published private(set) var lastError: AudioSessionError?

    private var interruptionObserver: NSObjectProtocol?
    private var routeChangeObserver: NSObjectProtocol?

    init() {
        #if canImport(AVFoundation)
        setupNotificationObservers()
        #endif
    }

    #if canImport(AVFoundation)
    private func setupNotificationObservers() {
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: audioSession,
            queue: .main
        ) { [weak self] notification in
            Task { @MainActor in
                self?.handleInterruption(notification)
            }
        }

        routeChangeObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: audioSession,
            queue: .main
        ) { [weak self] notification in
            Task { @MainActor in
                self?.handleRouteChange(notification)
            }
        }
    }

    private func handleInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }

        switch type {
        case .began:
            isAudioSessionActive = false
            lastError = .interruptionOccurred
        case .ended:
            if let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt {
                let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
                if options.contains(.shouldResume) {
                    try? reactivateAudioSession()
                }
            }
        @unknown default:
            break
        }
    }

    private func handleRouteChange(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }

        switch reason {
        case .oldDeviceUnavailable:
            isAudioSessionActive = false
        case .newDeviceAvailable:
            break
        default:
            break
        }
    }
    #endif

    func configureForPlayback() throws {
        #if canImport(AVFoundation)
        // 说明：`.playback` 类目合法选项仅包含 `.mixWithOthers` / `.duckOthers` /
        // `.interruptSpokenAudioAndMixWithOthers`。其余如 `.allowBluetooth`、
        // `.allowBluetoothA2DP`、`.allowAirPlay`、`.defaultToSpeaker` 均只允许
        // 用于 `.playAndRecord`/`.record`，在 `.playback` 下传入会触发
        // OSStatus -50 (kAudio_ParamError)，日志表现为
        // `SessionCore.mm:xxx Failed to set properties, error: 4294967246`。
        // `.playback` 默认即支持蓝牙 A2DP 与 AirPlay 外放，无需显式声明。
        let options: AVAudioSession.CategoryOptions = [.duckOthers]
        do {
            try audioSession.setCategory(
                .playback,
                mode: .spokenAudio,
                options: options
            )
        } catch {
            lastError = .categorySetupFailed(error)
            throw AudioSessionError.categorySetupFailed(error)
        }
        #endif
    }

    func activateAudioSession() throws {
        #if canImport(AVFoundation)
        do {
            try audioSession.setActive(true, options: [])
            isAudioSessionActive = true
            lastError = nil
        } catch {
            isAudioSessionActive = false
            lastError = .activationFailed(error)
            throw AudioSessionError.activationFailed(error)
        }
        #endif
    }

    func deactivateAudioSession() {
        #if canImport(AVFoundation)
        do {
            try audioSession.setActive(false, options: [.notifyOthersOnDeactivation])
            isAudioSessionActive = false
        } catch {
            lastError = .activationFailed(error)
        }
        #endif
    }

    private func reactivateAudioSession() throws {
        #if canImport(AVFoundation)
        do {
            try audioSession.setActive(true, options: [])
            isAudioSessionActive = true
            lastError = nil
        } catch {
            lastError = .activationFailed(error)
            throw AudioSessionError.activationFailed(error)
        }
        #endif
    }

    func prepareForPlayback() throws {
        try configureForPlayback()
        try activateAudioSession()
    }

    #if canImport(AVFoundation)
    func checkHeadphonesConnected() -> Bool {
        let outputs = audioSession.currentRoute.outputs
        for output in outputs {
            if output.portType == .headphones ||
               output.portType == .bluetoothA2DP ||
               output.portType == .bluetoothHFP ||
               output.portType == .bluetoothLE {
                return true
            }
        }
        return false
    }

    func getAvailableOutputDevices() -> [String] {
        return audioSession.currentRoute.outputs.map { $0.portName }
    }
    #endif
}

@MainActor
final class TTSService: NSObject, ObservableObject {
    private let logger = Logger(subsystem: "com.paipai.readalong", category: "SpeechPipeline")
    #if canImport(AVFoundation)
    private let synthesizer = AVSpeechSynthesizer()
    private var audioPlayer: AVAudioPlayer?
    private var speechContinuation: CheckedContinuation<Void, Error>?
    private var cloudAudioContinuation: CheckedContinuation<Void, Error>?
    private let audioSessionManager = AudioSessionManager.shared
    private var cachedVoicesByLanguage: [String: AVSpeechSynthesisVoice] = [:]
    private var prewarmedSpeechEngineLanguages: Set<String> = []
    private var didPrimeSpeechVoiceCatalog = false
    private var didPrepareAudioSessionForDeviceSpeech = false
    @Published var lastTTSError: TTSServiceError?
    #endif

    override init() {
        super.init()
        #if canImport(AVFoundation)
        synthesizer.delegate = self
        #endif
    }

    @discardableResult
    func speak(_ text: String, language: String, rate: Float = 1.0) -> Bool {
        #if canImport(AVFoundation)
        let startedAt = Date()
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        if audioPlayer?.isPlaying == true {
            audioPlayer?.stop()
        }
        if synthesizer.isSpeaking || synthesizer.isPaused {
            // 通过 DispatchQueue 派发跳出 Swift concurrency 上下文，
            // 避免 AVSpeechSynthesizer 内部 `unsafeForcedSync called from Swift Concurrent context` 告警。
            let currentSynthesizer = synthesizer
            DispatchQueue.main.async {
                currentSynthesizer.stopSpeaking(at: .immediate)
            }
        }
        guard prepareAudioSessionForImmediateSpeech(reason: "device_speak") else { return false }
        lastTTSError = nil
        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = voice(for: language)
        utterance.rate = deviceSpeechRate(forPlaybackSpeed: rate)
        let currentSynthesizer = synthesizer
        DispatchQueue.main.async {
            currentSynthesizer.speak(utterance)
        }
        let elapsedMs = Int(Date().timeIntervalSince(startedAt) * 1000)
        logger.info("speech_device_start language=\(language, privacy: .public) speed=\(rate, privacy: .public) avRate=\(utterance.rate, privacy: .public) cachedVoice=\(self.cachedVoicesByLanguage[self.normalizeSpeechLanguage(language)] != nil, privacy: .public) elapsedMs=\(elapsedMs, privacy: .public)")
        return true
        #else
        return false
        #endif
    }

    func speak(
        text: String,
        language: String,
        rate: Float = 1.0,
        mode: TTSPlaybackMode,
        backendClient: BackendClient? = nil,
        fallbackToDeviceOnFailure: Bool = true
    ) async throws -> TTSPlaybackResult {
        switch mode {
        case .device:
            try await speakDeviceAndWait(text: text, language: language, rate: rate)
            return TTSPlaybackResult(mode: .device, fellBackToDevice: false, cloudReceipt: nil)
        case .cloud:
            guard let backendClient else {
                throw TTSServiceError.backendUnavailable
            }
            let receipt = try await backendClient.synthesizeCloudSpeech(text: text, languageCode: language, rate: rate)
            if receipt.allowed == false || receipt.serviceStatus == "quota_blocked" {
                throw TTSServiceError.cloudQuotaBlocked(receipt.upgradeMessage ?? "Cloud TTS quota is exhausted.")
            }
            if receipt.serviceStatus == "provider_failed" || receipt.serviceStatus == "not_configured" {
                throw TTSServiceError.cloudSynthesisFailed(receipt.upgradeMessage ?? "Cloud TTS is currently unavailable.")
            }
            guard let audioBase64 = receipt.audioBase64 else {
                if fallbackToDeviceOnFailure {
                    try await speakDeviceAndWait(text: text, language: language, rate: rate)
                    return TTSPlaybackResult(mode: .cloud, fellBackToDevice: true, cloudReceipt: receipt)
                }
                throw TTSServiceError.missingCloudAudio
            }
            do {
                try await playCloudAudioAndWait(base64: audioBase64, mimeType: receipt.mimeType, playbackSpeed: rate)
                return TTSPlaybackResult(mode: .cloud, fellBackToDevice: false, cloudReceipt: receipt)
            } catch {
                if fallbackToDeviceOnFailure {
                    try await speakDeviceAndWait(text: text, language: language, rate: rate)
                    return TTSPlaybackResult(mode: .cloud, fellBackToDevice: true, cloudReceipt: receipt)
                }
                throw error
            }
        }
    }

    func playCloudAudio(base64: String, mimeType: String?, playbackSpeed: Float = 1.0) throws {
        #if canImport(AVFoundation)
        guard let data = Data(base64Encoded: base64) else {
            throw TTSServiceError.invalidCloudAudio
        }
        let currentSynthesizer = synthesizer
        let currentPlayer = audioPlayer
        DispatchQueue.main.async {
            currentSynthesizer.stopSpeaking(at: .immediate)
            currentPlayer?.stop()
        }
        #if os(iOS)
        guard prepareAudioSessionForImmediateSpeech(reason: "device_speak_wait") else {
            throw lastTTSError ?? TTSServiceError.audioSessionConfigurationFailed("Unknown error")
        }
        #endif
        let newPlayer = try AVAudioPlayer(data: data, fileTypeHint: fileTypeHint(for: mimeType))
        newPlayer.enableRate = true
        newPlayer.rate = audioPlayerRate(forPlaybackSpeed: playbackSpeed)
        newPlayer.prepareToPlay()
        audioPlayer = newPlayer
        DispatchQueue.main.async {
            newPlayer.play()
        }
        #else
        throw TTSServiceError.invalidCloudAudio
        #endif
    }

    private func speakDeviceAndWait(text: String, language: String, rate: Float) async throws {
        #if canImport(AVFoundation)
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        // 先 resume 旧 continuation 再置 nil，避免后续 delegate 回调重复唤醒。
        if let previous = speechContinuation {
            speechContinuation = nil
            previous.resume(throwing: CancellationError())
        }
        if audioPlayer?.isPlaying == true {
            audioPlayer?.stop()
        }
        let needsDrain = synthesizer.isSpeaking || synthesizer.isPaused
        if needsDrain {
            // 关键修复：不要在 Swift concurrency (`@MainActor async`) 上下文里直接调 stopSpeaking/speak，
            // 否则 AVFoundation 会在音频线程 `unsafeForcedSync` 回主线程，
            // 触发 `unsafeForcedSync called from Swift Concurrent context` 与空 buffer `mDataByteSize (0)` 日志。
            // 用 DispatchQueue.main.async 重新进入普通 run loop 上下文再调用。
            let currentSynthesizer = synthesizer
            DispatchQueue.main.async {
                currentSynthesizer.stopSpeaking(at: .immediate)
            }
            // 给 AVSpeechSynthesizer 内部回收 buffer 的时间，再推新的 utterance。
            await Task.yield()
            try? await Task.sleep(nanoseconds: 80_000_000) // 80ms
        }

        do {
            try audioSessionManager.prepareForPlayback()
        } catch {
            let audioError = error as? AudioSessionError ?? AudioSessionError.configurationConflict
            throw TTSServiceError.audioSessionConfigurationFailed(audioError.localizedDescription)
        }

        try await withCheckedThrowingContinuation { continuation in
            speechContinuation = continuation
            let utterance = AVSpeechUtterance(string: trimmed)
            utterance.voice = voice(for: language)
            utterance.rate = deviceSpeechRate(forPlaybackSpeed: rate)
            // 同理：use DispatchQueue.main.async 派发合成调用，让它落在非 Swift concurrency 的主线程执行上下文。
            let currentSynthesizer = self.synthesizer
            DispatchQueue.main.async {
                currentSynthesizer.speak(utterance)
            }
        }
        #endif
    }

    private func playCloudAudioAndWait(base64: String, mimeType: String?, playbackSpeed: Float) async throws {
        #if canImport(AVFoundation)
        guard let data = Data(base64Encoded: base64) else {
            throw TTSServiceError.invalidCloudAudio
        }
        // 与设备 TTS 一致：先 resume 旧 continuation，再停止合成器 / 音频播放器，给 AVFoundation 一个 runloop 排空 buffer。
        if let previous = cloudAudioContinuation {
            cloudAudioContinuation = nil
            previous.resume(throwing: CancellationError())
        }
        let needsDrain = synthesizer.isSpeaking || synthesizer.isPaused || audioPlayer?.isPlaying == true
        if synthesizer.isSpeaking || synthesizer.isPaused {
            // 同样用 DispatchQueue 派发避免 unsafeForcedSync 告警。
            let currentSynthesizer = synthesizer
            DispatchQueue.main.async {
                currentSynthesizer.stopSpeaking(at: .immediate)
            }
        }
        if audioPlayer?.isPlaying == true {
            let currentPlayer = audioPlayer
            DispatchQueue.main.async {
                currentPlayer?.stop()
            }
        }
        if needsDrain {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 80_000_000) // 80ms
        }
        #if os(iOS)
        do {
            try audioSessionManager.prepareForPlayback()
        } catch {
            let audioError = error as? AudioSessionError ?? AudioSessionError.configurationConflict
            throw TTSServiceError.audioSessionConfigurationFailed(audioError.localizedDescription)
        }
        #endif
        try await withCheckedThrowingContinuation { continuation in
            do {
                cloudAudioContinuation = continuation
                let newPlayer = try AVAudioPlayer(data: data, fileTypeHint: fileTypeHint(for: mimeType))
                newPlayer.delegate = self
                newPlayer.enableRate = true
                newPlayer.rate = audioPlayerRate(forPlaybackSpeed: playbackSpeed)
                newPlayer.prepareToPlay()
                audioPlayer = newPlayer
                // 同理派发到普通主线程 run loop 上执行 play，避免 AVAudioPlayer 触发 unsafeForcedSync。
                DispatchQueue.main.async {
                    if newPlayer.play() != true {
                        // 联动清理 continuation，避免挂死。
                        Task { @MainActor in
                            if let pending = self.cloudAudioContinuation {
                                self.cloudAudioContinuation = nil
                                pending.resume(throwing: TTSServiceError.invalidCloudAudio)
                            }
                        }
                    }
                }
            } catch {
                cloudAudioContinuation = nil
                continuation.resume(throwing: error)
            }
        }
        #else
        throw TTSServiceError.invalidCloudAudio
        #endif
    }

    #if canImport(AVFoundation)
    /// UI 传入的是 0.5x/0.75x/1.0x 等“播放倍率”，AVSpeechUtterance 需要的是系统语速值。
    /// 以系统默认语速为 1.0x 基准换算，避免直接传 0.25/0.35 时被不同系统声音近似或夹断。
    private func deviceSpeechRate(forPlaybackSpeed speed: Float) -> Float {
        let safeSpeed = max(0.5, min(speed, 1.5))
        let scaledRate = AVSpeechUtteranceDefaultSpeechRate * safeSpeed
        return max(AVSpeechUtteranceMinimumSpeechRate, min(scaledRate, AVSpeechUtteranceMaximumSpeechRate))
    }

    /// 云端返回的是完整音频文件；即使后端没有按 rate 合成，也在客户端按用户选择变速播放。
    private func audioPlayerRate(forPlaybackSpeed speed: Float) -> Float {
        max(0.5, min(speed, 1.5))
    }
    #endif

    func preloadDeviceVoices(languageCodes: [String]) {
        #if canImport(AVFoundation)
        let startedAt = Date()
        let normalizedLanguages = uniqueNormalizedLanguages(languageCodes)
        guard !normalizedLanguages.isEmpty else { return }

        if !didPrimeSpeechVoiceCatalog {
            _ = AVSpeechSynthesisVoice.speechVoices()
            didPrimeSpeechVoiceCatalog = true
        }

        for language in normalizedLanguages where cachedVoicesByLanguage[language] == nil {
            if let voice = AVSpeechSynthesisVoice(language: language) {
                cachedVoicesByLanguage[language] = voice
            }
        }

        prepareAudioSessionForDeviceSpeech(reason: "preload")

        let elapsedMs = Int(Date().timeIntervalSince(startedAt) * 1000)
        logger.info("speech_preload_completed languages=\(normalizedLanguages.joined(separator: ","), privacy: .public) cached=\(self.cachedVoicesByLanguage.count, privacy: .public) audioSessionPrepared=\(self.didPrepareAudioSessionForDeviceSpeech, privacy: .public) elapsedMs=\(elapsedMs, privacy: .public)")
        #endif
    }

    func preloadDeviceSpeechEngine(languageCodes: [String]) async {
        #if canImport(AVFoundation)
        let startedAt = Date()
        let normalizedLanguages = uniqueNormalizedLanguages(languageCodes)
        guard let language = normalizedLanguages.first else { return }

        preloadDeviceVoices(languageCodes: normalizedLanguages)
        guard !prewarmedSpeechEngineLanguages.contains(language) else { return }
        guard prepareAudioSessionForImmediateSpeech(reason: "engine_prewarm") else { return }

        if synthesizer.isSpeaking || synthesizer.isPaused {
            return
        }

        do {
            try await speakSilentWarmupUtterance(language: language)
            prewarmedSpeechEngineLanguages.insert(language)
            let elapsedMs = Int(Date().timeIntervalSince(startedAt) * 1000)
            logger.info("speech_engine_prewarm_completed language=\(language, privacy: .public) elapsedMs=\(elapsedMs, privacy: .public)")
        } catch {
            let elapsedMs = Int(Date().timeIntervalSince(startedAt) * 1000)
            logger.error("speech_engine_prewarm_failed language=\(language, privacy: .public) elapsedMs=\(elapsedMs, privacy: .public) error=\(String(describing: error), privacy: .public)")
        }
        #endif
    }

    func ensureDeviceVoiceCached(for language: String) {
        #if canImport(AVFoundation)
        let normalized = normalizeSpeechLanguage(language)
        guard !normalized.isEmpty, cachedVoicesByLanguage[normalized] == nil else { return }
        let startedAt = Date()
        if !didPrimeSpeechVoiceCatalog {
            _ = AVSpeechSynthesisVoice.speechVoices()
            didPrimeSpeechVoiceCatalog = true
        }
        cachedVoicesByLanguage[normalized] = AVSpeechSynthesisVoice(language: normalized)
        prepareAudioSessionForDeviceSpeech(reason: "cache_fill")
        let elapsedMs = Int(Date().timeIntervalSince(startedAt) * 1000)
        logger.info("speech_voice_cache_fill language=\(normalized, privacy: .public) cached=\(self.cachedVoicesByLanguage[normalized] != nil, privacy: .public) elapsedMs=\(elapsedMs, privacy: .public)")
        #endif
    }

    func hasCachedDeviceVoice(for language: String) -> Bool {
        #if canImport(AVFoundation)
        return cachedVoicesByLanguage[normalizeSpeechLanguage(language)] != nil
        #else
        return false
        #endif
    }

    func stop() {
        #if canImport(AVFoundation)
        if let pendingCloud = cloudAudioContinuation {
            cloudAudioContinuation = nil
            pendingCloud.resume(throwing: CancellationError())
        }
        if let pendingSpeech = speechContinuation {
            speechContinuation = nil
            pendingSpeech.resume(throwing: CancellationError())
        }
        let currentSynthesizer = synthesizer
        let currentPlayer = audioPlayer
        let shouldStopPlayer = currentPlayer?.isPlaying == true
        let shouldStopSynthesizer = synthesizer.isSpeaking || synthesizer.isPaused
        if shouldStopPlayer || shouldStopSynthesizer {
            // 同样跳出 Swift concurrency 上下文再调用停止 API。
            DispatchQueue.main.async {
                if shouldStopPlayer {
                    currentPlayer?.stop()
                }
                if shouldStopSynthesizer {
                    currentSynthesizer.stopSpeaking(at: .immediate)
                }
            }
        }
        #endif
    }

    #if canImport(AVFoundation)
    private func voice(for language: String) -> AVSpeechSynthesisVoice? {
        let normalized = normalizeSpeechLanguage(language)
        if let cached = cachedVoicesByLanguage[normalized] {
            return cached
        }
        let voice = AVSpeechSynthesisVoice(language: normalized)
        if let voice {
            cachedVoicesByLanguage[normalized] = voice
        }
        return voice
    }

    private func prepareAudioSessionForDeviceSpeech(reason: String) {
        guard !didPrepareAudioSessionForDeviceSpeech else { return }
        _ = configureAudioSessionForDeviceSpeech(reason: reason)
    }

    private func prepareAudioSessionForImmediateSpeech(reason: String) -> Bool {
        guard !didPrepareAudioSessionForDeviceSpeech || !audioSessionManager.isAudioSessionActive else { return true }
        return configureAudioSessionForDeviceSpeech(reason: reason)
    }

    private func configureAudioSessionForDeviceSpeech(reason: String) -> Bool {
        do {
            try audioSessionManager.prepareForPlayback()
            didPrepareAudioSessionForDeviceSpeech = true
            return true
        } catch {
            let audioError = error as? AudioSessionError ?? AudioSessionError.configurationConflict
            lastTTSError = TTSServiceError.audioSessionConfigurationFailed(audioError.localizedDescription)
            logger.error("speech_preload_audio_session_failed reason=\(reason, privacy: .public) error=\(audioError.localizedDescription, privacy: .public)")
            return false
        }
    }

    private func speakSilentWarmupUtterance(language: String) async throws {
        if let previous = speechContinuation {
            speechContinuation = nil
            previous.resume(throwing: CancellationError())
        }

        let utterance = AVSpeechUtterance(string: ".")
        utterance.voice = voice(for: language)
        utterance.rate = 0.55
        utterance.volume = 0
        let currentSynthesizer = synthesizer

        try await withCheckedThrowingContinuation { continuation in
            speechContinuation = continuation
            DispatchQueue.main.async {
                currentSynthesizer.speak(utterance)
            }
        }
    }

    private func uniqueNormalizedLanguages(_ languages: [String]) -> [String] {
        var result: [String] = []
        for language in languages {
            let normalized = normalizeSpeechLanguage(language)
            guard !normalized.isEmpty, !result.contains(normalized) else { continue }
            result.append(normalized)
        }
        return result
    }

    private func normalizeSpeechLanguage(_ language: String) -> String {
        let trimmed = language
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
        return trimmed.isEmpty ? language : trimmed
    }

    private func fileTypeHint(for mimeType: String?) -> String? {
        switch mimeType?.lowercased() {
        case "audio/wav":
            return "com.microsoft.waveform-audio"
        case "audio/mpeg", "audio/mp3":
            return "public.mp3"
        case "audio/aac":
            return "public.aac-audio"
        default:
            return nil
        }
    }
    #endif
}

#if canImport(AVFoundation)
extension TTSService: AVSpeechSynthesizerDelegate, AVAudioPlayerDelegate {
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            speechContinuation?.resume()
            speechContinuation = nil
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            speechContinuation?.resume(throwing: CancellationError())
            speechContinuation = nil
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            if flag {
                cloudAudioContinuation?.resume()
            } else {
                cloudAudioContinuation?.resume(throwing: TTSServiceError.invalidCloudAudio)
            }
            cloudAudioContinuation = nil
        }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor in
            cloudAudioContinuation?.resume(throwing: error ?? TTSServiceError.invalidCloudAudio)
            cloudAudioContinuation = nil
        }
    }
}
#endif
