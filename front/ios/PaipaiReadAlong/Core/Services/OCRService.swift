import Foundation
@preconcurrency import Vision
import ImageIO

// MARK: - OCR Result
struct OCRResult {
    let text: String
    let observations: [TextObservation]
    let isSuccess: Bool
    let error: Error?
    
    struct TextObservation {
        let text: String
        let boundingBox: CGRect
        let confidence: Float
    }
}

// MARK: - OCR Service
final class OCRService {
    
    enum OCRAvailability {
        case available
        case unsupportedSystem
    }
    
    enum OCRError: LocalizedError {
        case unsupportedSystem
        case unreadableImage
        case noTextFound
        case recognitionFailed(String)
        
        var errorDescription: String? {
            switch self {
            case .unsupportedSystem:
                return "当前系统版本暂不支持设备自带文字识别。"
            case .unreadableImage:
                return "当前图片无法进行本地识别，请重新拍照后再试。"
            case .noTextFound:
                return "设备已经完成识别，但没有提取到可用文字。"
            case .recognitionFailed(let message):
                return "识别失败: \(message)"
            }
        }
    }
    
    var availability: OCRAvailability {
        if #available(iOS 15.0, macOS 10.15, *) {
            return .available
        }
        return .unsupportedSystem
    }
    
    // MARK: - Text Recognition

    enum RecognitionLevel {
        case fast
        case accurate
    }
    
    func recognizeText(
        from imageData: Data,
        recognitionLanguages: [String] = ["zh-Hans", "en-US"],
        recognitionLevel: RecognitionLevel = .fast
    ) async throws -> OCRResult {
        guard case .available = availability else {
            throw OCRError.unsupportedSystem
        }
        
        guard let source = CGImageSourceCreateWithData(imageData as CFData, nil),
              let cgImage = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw OCRError.unreadableImage
        }
        
        return try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error = error {
                    continuation.resume(throwing: OCRError.recognitionFailed(error.localizedDescription))
                    return
                }
                
                guard let observations = request.results as? [VNRecognizedTextObservation] else {
                    continuation.resume(throwing: OCRError.noTextFound)
                    return
                }
                
                let textObservations = observations.compactMap { observation -> OCRResult.TextObservation? in
                    guard let candidate = observation.topCandidates(1).first else { return nil }
                    return OCRResult.TextObservation(
                        text: candidate.string,
                        boundingBox: observation.boundingBox,
                        confidence: candidate.confidence
                    )
                }
                
                let mergedText = textObservations
                    .map { $0.text }
                    .joined(separator: "\n")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                
                guard !mergedText.isEmpty else {
                    continuation.resume(throwing: OCRError.noTextFound)
                    return
                }
                
                let result = OCRResult(
                    text: Self.normalizeRecognizedText(mergedText),
                    observations: textObservations,
                    isSuccess: true,
                    error: nil
                )
                continuation.resume(returning: result)
            }
            
            switch recognitionLevel {
            case .fast:
                request.recognitionLevel = .fast
                request.usesLanguageCorrection = false
            case .accurate:
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = false
            }
            request.recognitionLanguages = Self.normalizedRecognitionLanguages(recognitionLanguages)
            
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(throwing: OCRError.recognitionFailed(error.localizedDescription))
            }
        }
    }
    
    // MARK: - Text Validation
    
    func shouldBlock(text: String) -> Bool {
        text.count > 200 || text.components(separatedBy: .newlines).count > 3
    }
    
    func isValidText(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && trimmed.count <= 200
    }
    
    // MARK: - Text Normalization
    
    private static func normalizeRecognizedText(_ text: String) -> String {
        let compact = text
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "\n")
        
        if compact.count <= 200 {
            return compact
        }
        return String(compact.prefix(200))
    }

    private static func normalizedRecognitionLanguages(_ languageCodes: [String]) -> [String] {
        var languages: [String] = []
        for code in languageCodes + ["zh-Hans", "en-US"] {
            let normalized = normalizedRecognitionLanguageCode(code)
            guard !normalized.isEmpty, !languages.contains(normalized) else { continue }
            languages.append(normalized)
        }
        return languages
    }

    private static func normalizedRecognitionLanguageCode(_ languageCode: String) -> String {
        let lowered = languageCode
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        if lowered == "zh" || lowered.hasPrefix("zh-hans") || lowered.hasPrefix("zh-cn") {
            return "zh-Hans"
        }
        if lowered.hasPrefix("zh-hant") || lowered.hasPrefix("zh-tw") || lowered.hasPrefix("zh-hk") {
            return "zh-Hant"
        }
        if lowered.hasPrefix("en") { return "en-US" }
        if lowered.hasPrefix("ja") { return "ja-JP" }
        if lowered.hasPrefix("ko") { return "ko-KR" }
        if lowered.hasPrefix("es") { return "es-ES" }
        if lowered.hasPrefix("fr") { return "fr-FR" }
        if lowered.hasPrefix("de") { return "de-DE" }
        if lowered.hasPrefix("it") { return "it-IT" }
        if lowered.hasPrefix("pt") { return "pt-BR" }
        if let primary = lowered.split(separator: "-").first {
            return String(primary)
        }
        return lowered
    }
}

// MARK: - Text Segmentation Service
import NaturalLanguage

final class TextSegmentationService {
    
    enum SegmentationError: LocalizedError {
        case emptyText
        case segmentationFailed
        
        var errorDescription: String? {
            switch self {
            case .emptyText:
                return "文本为空，无法进行分句。"
            case .segmentationFailed:
                return "分句处理失败。"
            }
        }
    }
    
    // MARK: - Sentence Segmentation
    
    func segmentIntoSentences(_ text: String, language: NLLanguage = .english) -> [String] {
        guard !text.isEmpty else { return [] }
        
        let tokenizer = NLTokenizer(unit: .sentence)
        tokenizer.string = text
        
        var sentences: [String] = []
        tokenizer.enumerateTokens(in: text.startIndex..<text.endIndex) { range, _ in
            let sentence = String(text[range]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !sentence.isEmpty {
                sentences.append(sentence)
            }
            return true
        }
        
        return sentences
    }
    
    // MARK: - Word Segmentation
    
    func segmentIntoWords(_ text: String, language: NLLanguage = .english) -> [String] {
        guard !text.isEmpty else { return [] }
        
        let tokenizer = NLTokenizer(unit: .word)
        tokenizer.string = text
        
        var words: [String] = []
        tokenizer.enumerateTokens(in: text.startIndex..<text.endIndex) { range, _ in
            let word = String(text[range])
            if !word.isEmpty {
                words.append(word)
            }
            return true
        }
        
        return words
    }
    
    // MARK: - Language Detection
    
    func detectLanguage(_ text: String) -> NLLanguage? {
        let recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        return recognizer.dominantLanguage
    }
    
    // MARK: - Smart Segmentation for Reading
    
    func segmentForReading(_ text: String) -> [ReadingSegment] {
        let sentences = segmentIntoSentences(text)
        
        return sentences.map { sentence in
            let words = segmentIntoWords(sentence)
            return ReadingSegment(
                fullText: sentence,
                words: words,
                wordCount: words.count
            )
        }
    }
}

// MARK: - Sentence Segment
struct ReadingSegment {
    let fullText: String
    let words: [String]
    let wordCount: Int
    
    var isShortSentence: Bool {
        wordCount <= 10
    }
    
    var isMediumSentence: Bool {
        wordCount > 10 && wordCount <= 20
    }
    
    var isLongSentence: Bool {
        wordCount > 20
    }
}
