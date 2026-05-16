import Foundation

struct DiagnosticCase {
    let name: String
    let text: String
    let sourceOverride: String?
    let targetOverride: String
    let expectedSourceMode: TranslationRequestPlan.SourceMode
    let expectedSource: String?
    let expectedTarget: String
}

@main
struct TranslationPipelineDiagnosticsRunner {
    static func main() {
        let cases: [DiagnosticCase] = [
            DiagnosticCase(
                name: "formal_document_en_to_zh",
                text: "Please review the attached agreement before signing.",
                sourceOverride: "en-US",
                targetOverride: "zh-Hans",
                expectedSourceMode: .explicit,
                expectedSource: "en",
                expectedTarget: "zh-Hans"
            ),
            DiagnosticCase(
                name: "colloquial_zh_to_en",
                text: "这个功能现在不太对，麻烦再看一下。",
                sourceOverride: "zh-Hans",
                targetOverride: "en",
                expectedSourceMode: .explicit,
                expectedSource: "zh-Hans",
                expectedTarget: "en"
            ),
            DiagnosticCase(
                name: "terminology_en_to_es",
                text: "The OCR pipeline must preserve the user's target language parameter.",
                sourceOverride: "en",
                targetOverride: "es-ES",
                expectedSourceMode: .explicit,
                expectedSource: "en",
                expectedTarget: "es"
            ),
            DiagnosticCase(
                name: "mixed_language_auto_to_ja",
                text: "今天 read aloud 的 latency 有点高，需要优化。",
                sourceOverride: nil,
                targetOverride: "ja",
                expectedSourceMode: .automatic,
                expectedSource: nil,
                expectedTarget: "ja"
            ),
            DiagnosticCase(
                name: "same_detected_and_target_still_translates_auto_to_en",
                text: "Hello，今天我们学习 photosynthesis。",
                sourceOverride: nil,
                targetOverride: "en",
                expectedSourceMode: .automatic,
                expectedSource: nil,
                expectedTarget: "en"
            ),
            DiagnosticCase(
                name: "same_explicit_source_and_target_falls_back_to_auto",
                text: "Hello，今天我们学习 photosynthesis。",
                sourceOverride: "en-US",
                targetOverride: "en",
                expectedSourceMode: .automatic,
                expectedSource: nil,
                expectedTarget: "en"
            ),
            DiagnosticCase(
                name: "supported_target_ja",
                text: "Please translate this sentence.",
                sourceOverride: nil,
                targetOverride: "ja",
                expectedSourceMode: .automatic,
                expectedSource: nil,
                expectedTarget: "ja"
            ),
            DiagnosticCase(
                name: "supported_target_ko",
                text: "Please translate this sentence.",
                sourceOverride: nil,
                targetOverride: "ko",
                expectedSourceMode: .automatic,
                expectedSource: nil,
                expectedTarget: "ko"
            ),
            DiagnosticCase(
                name: "supported_target_es",
                text: "Please translate this sentence.",
                sourceOverride: nil,
                targetOverride: "es",
                expectedSourceMode: .automatic,
                expectedSource: nil,
                expectedTarget: "es"
            )
        ]

        var failureCount = 0
        for testCase in cases {
            let plan = TranslationPipelinePlanner.makePlan(
                text: testCase.text,
                sourceLanguageOverride: testCase.sourceOverride,
                targetLanguageOverride: testCase.targetOverride,
                fallbackSourceLanguageCode: "en",
                fallbackTargetLanguageCode: "zh-Hans"
            )

            failureCount += assertEqual(plan.sourceMode, testCase.expectedSourceMode, "\(testCase.name).sourceMode")
            failureCount += assertEqual(plan.sourceLanguageCode, testCase.expectedSource, "\(testCase.name).source")
            failureCount += assertEqual(plan.targetLanguageCode, testCase.expectedTarget, "\(testCase.name).target")
            failureCount += assertEqual(plan.sourceTextLength, TranslationTextProcessor.preprocess(testCase.text).count, "\(testCase.name).length")

            print("[TranslationDiagnostic][OK] \(testCase.name) sourceMode=\(plan.sourceMode.rawValue) source=\(plan.sourceLanguageCode ?? "auto") target=\(plan.targetLanguageCode) inputLength=\(plan.sourceTextLength) inputFingerprint=\(plan.sourceTextFingerprint)")
        }

        let preprocessed = TranslationTextProcessor.preprocess(" \r\nHello\u{00A0}world\r\n ")
        failureCount += assertEqual(preprocessed, "Hello world", "preprocess.normalizesWhitespace")
        let postprocessed = TranslationTextProcessor.postprocess("\n translated text \r\n")
        failureCount += assertEqual(postprocessed, "translated text", "postprocess.trimsOutput")

        if failureCount > 0 {
            fputs("[TranslationDiagnostic][FAIL] \(failureCount) assertion(s) failed\n", stderr)
            Foundation.exit(1)
        }

        print("[TranslationDiagnostic][OK] all translation pipeline diagnostics passed")
    }

    private static func assertEqual<T: Equatable>(_ actual: T, _ expected: T, _ label: String) -> Int {
        guard actual != expected else { return 0 }
        fputs("[TranslationDiagnostic][FAIL] \(label): expected \(expected), got \(actual)\n", stderr)
        return 1
    }
}
