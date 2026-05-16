import CoreGraphics
import UIKit
import XCTest
@testable import PaipaiReadAlong

final class RecognitionCropMapperTests: XCTestCase {
    func testMetadataRectMapsDirectlyToCapturedImagePixels() {
        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: CGRect(x: 45, y: 320, width: 300, height: 160),
            previewSize: CGSize(width: 390, height: 844),
            normalizedMetadataRect: CGRect(x: 0.2, y: 0.3, width: 0.5, height: 0.1)
        )

        let cropRect = RecognitionCropMapper.cropRect(
            imageSize: CGSize(width: 4000, height: 3000),
            cropGeometry: geometry
        )

        XCTAssertEqual(cropRect, CGRect(x: 800, y: 900, width: 2000, height: 300))
    }

    func testMetadataRectIsClampedAtImageEdgesWithoutAspectCorrection() {
        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: CGRect(x: 40, y: 300, width: 320, height: 170),
            previewSize: CGSize(width: 390, height: 844),
            normalizedMetadataRect: CGRect(x: 0.82, y: -0.04, width: 0.24, height: 0.22)
        )

        let cropRect = RecognitionCropMapper.cropRect(
            imageSize: CGSize(width: 4032, height: 3024),
            cropGeometry: geometry
        )

        XCTAssertEqual(cropRect, CGRect(x: 3307, y: 0, width: 725, height: 544))
    }

    func testRightOrientedPhotoRotatesMetadataRectIntoUprightCropCoordinates() {
        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: CGRect(x: 45, y: 320, width: 300, height: 160),
            previewSize: CGSize(width: 390, height: 844),
            normalizedMetadataRect: CGRect(x: 0.4, y: 0.3, width: 0.12, height: 0.28)
        )

        let cropRect = RecognitionCropMapper.cropRect(
            imageSize: CGSize(width: 3024, height: 4032),
            cropGeometry: geometry,
            imageOrientation: .right
        )

        XCTAssertEqual(cropRect, CGRect(x: 1271, y: 1613, width: 845, height: 483))
        XCTAssertGreaterThan(cropRect?.width ?? 0, cropRect?.height ?? 0)
    }

    func testFallbackMatchesAspectFillMappingOnTallPhonePreview() {
        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: CGRect(x: 45, y: 320, width: 300, height: 160),
            previewSize: CGSize(width: 390, height: 844),
            normalizedMetadataRect: nil
        )

        let cropRect = RecognitionCropMapper.cropRect(
            imageSize: CGSize(width: 4000, height: 3000),
            cropGeometry: geometry
        )

        XCTAssertEqual(cropRect, CGRect(x: 1467, y: 1138, width: 1066, height: 568))
    }

    func testFallbackMatchesAspectFillMappingOnIPadPreview() {
        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: CGRect(x: 124, y: 420, width: 520, height: 240),
            previewSize: CGSize(width: 768, height: 1024),
            normalizedMetadataRect: nil
        )

        let cropRect = RecognitionCropMapper.cropRect(
            imageSize: CGSize(width: 4032, height: 3024),
            cropGeometry: geometry
        )

        XCTAssertEqual(cropRect, CGRect(x: 1249, y: 1241, width: 1534, height: 708))
    }

    func testInvalidPreviewGeometryReturnsNil() {
        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: CGRect(x: 0, y: 0, width: 1, height: 1),
            previewSize: .zero,
            normalizedMetadataRect: nil
        )

        let cropRect = RecognitionCropMapper.cropRect(
            imageSize: CGSize(width: 4000, height: 3000),
            cropGeometry: geometry
        )

        XCTAssertNil(cropRect)
    }
}

final class ParentGateServiceTests: XCTestCase {
    private func makeService() -> ParentGateService {
        ParentGateService(
            appCode: "parent-gate-tests-\(UUID().uuidString)",
            bundleIdentifier: "com.paipai.readalong.tests",
            pbkdf2Iterations: 1_000,
            lockDuration: 60,
            recoveryLockDuration: 60
        )
    }

    private var validAnswers: [String: String] {
        Dictionary(uniqueKeysWithValues: ParentGateService.recoveryQuestions.enumerated().map { index, question in
            (question.id, "answer-\(index)")
        })
    }

    func testPasswordComplexityAndWeakPasswordRules() throws {
        let service = makeService()
        defer { service.clearOfflinePassword() }

        XCTAssertThrowsError(try service.validatePassword("a1b2")) { error in
            XCTAssertEqual(error as? ParentGateServiceError, .passwordTooShort)
        }
        XCTAssertThrowsError(try service.validatePassword("abcdef")) { error in
            XCTAssertEqual(error as? ParentGateServiceError, .passwordNeedsLettersAndNumbers)
        }
        XCTAssertThrowsError(try service.validatePassword("password1")) { error in
            XCTAssertEqual(error as? ParentGateServiceError, .weakPassword)
        }
        XCTAssertNoThrow(try service.validatePassword("Read2026"))
    }

    func testOfflinePasswordIsCreatedVerifiedAndPreferredBeforeDeviceAuth() throws {
        let service = makeService()
        defer { service.clearOfflinePassword() }

        try service.createOfflinePassword(ParentPasswordSetupPayload(password: "Read2026", answersByQuestionId: validAnswers))

        XCTAssertEqual(service.preferredAccessMethod(), .offlinePassword)
        XCTAssertThrowsError(try service.verifyOfflinePassword("Wrong2026")) { error in
            XCTAssertEqual(error as? ParentGateServiceError, .invalidPassword(remainingAttempts: 4))
        }
        XCTAssertNoThrow(try service.verifyOfflinePassword("Read2026"))
    }

    func testOfflinePasswordLocksAfterRepeatedFailures() throws {
        let service = makeService()
        defer { service.clearOfflinePassword() }
        try service.createOfflinePassword(ParentPasswordSetupPayload(password: "Read2026", answersByQuestionId: validAnswers))

        for remaining in stride(from: 4, through: 1, by: -1) {
            XCTAssertThrowsError(try service.verifyOfflinePassword("Wrong2026")) { error in
                XCTAssertEqual(error as? ParentGateServiceError, .invalidPassword(remainingAttempts: remaining))
            }
        }

        XCTAssertThrowsError(try service.verifyOfflinePassword("Wrong2026")) { error in
            guard case .locked = error as? ParentGateServiceError else {
                XCTFail("Expected locked error, got \(error)")
                return
            }
        }
        XCTAssertThrowsError(try service.verifyOfflinePassword("Read2026")) { error in
            guard case .locked = error as? ParentGateServiceError else {
                XCTFail("Expected lock to block correct password during lock window, got \(error)")
                return
            }
        }
    }

    func testAnyOneRecoveryAnswerCanAuthorizeReset() throws {
        let service = makeService()
        defer { service.clearOfflinePassword() }
        try service.createOfflinePassword(ParentPasswordSetupPayload(password: "Read2026", answersByQuestionId: validAnswers))

        let firstQuestionId = try XCTUnwrap(ParentGateService.recoveryQuestions.first?.id)
        XCTAssertNoThrow(try service.verifyRecoveryAnswer([firstQuestionId: " ANSWER-0 "]))
        XCTAssertThrowsError(
            try service.resetOfflinePassword(
                ParentPasswordSetupPayload(password: "password1", answersByQuestionId: validAnswers)
            )
        ) { error in
            XCTAssertEqual(error as? ParentGateServiceError, .weakPassword)
        }

        try service.resetOfflinePassword(ParentPasswordSetupPayload(password: "New2026", answersByQuestionId: validAnswers))
        XCTAssertThrowsError(try service.verifyOfflinePassword("Read2026"))
        XCTAssertNoThrow(try service.verifyOfflinePassword("New2026"))
    }
}
