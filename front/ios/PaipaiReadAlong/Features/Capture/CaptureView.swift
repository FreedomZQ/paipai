import SwiftUI
import UniformTypeIdentifiers
import NaturalLanguage
import os
#if os(iOS)
import AVFoundation
import Photos
import UIKit
public typealias PlatformImage = UIImage
#elseif os(macOS)
import AppKit
public typealias PlatformImage = NSImage
#endif

struct CaptureImagePayload: Identifiable {
    let id = UUID()
    let image: PlatformImage
    #if os(iOS)
    let cropGeometry: RecognitionCropGeometry?

    init(image: PlatformImage, cropGeometry: RecognitionCropGeometry? = nil) {
        self.image = image
        self.cropGeometry = cropGeometry
    }
    #else
    init(image: PlatformImage) {
        self.image = image
    }
    #endif
}

struct CaptureView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var appState: AppState

    private let shouldRequestInitialPermissions: Bool

    @State private var capturedImage: PlatformImage?
    @State private var capturePayload: CaptureImagePayload?
    @State private var showImagePicker = false
    @State private var showOCRConfirm = false
    @State private var showInitialCaptureConsent = false
    @State private var showPermissionSettingsAlert = false
    @State private var permissionSettingsMessage = ""
    @State private var usageSessionId = UUID().uuidString
    @State private var usageSessionActive = false
    @State private var usageChildId = ""
    #if os(iOS)
    @StateObject private var cameraController = CameraController()
    @State private var flashMode: UIImagePickerController.CameraFlashMode = .auto
    @State private var cameraPreviewFrame: CGRect = .zero
    @State private var recognitionFrame: CGRect = .zero
    #endif

    init(shouldRequestInitialPermissions: Bool = false) {
        self.shouldRequestInitialPermissions = shouldRequestInitialPermissions
    }

    var body: some View {
        NavigationStack {
            GeometryReader { proxy in
                ZStack {
                    #if os(iOS)
                    if cameraController.shouldShowLivePreview {
                        CameraPreviewView(
                            controller: cameraController,
                            recognitionFrame: recognitionFrame
                        )
                    } else {
                        cameraBackdrop
                    }
                    #else
                    cameraBackdrop
                    #endif
                    captureOverlay(proxy: proxy)
                }
                .frame(width: proxy.size.width, height: proxy.size.height)
                .background(Color.black)
                #if os(iOS)
                .background(
                    GlobalFrameReporter { frame in
                        updateCameraPreviewFrame(frame)
                    }
                )
                #endif
                .ignoresSafeArea()
            }
            .sheet(isPresented: $showImagePicker) {
                #if os(iOS)
                PhotoLibraryPicker(selectedImage: $capturedImage)
                #else
                ImagePicker(selectedImage: $capturedImage)
                #endif
            }
            .alert(appState.uiText("需要设备权限", "Device permissions required"), isPresented: $showInitialCaptureConsent) {
                Button(appState.uiText("同意并继续", "Agree and continue")) {
                    requestDevicePermissionsAndContinue()
                }
                Button(appState.uiText("拒绝并返回", "Decline and return"), role: .cancel) {
                    recordInitialCaptureConsent(granted: false)
                    returnToReadingPark()
                }
            } message: {
                Text(appState.uiText("首次使用拍拍识图时，需要请求图库、相机以及设备朗读相关权限。", "The first time you use Capture & Recognize, the app will request photo library, camera, and device reading permissions."))
            }
            .alert(appState.uiText("需要开启权限", "Permissions required"), isPresented: $showPermissionSettingsAlert) {
                #if os(iOS)
                Button(appState.uiText("前往设置", "Open Settings")) {
                    openSystemSettings()
                }
                #endif
                Button(appState.uiText("取消", "Cancel"), role: .cancel) { }
            } message: {
                Text(permissionSettingsMessage)
            }
            #if os(iOS)
            .onReceive(cameraController.$capturedPhoto.compactMap { $0 }) { payload in
                capturePayload = payload
            }
            #endif
            .onChange(of: capturedImage != nil) { _, hasImage in
                guard hasImage, let capturedImage else { return }
                #if os(iOS)
                capturePayload = CaptureImagePayload(image: capturedImage, cropGeometry: nil)
                #else
                capturePayload = CaptureImagePayload(image: capturedImage)
                #endif
            }
            .onChange(of: capturePayload != nil) { _, hasPayload in
                if hasPayload { showOCRConfirm = true }
            }
            .onChange(of: showOCRConfirm) { _, isShowing in
                if !isShowing {
                    resetCaptureState()
                    #if os(iOS)
                    cameraController.resumeSessionIfNeeded()
                    #endif
                }
            }
            .navigationDestination(isPresented: $showOCRConfirm) {
                if let capturePayload {
                    #if os(iOS)
                    OCRConfirmView(
                        image: capturePayload.image,
                        cropGeometry: capturePayload.cropGeometry,
                        onRetake: {
                            resetCaptureState()
                            cameraController.resumeSessionIfNeeded()
                        }
                    )
                        .environmentObject(appState)
                    #else
                    OCRConfirmView(
                        image: capturePayload.image,
                        onRetake: {
                            resetCaptureState()
                        }
                    )
                        .environmentObject(appState)
                    #endif
                }
            }
            .onAppear {
                #if os(iOS)
                cameraController.resumeSessionIfNeeded()
                #endif
            }
            .task {
                await appState.bootstrapIfNeeded()
                usageChildId = appState.selectedChild.id
                beginCaptureEntryIfNeeded()
                await startUsageSessionIfNeeded()
            }
            .onChange(of: scenePhase) { _, newPhase in
                Task {
                    if newPhase == .active {
                        #if os(iOS)
                        #if !targetEnvironment(simulator)
                        cameraController.refreshAuthorizationAndConfigureIfNeeded()
                        #endif
                        #endif
                        await startUsageSessionIfNeeded()
                    } else {
                        await endUsageSessionIfNeeded()
                    }
                }
            }
            .onChange(of: appState.selectedChild.id) { _, newChildId in
                Task {
                    guard newChildId != usageChildId else { return }
                    await endUsageSessionIfNeeded(refreshParentData: false)
                    usageChildId = newChildId
                    usageSessionId = UUID().uuidString
                    await startUsageSessionIfNeeded()
                }
            }
            .onDisappear {
                #if os(iOS)
                cameraController.stopSession()
                #endif
                Task {
                    await endUsageSessionIfNeeded()
                }
            }
            .onChange(of: appState.requestDismissCaptureCover) { _, shouldDismiss in
                guard shouldDismiss else { return }
                appState.requestDismissCaptureCover = false
                // 关闭外层 fullScreenCover，返回伴读乐园页
                dismiss()
            }
        }
    }

    private var cameraBackdrop: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.08, green: 0.08, blue: 0.09), Color(red: 0.18, green: 0.18, blue: 0.2)], startPoint: .topLeading, endPoint: .bottomTrailing)
            VStack(spacing: 14) {
                Image(systemName: "camera.viewfinder")
                    .font(AppTypography.scaledFont(size: 52, weight: .light))
                    .foregroundColor(.white.opacity(0.2))
                Text(appState.uiText("相机预览区域", "Camera preview area"))
                    .font(AppTypography.title3)
                    .foregroundColor(.white.opacity(0.28))
            }
            RadialGradient(colors: [Color.clear, Color.black.opacity(0.72)], center: .center, startRadius: 80, endRadius: 560)
        }
    }

    private func captureOverlay(proxy: GeometryProxy) -> some View {
        let safeWidth = max(proxy.size.width.isFinite ? proxy.size.width : 0, 320)
        let safeHeight = max(proxy.size.height.isFinite ? proxy.size.height : 0, 560)
        let horizontalPadding = min(max(safeWidth * 0.06, 20), 42)
        let availableWidth = max(safeWidth - horizontalPadding * 2, 120)
        let frameWidth = min(availableWidth, 520)
        let frameHeight = min(max(safeHeight * 0.22, 150), 320)

        return VStack(spacing: 0) {
            headerBar(horizontalPadding: horizontalPadding)
                .padding(.top, max(proxy.safeAreaInsets.top, 18))

            Spacer(minLength: 24)

            VStack(spacing: 12) {
                Text(appState.uiText("尽量只拍一行或一句", "Capture one line or one sentence"))
                    .font(AppTypography.body.weight(.semibold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                viewfinder(width: frameWidth, height: frameHeight)
                #if os(iOS)
                if cameraController.shouldShowLivePreview {
                    zoomBadge
                }
                #endif
                Text(appState.uiText("请把文字放进中间区域", "Place the text inside the middle area"))
                    .font(AppTypography.footnote)
                    .foregroundColor(.white.opacity(0.82))
                    .multilineTextAlignment(.center)
                Text(appState.uiText("建议拍摄单行文字，识别效果更好", "A single line gives better recognition results"))
                    .font(AppTypography.caption)
                    .foregroundColor(.white.opacity(0.72))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, horizontalPadding)
            }
            .frame(maxWidth: .infinity)
            .allowsHitTesting(false)

            Spacer(minLength: 24)

            bottomControls(horizontalPadding: horizontalPadding)
                .padding(.bottom, max(proxy.safeAreaInsets.bottom + 16, 34))
        }
    }

    private func headerBar(horizontalPadding: CGFloat) -> some View {
        HStack {
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(AppTypography.scaledFont(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(appState.uiText("取消拍摄", "Cancel capture"))

            Spacer()

            #if os(iOS)
            Button {
                toggleFlashMode()
            } label: {
                Image(systemName: flashIcon)
                    .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(appState.uiText("切换闪光灯", "Toggle flash"))
            #else
            Image(systemName: "bolt.slash")
                .font(AppTypography.scaledFont(size: 18, weight: .semibold))
                .foregroundColor(.white.opacity(0.6))
                .frame(width: 44, height: 44)
                .background(Color.white.opacity(0.14))
                .clipShape(Circle())
            #endif
        }
        .padding(.horizontal, horizontalPadding)
    }

    private func viewfinder(width: CGFloat, height: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 16, style: .continuous)
            .stroke(Color.white, lineWidth: 3)
            .background(Color.white.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(alignment: .topLeading) { cornerGuides }
            .overlay(alignment: .bottomTrailing) { cornerGuides.rotationEffect(.degrees(180)) }
            .frame(width: width, height: height)
            .shadow(color: Color.black.opacity(0.22), radius: 16, x: 0, y: 8)
            #if os(iOS)
            .background(
                GlobalFrameReporter { frame in
                    updateRecognitionFrame(frame)
                }
            )
            #endif
    }

    #if os(iOS)
    private func updateCameraPreviewFrame(_ frame: CGRect) {
        guard frame.isUsable, cameraPreviewFrame != frame else { return }
        cameraPreviewFrame = frame
        cameraController.setRecognitionFrame(recognitionFrame, in: frame)
    }

    private func updateRecognitionFrame(_ frame: CGRect) {
        guard frame.isUsable, recognitionFrame != frame else { return }
        recognitionFrame = frame
        cameraController.setRecognitionFrame(frame, in: cameraPreviewFrame)
    }
    #endif

    #if os(iOS)
    private var zoomBadge: some View {
        Text(String(format: "%.1fx", cameraController.zoomFactor))
            .font(AppTypography.caption.weight(.semibold))
            .monospacedDigit()
            .foregroundColor(.white)
            .padding(.horizontal, 12)
            .frame(height: 30)
            .background(Color.black.opacity(0.38))
            .clipShape(Capsule())
            .accessibilityLabel(appState.uiText("当前缩放比例", "Current zoom"))
            .accessibilityValue(String(format: "%.1fx", cameraController.zoomFactor))
    }
    #endif

    private var cornerGuides: some View {
        Path { path in
            path.move(to: CGPoint(x: 0, y: 28))
            path.addLine(to: CGPoint(x: 0, y: 0))
            path.addLine(to: CGPoint(x: 28, y: 0))
        }
        .stroke(Color.white, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
        .frame(width: 38, height: 38)
        .padding(10)
    }

    private var isCameraCaptureDisabled: Bool {
        #if os(iOS)
        return cameraController.isCaptureUnavailable
        #else
        return false
        #endif
    }

    private func bottomControls(horizontalPadding: CGFloat) -> some View {
        ZStack {
            Button {
                openPhotoLibrary()
            } label: {
                Image(systemName: "photo.on.rectangle")
                    .font(AppTypography.scaledFont(size: 24, weight: .regular))
                    .foregroundColor(.white)
                    .frame(width: 56, height: 56)
                    .background(Color.white.opacity(0.2))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Color.white, lineWidth: 2))
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel(appState.uiText("从图库上传", "Upload from gallery"))

            Button {
                openCamera()
            } label: {
                ZStack {
                    Circle().fill(Color.white).frame(width: 72, height: 72)
                    Circle().stroke(Color.white, lineWidth: 4).frame(width: 84, height: 84)
                }
                .frame(width: 94, height: 94)
                .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .opacity(isCameraCaptureDisabled ? 0.45 : 1)
            .accessibilityLabel(appState.uiText("拍摄照片", "Take photo"))

            Button {
                dismiss()
            } label: {
                Text(appState.uiText("取消", "Cancel"))
                    .font(AppTypography.footnote.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(width: 64, height: 44)
                    .background(Color.white.opacity(0.16))
                    .clipShape(Capsule())
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, horizontalPadding)
        .frame(maxWidth: 520)
    }

    private func openPhotoLibrary() {
        resetCaptureState()
        #if os(iOS)
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        switch status {
        case .authorized, .limited:
            recordInitialCaptureConsent(granted: true)
            showImagePicker = true
        case .notDetermined:
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    if newStatus == .authorized || newStatus == .limited {
                        recordInitialCaptureConsent(granted: true)
                        showImagePicker = true
                    } else {
                        recordInitialCaptureConsent(granted: false)
                        presentPermissionSettingsAlert(kind: .photos)
                    }
                }
            }
        default:
            presentPermissionSettingsAlert(kind: .photos)
        }
        #else
        showImagePicker = true
        #endif
    }

    private func openCamera() {
        resetCaptureState()
        #if os(iOS)
        #if targetEnvironment(simulator)
        return
        #else
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            recordInitialCaptureConsent(granted: true)
            cameraController.refreshAuthorizationAndConfigureIfNeeded()
            cameraController.capturePhoto(flashMode: flashMode)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted {
                        recordInitialCaptureConsent(granted: true)
                        cameraController.refreshAuthorizationAndConfigureIfNeeded()
                        cameraController.capturePhoto(flashMode: flashMode)
                    } else {
                        recordInitialCaptureConsent(granted: false)
                        presentPermissionSettingsAlert(kind: .camera)
                    }
                }
            }
        default:
            presentPermissionSettingsAlert(kind: .camera)
        }
        #endif
        #else
        showImagePicker = true
        #endif
    }

    private func beginCaptureEntryIfNeeded() {
        #if os(iOS)
        guard shouldRequestInitialPermissions else { return }
        // 仅在系统权限处于未决定状态时才弹出自定义同意说明；
        // 曾被拒绝的情况不再自动退回，允许用户在页内再次点击拍摄/图库按钮时被引导前往系统设置。
        if hasAnyUndeterminedCapturePermission() {
            showInitialCaptureConsent = true
        }
        #endif
    }

    private func resetCaptureState() {
        capturedImage = nil
        capturePayload = nil
        showOCRConfirm = false
        #if os(iOS)
        cameraController.clearCapturedPhoto()
        #endif
    }

    private func hasAnyUndeterminedCapturePermission() -> Bool {
        #if os(iOS)
        let photoUndetermined = PHPhotoLibrary.authorizationStatus(for: .readWrite) == .notDetermined
        #if targetEnvironment(simulator)
        return photoUndetermined
        #else
        let cameraUndetermined = AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined
        return cameraUndetermined || photoUndetermined
        #endif
        #else
        return false
        #endif
    }

    private func recordInitialCaptureConsent(granted: Bool) {
        let defaults = AppScopedDefaults()
        defaults.set(true, forKey: AppDefaultKey.capturePermissionRequested)
        defaults.set(granted, forKey: AppDefaultKey.capturePermissionGranted)
        defaults.set(!granted, forKey: AppDefaultKey.capturePermissionDenied)
    }

    private func requestDevicePermissionsAndContinue() {
        #if os(iOS)
        requestInitialDevicePermissions()
        #endif
    }

    #if os(iOS)
    private enum CapturePermissionKind {
        case camera
        case photos
    }

    private func presentPermissionSettingsAlert(kind: CapturePermissionKind) {
        switch kind {
        case .camera:
            permissionSettingsMessage = appState.uiText(
                "拍拍识图需要使用相机拍摄文字，请在“设置 - 隐私与安全 - 相机”中允许拍拍伴读访问相机。",
                "Capture & Recognize needs the camera to photograph text. Please enable camera access under Settings › Privacy & Security › Camera."
            )
        case .photos:
            permissionSettingsMessage = appState.uiText(
                "拍拍识图需要访问图库选择图片，请在“设置 - 隐私与安全 - 照片”中允许拍拍伴读访问照片。",
                "Capture & Recognize needs photo library access. Please enable it under Settings › Privacy & Security › Photos."
            )
        }
        showPermissionSettingsAlert = true
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func returnToReadingPark() {
        appState.selectedTab = .readingPark
        dismiss()
    }

    private func requestInitialDevicePermissions() {
        #if targetEnvironment(simulator)
        requestPhotoLibraryAccess { photoGranted in
            if photoGranted {
                recordInitialCaptureConsent(granted: true)
            } else {
                recordInitialCaptureConsent(granted: false)
                presentPermissionSettingsAlert(kind: .photos)
            }
        }
        #else
        requestCameraAccess { cameraGranted in
            guard cameraGranted else {
                recordInitialCaptureConsent(granted: false)
                presentPermissionSettingsAlert(kind: .camera)
                return
            }
            requestPhotoLibraryAccess { photoGranted in
                if photoGranted {
                    recordInitialCaptureConsent(granted: true)
                } else {
                    recordInitialCaptureConsent(granted: false)
                    presentPermissionSettingsAlert(kind: .photos)
                }
            }
        }
        #endif
    }

    private func requestCameraAccess(completion: @escaping (Bool) -> Void) {
        #if targetEnvironment(simulator)
        completion(false)
        #else
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraController.refreshAuthorizationAndConfigureIfNeeded()
            completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted {
                        cameraController.refreshAuthorizationAndConfigureIfNeeded()
                    }
                    completion(granted)
                }
            }
        default:
            completion(false)
        }
        #endif
    }

    private func requestPhotoLibraryAccess(completion: @escaping (Bool) -> Void) {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        switch status {
        case .authorized, .limited:
            completion(true)
        case .notDetermined:
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                DispatchQueue.main.async {
                    completion(newStatus == .authorized || newStatus == .limited)
                }
            }
        default:
            completion(false)
        }
    }
    #endif

    #if os(iOS)
    private var flashIcon: String {
        switch flashMode {
        case .on: return "bolt.fill"
        case .off: return "bolt.slash.fill"
        case .auto: return "bolt.badge.a.fill"
        @unknown default: return "bolt.fill"
        }
    }

    private func toggleFlashMode() {
        switch flashMode {
        case .auto: flashMode = .on
        case .on: flashMode = .off
        case .off: flashMode = .auto
        @unknown default: flashMode = .auto
        }
        cameraController.setTorchMode(flashMode)
    }
    #endif

    private func startUsageSessionIfNeeded() async {
        guard !usageSessionActive, appState.hasAuthenticatedSession else { return }
        usageChildId = appState.selectedChild.id
        await appState.startUsageSession(sessionUuid: usageSessionId, sourcePage: "capture")
        usageSessionActive = true
    }

    private func endUsageSessionIfNeeded(refreshParentData: Bool = true) async {
        guard usageSessionActive, appState.hasAuthenticatedSession else { return }
        await appState.endUsageSession(sessionUuid: usageSessionId)
        usageSessionActive = false
        if refreshParentData {
            await appState.refreshParentData()
        }
    }
}

#if os(iOS)
struct RecognitionCropGeometry {
    let recognitionFrameInPreview: CGRect
    let previewSize: CGSize
    let normalizedMetadataRect: CGRect?
}

struct RecognitionCropMapper {
    static func cropRect(
        imageSize: CGSize,
        cropGeometry: RecognitionCropGeometry,
        imageOrientation: UIImage.Orientation = .up
    ) -> CGRect? {
        let imageBounds = CGRect(origin: .zero, size: imageSize)
        guard imageBounds.isUsable,
              cropGeometry.previewSize.width > 1,
              cropGeometry.previewSize.height > 1,
              cropGeometry.recognitionFrameInPreview.width > 1,
              cropGeometry.recognitionFrameInPreview.height > 1 else {
            return nil
        }

        if let normalizedCropRect = cropGeometry.normalizedMetadataRect?.clampedToUnitRect,
           normalizedCropRect.isUsableNormalizedCrop {
            let orientedCropRect = normalizedCropRect.orientedForUprightImage(imageOrientation)
            let cropRect = CGRect(
                x: orientedCropRect.minX * imageSize.width,
                y: orientedCropRect.minY * imageSize.height,
                width: orientedCropRect.width * imageSize.width,
                height: orientedCropRect.height * imageSize.height
            )
            let boundedCropRect = cropRect
                .intersection(imageBounds)
                .pixelAlignedInside
            return boundedCropRect.isUsable ? boundedCropRect : nil
        }

        let previewSize = cropGeometry.previewSize
        let scale = max(previewSize.width / imageSize.width, previewSize.height / imageSize.height)
        guard scale.isFinite, scale > 0 else { return nil }

        let displayedSize = CGSize(width: imageSize.width * scale, height: imageSize.height * scale)
        let displayedOrigin = CGPoint(
            x: (previewSize.width - displayedSize.width) / 2,
            y: (previewSize.height - displayedSize.height) / 2
        )
        let cropFrame = cropGeometry.recognitionFrameInPreview
        let cropRect = CGRect(
            x: (cropFrame.minX - displayedOrigin.x) / scale,
            y: (cropFrame.minY - displayedOrigin.y) / scale,
            width: cropFrame.width / scale,
            height: cropFrame.height / scale
        )
        let boundedCropRect = cropRect
            .intersection(imageBounds)
            .pixelAlignedInside
        return boundedCropRect.isUsable ? boundedCropRect : nil
    }
}

private struct GlobalFrameReporter: View {
    let onChange: (CGRect) -> Void

    var body: some View {
        GeometryReader { proxy in
            Color.clear
                .preference(key: GlobalFramePreferenceKey.self, value: proxy.frame(in: .global))
        }
        .onPreferenceChange(GlobalFramePreferenceKey.self) { frame in
            DispatchQueue.main.async {
                onChange(frame)
            }
        }
    }
}

private struct GlobalFramePreferenceKey: PreferenceKey {
    static var defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

private extension CGRect {
    var isUsable: Bool {
        minX.isFinite &&
        minY.isFinite &&
        width.isFinite &&
        height.isFinite &&
        width > 1 &&
        height > 1 &&
        !isNull &&
        !isInfinite
    }

    var clampedToUnitRect: CGRect {
        let clampedMinX = min(max(minX, 0), 1)
        let clampedMinY = min(max(minY, 0), 1)
        let clampedMaxX = min(max(maxX, 0), 1)
        let clampedMaxY = min(max(maxY, 0), 1)
        return CGRect(
            x: clampedMinX,
            y: clampedMinY,
            width: max(0, clampedMaxX - clampedMinX),
            height: max(0, clampedMaxY - clampedMinY)
        )
    }

    var isUsableNormalizedCrop: Bool {
        minX.isFinite &&
        minY.isFinite &&
        width.isFinite &&
        height.isFinite &&
        width > 0.001 &&
        height > 0.001 &&
        !isNull &&
        !isInfinite
    }

    func orientedForUprightImage(_ orientation: UIImage.Orientation) -> CGRect {
        let oriented: CGRect
        switch orientation {
        case .up:
            oriented = self
        case .down:
            oriented = CGRect(x: 1 - maxX, y: 1 - maxY, width: width, height: height)
        case .left:
            oriented = CGRect(x: minY, y: 1 - maxX, width: height, height: width)
        case .right:
            oriented = CGRect(x: 1 - maxY, y: minX, width: height, height: width)
        case .upMirrored:
            oriented = CGRect(x: 1 - maxX, y: minY, width: width, height: height)
        case .downMirrored:
            oriented = CGRect(x: minX, y: 1 - maxY, width: width, height: height)
        case .leftMirrored:
            oriented = CGRect(x: 1 - maxY, y: 1 - maxX, width: height, height: width)
        case .rightMirrored:
            oriented = CGRect(x: minY, y: minX, width: height, height: width)
        @unknown default:
            oriented = self
        }
        return oriented.clampedToUnitRect
    }

    var pixelAlignedInside: CGRect {
        let alignedMinX = ceil(minX)
        let alignedMinY = ceil(minY)
        let alignedMaxX = floor(maxX)
        let alignedMaxY = floor(maxY)
        return CGRect(
            x: alignedMinX,
            y: alignedMinY,
            width: max(0, alignedMaxX - alignedMinX),
            height: max(0, alignedMaxY - alignedMinY)
        )
    }
}

final class CameraController: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    @Published var capturedPhoto: CaptureImagePayload?
    @Published var authorizationDenied = false
    @Published var livePreviewUnavailable = false
    @Published private(set) var zoomFactor: CGFloat = 1

    var shouldShowLivePreview: Bool {
        !authorizationDenied && !livePreviewUnavailable
    }

    var isCaptureUnavailable: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return livePreviewUnavailable
        #endif
    }

    private let output = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "paipai.camera.session")
    private let productZoomRange: ClosedRange<CGFloat> = 0.5...5
    private var captureDevice: AVCaptureDevice?
    private var zoomGestureBaseFactor: CGFloat = 1
    private var recognitionCropGeometry: RecognitionCropGeometry?
    private var pendingRecognitionCropGeometry: RecognitionCropGeometry?
    private var isConfigured = false

    override init() {
        super.init()
        #if targetEnvironment(simulator)
        livePreviewUnavailable = true
        #else
        refreshAuthorizationAndConfigureIfNeeded()
        #endif
    }

    func capturePhoto(flashMode: UIImagePickerController.CameraFlashMode) {
        #if targetEnvironment(simulator)
        return
        #else
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else { return }
        authorizationDenied = false
        if !isConfigured {
            configureSession()
        }
        sessionQueue.async { [weak self] in
            guard let self, self.isConfigured else { return }
            let settings = AVCapturePhotoSettings()
            if let connection = self.output.connection(with: .video),
               connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if self.output.supportedFlashModes.contains(Self.avFlashMode(from: flashMode)) {
                settings.flashMode = Self.avFlashMode(from: flashMode)
            }
            if let device = self.captureDevice {
                do {
                    try device.lockForConfiguration()
                    if device.isRampingVideoZoom {
                        device.cancelVideoZoomRamp()
                    }
                    let effectiveZoom = self.clampedZoomFactor(device.videoZoomFactor, for: device)
                    device.videoZoomFactor = effectiveZoom
                    device.unlockForConfiguration()
                    DispatchQueue.main.async {
                        self.zoomFactor = effectiveZoom
                    }
                } catch {
                }
            }
            self.pendingRecognitionCropGeometry = self.recognitionCropGeometry
            self.output.capturePhoto(with: settings, delegate: self)
        }
        #endif
    }

    func setTorchMode(_ flashMode: UIImagePickerController.CameraFlashMode) {
        #if targetEnvironment(simulator)
        return
        #else
        sessionQueue.async {
            guard let device = self.captureDevice, device.hasTorch else { return }
            do {
                try device.lockForConfiguration()
                device.torchMode = flashMode == .on ? .on : .off
                device.unlockForConfiguration()
            } catch {
            }
        }
        #endif
    }

    func stopSession() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    func resumeSessionIfNeeded() {
        #if targetEnvironment(simulator)
        return
        #else
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else { return }
        if !isConfigured {
            configureSession()
            return
        }
        sessionQueue.async { [weak self] in
            guard let self, !self.session.isRunning else { return }
            self.session.startRunning()
        }
        #endif
    }

    func clearCapturedPhoto() {
        capturedPhoto = nil
    }

    func setRecognitionFrame(
        _ recognitionFrame: CGRect,
        in previewFrame: CGRect,
        normalizedMetadataRect: CGRect? = nil
    ) {
        guard recognitionFrame.isUsable, previewFrame.isUsable else { return }
        let previewBounds = CGRect(origin: .zero, size: previewFrame.size)
        let relativeFrame = recognitionFrame
            .offsetBy(dx: -previewFrame.minX, dy: -previewFrame.minY)
            .intersection(previewBounds)
        guard relativeFrame.isUsable else { return }

        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: relativeFrame,
            previewSize: previewFrame.size,
            normalizedMetadataRect: normalizedMetadataRect
        )
        sessionQueue.async { [weak self] in
            self?.recognitionCropGeometry = geometry
        }
    }

    func setRecognitionGeometry(
        localRecognitionFrame: CGRect,
        previewSize: CGSize,
        normalizedMetadataRect: CGRect
    ) {
        guard localRecognitionFrame.isUsable,
              previewSize.width > 1,
              previewSize.height > 1,
              normalizedMetadataRect.isUsableNormalizedCrop else {
            return
        }

        let geometry = RecognitionCropGeometry(
            recognitionFrameInPreview: localRecognitionFrame,
            previewSize: previewSize,
            normalizedMetadataRect: normalizedMetadataRect
        )
        sessionQueue.async { [weak self] in
            self?.recognitionCropGeometry = geometry
        }
    }

    func beginZoomGesture() {
        sessionQueue.async { [weak self] in
            guard let self, let device = self.captureDevice else { return }
            self.zoomGestureBaseFactor = device.videoZoomFactor
        }
    }

    func updateZoomGesture(scale: CGFloat, focusPoint: CGPoint?) {
        #if targetEnvironment(simulator)
        return
        #else
        let safeScale = max(scale.isFinite ? scale : 1, 0.01)
        sessionQueue.async { [weak self] in
            guard let self, let device = self.captureDevice else { return }
            let targetZoom = self.clampedZoomFactor(self.zoomGestureBaseFactor * safeScale, for: device)
            do {
                try device.lockForConfiguration()
                if device.isRampingVideoZoom {
                    device.cancelVideoZoomRamp()
                }
                device.ramp(toVideoZoomFactor: targetZoom, withRate: 18)
                if let focusPoint {
                    self.applyFocusAndExposure(at: focusPoint, on: device)
                }
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.zoomFactor = targetZoom
                }
            } catch {
            }
        }
        #endif
    }

    func endZoomGesture(focusPoint: CGPoint?) {
        #if targetEnvironment(simulator)
        return
        #else
        sessionQueue.async { [weak self] in
            guard let self, let device = self.captureDevice else { return }
            do {
                try device.lockForConfiguration()
                if device.isRampingVideoZoom {
                    device.cancelVideoZoomRamp()
                }
                let finalZoom = self.clampedZoomFactor(device.videoZoomFactor, for: device)
                device.videoZoomFactor = finalZoom
                if let focusPoint {
                    self.applyFocusAndExposure(at: focusPoint, on: device)
                }
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.zoomFactor = finalZoom
                }
            } catch {
            }
        }
        #endif
    }

    func refreshAuthorizationAndConfigureIfNeeded() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            authorizationDenied = false
            configureSession()
        case .notDetermined:
            authorizationDenied = false
            livePreviewUnavailable = true
        default:
            authorizationDenied = true
        }
    }

    private func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self, !self.isConfigured else { return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo

            guard let device = Self.preferredBackCameraDevice(),
                  let input = try? AVCaptureDeviceInput(device: device),
                  self.session.canAddInput(input),
                  self.session.canAddOutput(self.output) else {
                self.session.commitConfiguration()
                DispatchQueue.main.async { self.livePreviewUnavailable = true }
                return
            }

            self.captureDevice = device
            self.session.addInput(input)
            self.session.addOutput(self.output)
            do {
                try device.lockForConfiguration()
                let initialZoom = self.clampedZoomFactor(1, for: device)
                device.videoZoomFactor = initialZoom
                if device.isFocusModeSupported(.continuousAutoFocus) {
                    device.focusMode = .continuousAutoFocus
                }
                if device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposureMode = .continuousAutoExposure
                }
                device.unlockForConfiguration()
                DispatchQueue.main.async {
                    self.zoomFactor = initialZoom
                }
            } catch {
            }
            if #unavailable(iOS 16.0) {
                self.output.isHighResolutionCaptureEnabled = true
            }
            self.session.commitConfiguration()
            self.isConfigured = true
            DispatchQueue.main.async {
                self.authorizationDenied = false
                self.livePreviewUnavailable = false
            }
            self.session.startRunning()
        }
    }

    private func clampedZoomFactor(_ value: CGFloat, for device: AVCaptureDevice) -> CGFloat {
        let hardwareMin = CGFloat(device.minAvailableVideoZoomFactor)
        let hardwareMax = CGFloat(device.maxAvailableVideoZoomFactor)
        let lowerBound = max(productZoomRange.lowerBound, hardwareMin)
        let upperBound = min(productZoomRange.upperBound, hardwareMax)
        guard lowerBound <= upperBound else { return hardwareMin }
        return min(max(value, lowerBound), upperBound)
    }

    private func applyFocusAndExposure(at point: CGPoint, on device: AVCaptureDevice) {
        let normalizedPoint = CGPoint(
            x: min(max(point.x, 0), 1),
            y: min(max(point.y, 0), 1)
        )
        if device.isFocusPointOfInterestSupported {
            device.focusPointOfInterest = normalizedPoint
            if device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusMode = .continuousAutoFocus
            } else if device.isFocusModeSupported(.autoFocus) {
                device.focusMode = .autoFocus
            }
        }
        if device.isExposurePointOfInterestSupported {
            device.exposurePointOfInterest = normalizedPoint
            if device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposureMode = .continuousAutoExposure
            } else if device.isExposureModeSupported(.autoExpose) {
                device.exposureMode = .autoExpose
            }
        }
    }

    private static func preferredBackCameraDevice() -> AVCaptureDevice? {
        let preferredTypes: [AVCaptureDevice.DeviceType] = [
            .builtInTripleCamera,
            .builtInDualWideCamera,
            .builtInDualCamera,
            .builtInWideAngleCamera
        ]
        let discovery = AVCaptureDevice.DiscoverySession(deviceTypes: preferredTypes, mediaType: .video, position: .back)
        for type in preferredTypes {
            if let device = discovery.devices.first(where: { $0.deviceType == type }) {
                return device
            }
        }
        return AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else { return }
        let cropGeometry = pendingRecognitionCropGeometry
        DispatchQueue.main.async {
            self.capturedPhoto = CaptureImagePayload(image: image, cropGeometry: cropGeometry)
        }
    }

    private static func avFlashMode(from mode: UIImagePickerController.CameraFlashMode) -> AVCaptureDevice.FlashMode {
        switch mode {
        case .on: return .on
        case .off: return .off
        case .auto: return .auto
        @unknown default: return .auto
        }
    }
}

private extension UIImage {
    func normalizedForPixelEditing() -> UIImage {
        guard imageOrientation != .up else { return self }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

struct CameraPreviewView: UIViewRepresentable {
    @ObservedObject var controller: CameraController
    let recognitionFrame: CGRect

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.backgroundColor = .black
        view.videoPreviewLayer.session = controller.session
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        view.updateRecognitionFrame(recognitionFrame, controller: controller)
        view.onPinchBegan = {
            controller.beginZoomGesture()
        }
        view.onPinchChanged = { scale, point in
            controller.updateZoomGesture(scale: scale, focusPoint: point)
        }
        view.onPinchEnded = { point in
            controller.endZoomGesture(focusPoint: point)
        }
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.videoPreviewLayer.session = controller.session
        uiView.updateRecognitionFrame(recognitionFrame, controller: controller)
    }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
        var onPinchBegan: (() -> Void)?
        var onPinchChanged: ((CGFloat, CGPoint) -> Void)?
        var onPinchEnded: ((CGPoint) -> Void)?

        override init(frame: CGRect) {
            super.init(frame: frame)
            configureGestures()
        }

        required init?(coder: NSCoder) {
            super.init(coder: coder)
            configureGestures()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            guard bounds.width.isFinite, bounds.height.isFinite, bounds.width > 0, bounds.height > 0 else { return }
            videoPreviewLayer.frame = bounds
            applyPortraitOrientationIfSupported()
        }

        func updateRecognitionFrame(_ globalRecognitionFrame: CGRect, controller: CameraController) {
            guard globalRecognitionFrame.isUsable, bounds.isUsable else { return }
            DispatchQueue.main.async { [weak self, weak controller] in
                guard let self, let controller, self.bounds.isUsable else { return }
                self.applyPortraitOrientationIfSupported()
                let localFrame = self.convert(globalRecognitionFrame, from: nil)
                let boundedFrame = localFrame.intersection(self.bounds)
                guard boundedFrame.isUsable else { return }
                let metadataRect = self.videoPreviewLayer
                    .metadataOutputRectConverted(fromLayerRect: boundedFrame)
                    .clampedToUnitRect
                guard metadataRect.isUsableNormalizedCrop else { return }
                controller.setRecognitionGeometry(
                    localRecognitionFrame: boundedFrame,
                    previewSize: self.bounds.size,
                    normalizedMetadataRect: metadataRect
                )
            }
        }

        private func configureGestures() {
            isMultipleTouchEnabled = true
            let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
            pinch.cancelsTouchesInView = false
            addGestureRecognizer(pinch)
        }

        private func applyPortraitOrientationIfSupported() {
            guard let connection = videoPreviewLayer.connection,
                  connection.isVideoOrientationSupported else {
                return
            }
            connection.videoOrientation = .portrait
        }

        @objc private func handlePinch(_ recognizer: UIPinchGestureRecognizer) {
            let previewPoint = recognizer.location(in: self)
            let devicePoint = videoPreviewLayer.captureDevicePointConverted(fromLayerPoint: previewPoint)
            switch recognizer.state {
            case .began:
                onPinchBegan?()
            case .changed:
                onPinchChanged?(recognizer.scale, devicePoint)
            case .ended, .cancelled, .failed:
                onPinchEnded?(devicePoint)
            default:
                break
            }
        }
    }
}

struct PhotoLibraryPicker: UIViewControllerRepresentable {
    @Binding var selectedImage: PlatformImage?
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.photoLibrary) ? .photoLibrary : .savedPhotosAlbum
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) { }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: PhotoLibraryPicker
        init(_ parent: PhotoLibraryPicker) { self.parent = parent }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.selectedImage = image
            }
            parent.dismiss()
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
#else
struct ImagePicker: View {
    @EnvironmentObject var appState: AppState
    @Binding var selectedImage: PlatformImage?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 16) {
            Text(appState.uiText("选择一张图片继续", "Choose an image to continue"))
                .font(AppTypography.headline)
            PrimaryButton(title: appState.uiText("打开文件", "Open File")) {
                let panel = NSOpenPanel()
                panel.allowedContentTypes = [.png, .jpeg, .image]
                panel.allowsMultipleSelection = false
                if panel.runModal() == .OK, let url = panel.url, let image = NSImage(contentsOf: url) {
                    selectedImage = image
                }
                dismiss()
            }
            SecondaryButton(title: appState.uiText("取消", "Cancel")) { dismiss() }
        }
        .padding()
        .frame(minWidth: 320, minHeight: 220)
        .background(AppColors.background)
    }
}
#endif

struct OCRConfirmView: View {
    let image: PlatformImage
    #if os(iOS)
    let cropGeometry: RecognitionCropGeometry?
    #endif
    let onRetake: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var appState: AppState

    @State private var recognizedText = ""
    @State private var preparedPreviewImage: PlatformImage?
    @State private var preparedOCRImage: PreparedOCRImage?
    @State private var isPreparingImage = true
    @State private var isRecognizing = false
    @State private var showReader = false
    @State private var errorMessage: String?
    @State private var isQuotaInsufficient = false
    @State private var didRunInitialOCR = false
    @State private var readerSourceLanguageCode = ""
    @State private var readerTargetLanguageCode = ""
    @State private var selectedTranslationLanguageCode = ""
    @State private var languageSnapshot = CaptureLanguageSnapshot.empty
    @State private var languageSnapshotRefreshTask: Task<Void, Never>?
    #if os(iOS)
    @State private var isRecognizedTextFocused = false
    #endif

    private var isPremiumUser: Bool {
        appState.accountState?.entitlement.backendVerifiedPremiumActive == true
    }

    private var targetLanguageDisplay: String {
        guard let targetLanguageCode = selectedOrDefaultTranslationLanguageCode else {
            return translationLanguageAutoText
        }
        return appState.displayTitle(for: targetLanguageCode)
    }

    private var selectedTranslationLanguageDisplay: String {
        guard let selectedLanguageCode = selectedOrDefaultTranslationMenuLanguageCode else {
            return translationLanguageAutoText
        }
        return appState.displayTitle(for: selectedLanguageCode)
    }

    private var translationLanguageLabelText: String {
        appState.localizedText(
            zhHans: "翻译语种",
            english: "Translation language",
            japanese: "翻訳言語",
            korean: "번역 언어",
            spanish: "Idioma de traducción"
        )
    }

    private var translationLanguageAutoText: String {
        appState.localizedText(
            zhHans: "自动判断",
            english: "Auto",
            japanese: "自動",
            korean: "자동",
            spanish: "Automático"
        )
    }

    private var currentLanguageDecision: CaptureLanguageDecision? {
        guard let targetLanguageCode = selectedOrDefaultTranslationMenuLanguageCode else {
            return nil
        }
        return CaptureLanguageDecision(sourceLanguageCode: languageSnapshot.sourceLanguageCode, targetLanguageCode: targetLanguageCode)
    }

    private var currentSourceLanguageCode: String? {
        languageSnapshot.sourceLanguageCode
    }

    private var translationLanguageOptions: [String] {
        languageSnapshot.translationLanguageOptions
    }

    private var translationLanguageMenuOptions: [String] {
        languageSnapshot.translationLanguageMenuOptions
    }

    private var selectedOrDefaultTranslationMenuLanguageCode: String? {
        let options = translationLanguageMenuOptions
        if let selected = options.first(where: { CaptureLanguageResolver.isSameLanguage($0, selectedTranslationLanguageCode) }) {
            return selected
        }
        return options.first
    }

    private var selectedOrDefaultTranslationLanguageCode: String? {
        selectedOrDefaultTranslationMenuLanguageCode
    }

    private var confirmHintText: String {
        appState.localizedText(
            zhHans: "请确认文字无误后点击进入学习",
            english: "Confirm the text is correct, then tap to start learning.",
            japanese: "文字が正しいことを確認してから、学習を開始してください。",
            korean: "문자가 올바른지 확인한 후 학습을 시작하세요.",
            spanish: "Confirma que el texto sea correcto y toca para empezar a aprender."
        )
    }

    private var isBlockingProcessing: Bool {
        isPreparingImage || isRecognizing
    }

    private var blockingProcessingTitle: String {
        if isPreparingImage {
            return appState.uiText("正在截取图片...", "Cropping image...")
        }
        return appState.uiText("文字识别中...", "Recognizing text...")
    }

    private var blockingProcessingSubtitle: String {
        if isPreparingImage {
            return appState.uiText("请稍等一下，马上看到清晰片段。", "Please wait while we prepare the cropped image.")
        }
        return appState.uiText("小兔正在帮你找句子，完成后会自动填入。", "Paipai is finding the sentence and will fill it in automatically.")
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                HStack(alignment: .center) {
                    retakeTopBar
                    Spacer(minLength: 12)
                    readingParkReturnButton
                }
                previewSection
                    resultSection
                    targetLanguageSection

                    if let errorMessage {
                        Text(errorMessage)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.error)
                            .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, AppLayout.paddingScreen)
                    }

                    bottomActionBar

                    Text(confirmHintText)
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, AppLayout.paddingScreen)
                        .padding(.bottom, 16)
                }
                .padding(AppLayout.paddingScreen)
                .padding(.bottom, AppLayout.spacingL)
                .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
                .contentShape(Rectangle())
        }
        .disabled(isBlockingProcessing)
        .appScrollDismissesKeyboardInteractively()
        .background(AppColors.background.ignoresSafeArea())
        .overlay(alignment: .top) {
            if isBlockingProcessing {
                blockingProcessingOverlay
                    .transition(.opacity)
            }
        }
        .navigationTitle(appState.uiText("图片上传确认", "Image confirmation"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .navigationBar)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .task {
            await appState.bootstrapIfNeeded()
            Task { await appState.refreshAccountState() }
            await Task.yield()
            try? await Task.sleep(nanoseconds: 80_000_000)
            await performInitialOCRIfNeeded()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await appState.refreshAccountState() }
            }
        }
        .onChange(of: recognizedText) { _, _ in
            scheduleLanguageSnapshotRefresh()
        }
        .onChange(of: appState.interfaceLocaleCode) { _, _ in
            scheduleLanguageSnapshotRefresh()
        }
        .onChange(of: appState.selectedChild.id) { _, _ in
            scheduleLanguageSnapshotRefresh()
        }
        .onDisappear {
            languageSnapshotRefreshTask?.cancel()
            languageSnapshotRefreshTask = nil
        }
        .navigationDestination(isPresented: $showReader) {
            LearningDetailView(
                text: recognizedText,
                sourceLanguageCode: readerSourceLanguageCode.isEmpty ? nil : readerSourceLanguageCode,
                targetLanguageCode: readerTargetLanguageCode.isEmpty ? nil : readerTargetLanguageCode
            )
                .environmentObject(appState)
        }
    }

    private var blockingProcessingOverlay: some View {
        ZStack(alignment: .top) {
            Color.black.opacity(0.001)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { }

            PaipaiOCRLoadingBanner(
                title: blockingProcessingTitle,
                subtitle: blockingProcessingSubtitle
            )
            .padding(.top, 14)
            .padding(.horizontal, AppLayout.paddingScreen)
            .frame(maxWidth: 420)
            .onTapGesture { }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .allowsHitTesting(true)
        .zIndex(10)
    }

    private var retakeTopBar: some View {
        Button {
            onRetake()
            dismiss()
        } label: {
            Label(appState.uiText("重新上传图片", "Upload again"), systemImage: "chevron.left")
                .font(AppTypography.footnote.weight(.semibold))
                .foregroundColor(AppColors.textPrimary)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .background(Color.white)
                .clipShape(Capsule())
                .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }

    private var readingParkReturnButton: some View {
        Button {
            appState.selectedTab = .readingPark
            // 关闭外层 fullScreenCover（CaptureView）后
            // NavigationStack 会整体拆除，无需单独 pop 当前页。
            appState.requestDismissCaptureCover = true
        } label: {
            Label(appState.uiText("返回伴读乐园", "Back to Learning Park"), systemImage: "tent.fill")
                .font(AppTypography.footnote.weight(.semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .background(AppColors.primary)
                .clipShape(Capsule())
                .shadow(color: AppColors.primary.opacity(0.24), radius: 8, x: 0, y: 2)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
        }
        .buttonStyle(.plain)
    }

    private var previewSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center) {
                    Text(appState.uiText("识别图片", "Image to recognize"))
                        .font(AppTypography.footnote.weight(.medium))
                        .foregroundColor(AppColors.textSecondary)
                    Spacer()
                }
                ZStack {
                    Color(red: 0.96, green: 0.96, blue: 0.96)
                    if let preparedPreviewImage {
                        platformImageView(preparedPreviewImage)
                            .scaledToFit()
                            .padding(4)
                            .transition(.opacity)
                    } else {
                        VStack(spacing: 10) {
                            PaipaiLoadingMark(size: 48)
                                .accessibilityHidden(true)
                            Text(appState.uiText("正在准备截取后的图片", "Preparing cropped image"))
                                .font(AppTypography.caption.weight(.medium))
                                .foregroundColor(AppColors.textSecondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 180)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
        }
    }

    private var resultSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack(alignment: .center) {
                    Text(appState.uiText("识别结果", "Recognition result"))
                        .font(AppTypography.footnote.weight(.medium))
                        .foregroundColor(AppColors.textSecondary)
                    Spacer()
                    if isRecognizing {
                        Text(appState.uiText("识别中", "Recognizing"))
                            .font(AppTypography.caption.weight(.semibold))
                            .foregroundColor(AppColors.primary)
                    } else {
                        Button {
                            Task { await performOCR() }
                        } label: {
                            Label(appState.uiText("重新识别", "Recognize again"), systemImage: "arrow.clockwise")
                                .font(AppTypography.caption.weight(.semibold))
                                .foregroundColor(AppColors.primary)
                        }
                        .buttonStyle(.plain)
                    }
                }

                if isRecognizing {
                    PaipaiOCRLoadingBanner(
                        title: appState.uiText("文字识别中...", "Recognizing text..."),
                        subtitle: appState.uiText("小兔正在帮你找句子，完成后会自动填入下方。", "Paipai is finding the sentence and will fill it in below.")
                    )
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                ZStack(alignment: .bottomTrailing) {
                    Group {
                    #if os(iOS)
                        RecognizedTextView(
                            text: $recognizedText,
                            isFocused: $isRecognizedTextFocused,
                            maximumLength: 200,
                            doneTitle: appState.uiText("完成", "Done")
                        )
                        .frame(minHeight: 138)
                    #else
                        TextEditor(text: $recognizedText)
                            .font(AppTypography.body)
                            .frame(minHeight: 138)
                            .padding(AppLayout.spacingS)
                            .scrollContentBackground(.hidden)
                            .background(Color.white)
                    #endif
                    }
                    .overlay(
                        RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous)
                            .stroke(AppColors.border, lineWidth: 1)
                    )
                    Text("\(recognizedText.count)/200")
                        .font(AppTypography.caption)
                        .foregroundColor(AppColors.textSecondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.92))
                        .clipShape(Capsule())
                        .padding(10)
                        .allowsHitTesting(false)
                }
                .animation(.easeInOut(duration: 0.2), value: isRecognizing)
            }
        }
    }

    private var targetLanguageSection: some View {
        let options = translationLanguageMenuOptions
        return HStack(spacing: 10) {
            Text(translationLanguageLabelText)
                .font(AppTypography.footnote.weight(.medium))
                .foregroundColor(AppColors.textPrimary)
                .layoutPriority(1)
            Spacer(minLength: 8)
            if options.count <= 1 {
                Text(selectedTranslationLanguageDisplay)
                    .font(AppTypography.footnote.weight(.semibold))
                    .foregroundColor(AppColors.info)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                    .frame(minWidth: 84)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color(red: 0.91, green: 0.97, blue: 1.0))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityLabel(translationLanguageLabelText)
                    .accessibilityValue(selectedTranslationLanguageDisplay)
            } else {
                Menu {
                    ForEach(options, id: \.self) { languageCode in
                        Button {
                            selectedTranslationLanguageCode = languageCode
                        } label: {
                            HStack {
                                Text(appState.displayTitle(for: languageCode))
                                if CaptureLanguageResolver.isSameLanguage(languageCode, selectedOrDefaultTranslationMenuLanguageCode ?? "") {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text(selectedTranslationLanguageDisplay)
                            .font(AppTypography.footnote.weight(.semibold))
                            .foregroundColor(AppColors.info)
                            .lineLimit(1)
                            .minimumScaleFactor(0.78)
                        Image(systemName: "chevron.down")
                            .font(AppTypography.caption.weight(.semibold))
                            .foregroundColor(AppColors.info)
                    }
                    .frame(minWidth: 84)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color(red: 0.91, green: 0.97, blue: 1.0))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(translationLanguageLabelText)
                .accessibilityValue(selectedTranslationLanguageDisplay)
            }
        }
        .padding(.horizontal, AppLayout.paddingCard)
        .padding(.vertical, 8)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
        .shadow(color: Color.black.opacity(0.06), radius: 10, x: 0, y: 2)
    }

    private var bottomActionBar: some View {
        VStack(spacing: 10) {
            HStack(spacing: 12) {
                Button {
                    appState.selectedTab = .readingPark
                    dismiss()
                } label: {
                    Text(appState.uiText("取消", "Cancel"))
                        .font(AppTypography.body.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(Color(red: 0.94, green: 0.94, blue: 0.94))
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)

                Button {
                    Task { await enterLearning() }
                } label: {
                    Label(appState.uiText("确认进入学习", "Confirm and learn"), systemImage: "play.fill")
                        .font(AppTypography.body.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(
                            LinearGradient(
                                colors: [Color(hex: "#FF6B6B"), Color(hex: "#FF8E8E")],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(recognizedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isRecognizing)
                .opacity(recognizedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isRecognizing ? 0.55 : 1)
            }
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private func platformImageView(_ image: PlatformImage) -> some View {
        #if os(iOS)
        Image(uiImage: image).resizable()
        #else
        Image(nsImage: image).resizable()
        #endif
    }

    private func performInitialOCRIfNeeded() async {
        guard !didRunInitialOCR else { return }
        didRunInitialOCR = true
        await performOCR()
    }

    private func performOCR() async {
        await MainActor.run {
            errorMessage = nil
            isQuotaInsufficient = false
            isRecognizing = true
            if preparedOCRImage == nil {
                isPreparingImage = true
            }
        }
        let interfaceLanguageCode = appState.interfaceLocaleCode
        let learningLanguageCodes = appState.learningLanguageCodes(for: appState.selectedChild)
        let ocrService = appState.ocrService
        do {
            let preparedImage = try await prepareImageIfNeeded()

            let quotaValidation = await appState.validateCaptureQuotaBeforeRecognition(requiredAmount: 1)
            guard quotaValidation.isAllowed else {
                await MainActor.run {
                    errorMessage = quotaValidation.message
                    isQuotaInsufficient = true
                    isRecognizing = false
                }
                return
            }
            let recognitionLanguages = CaptureLanguageResolver.ocrRecognitionLanguageCodes(
                interfaceLanguageCode: interfaceLanguageCode,
                learningLanguageCodes: learningLanguageCodes
            )
            let result = try await Task.detached(priority: .userInitiated) { [ocrService, imageData = preparedImage.imageData, recognitionLanguages] in
                try await ocrService.recognizeText(from: imageData, recognitionLanguages: recognitionLanguages, recognitionLevel: .accurate)
            }.value
            let normalizedText = result.text.trimmingCharacters(in: .whitespacesAndNewlines)
            await MainActor.run {
                withAnimation(.easeInOut(duration: 0.18)) {
                    recognizedText = normalizedText
                }
            }
            await refreshLanguageSnapshotNow(text: normalizedText)
            guard !normalizedText.isEmpty else {
                await MainActor.run {
                    errorMessage = appState.uiText("未识别到文字，请重新拍摄或上传更清晰的图片。", "No text was recognized. Please retake or upload a clearer image.")
                    isRecognizing = false
                }
                return
            }
            _ = await appState.recordCaptureUsage(source: "device_ocr")
        } catch {
            await MainActor.run {
                switch error {
                case OCRConfirmViewError.imageEncodingFailed:
                    errorMessage = appState.uiText("图片编码失败", "Failed to encode the image")
                default:
                    errorMessage = appState.localizedErrorMessage(error)
                }
                isPreparingImage = false
                isRecognizing = false
            }
            return
        }
        await MainActor.run {
            isRecognizing = false
        }
    }

    private func prepareImageIfNeeded() async throws -> PreparedOCRImage {
        if let preparedOCRImage {
            return preparedOCRImage
        }

        await MainActor.run {
            isPreparingImage = true
        }

        #if os(iOS)
        let currentCropGeometry = cropGeometry
        let preparedImage = try await Task.detached(priority: .userInitiated) { [image] in
            try image.preparedOCRImage(cropGeometry: currentCropGeometry)
        }.value
        #else
        let preparedImage = try await Task.detached(priority: .userInitiated) { [image] in
            try image.preparedOCRImage()
        }.value
        #endif

        await MainActor.run {
            preparedOCRImage = preparedImage
            withAnimation(.easeInOut(duration: 0.18)) {
                preparedPreviewImage = preparedImage.previewImage
            }
            isPreparingImage = false
        }

        return preparedImage
    }

    private func enterLearning() async {
        dismissRecognizedTextFocus()
        let normalizedText = recognizedText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedText.isEmpty, !isRecognizing else { return }
        await refreshLanguageSnapshotNow(text: normalizedText)
        guard let decision = currentLanguageDecision else { return }
        readerSourceLanguageCode = decision.sourceLanguageCode ?? ""
        readerTargetLanguageCode = decision.targetLanguageCode
        await appState.recordLearningEvent(sourcePage: "capture_confirm")
        showReader = true
    }

    private func scheduleLanguageSnapshotRefresh() {
        languageSnapshotRefreshTask?.cancel()
        let text = recognizedText
        let interfaceLanguageCode = appState.interfaceLocaleCode
        let learningLanguageCodes = appState.learningLanguageCodes(for: appState.selectedChild)
        languageSnapshotRefreshTask = Task {
            // 语言判断会懒加载 NaturalLanguage 模型。输入和移动光标时不应让它反复占用主线程，
            // 因此把计算挪到后台并做短防抖，只在用户停顿后刷新翻译语种菜单。
            try? await Task.sleep(nanoseconds: 160_000_000)
            guard !Task.isCancelled else { return }
            let snapshot = await Task.detached(priority: .utility) {
                CaptureLanguageSnapshot(
                    text: text,
                    interfaceLanguageCode: interfaceLanguageCode,
                    learningLanguageCodes: learningLanguageCodes
                )
            }.value
            guard !Task.isCancelled else { return }
            await MainActor.run {
                applyLanguageSnapshot(snapshot)
            }
        }
    }

    private func refreshLanguageSnapshotNow(text: String? = nil) async {
        languageSnapshotRefreshTask?.cancel()
        languageSnapshotRefreshTask = nil
        let sourceText = text ?? recognizedText
        let interfaceLanguageCode = appState.interfaceLocaleCode
        let learningLanguageCodes = appState.learningLanguageCodes(for: appState.selectedChild)
        let snapshot = await Task.detached(priority: .userInitiated) {
            CaptureLanguageSnapshot(
                text: sourceText,
                interfaceLanguageCode: interfaceLanguageCode,
                learningLanguageCodes: learningLanguageCodes
            )
        }.value
        await MainActor.run {
            applyLanguageSnapshot(snapshot)
        }
    }

    @MainActor
    private func applyLanguageSnapshot(_ snapshot: CaptureLanguageSnapshot) {
        languageSnapshot = snapshot
        let options = snapshot.translationLanguageMenuOptions
        guard !options.isEmpty else {
            selectedTranslationLanguageCode = ""
            return
        }
        if !options.contains(where: { CaptureLanguageResolver.isSameLanguage($0, selectedTranslationLanguageCode) }) {
            selectedTranslationLanguageCode = options[0]
        }
    }

    private func dismissRecognizedTextFocus() {
        #if os(iOS)
        isRecognizedTextFocused = false
        #endif
    }
}

private struct CaptureLanguageSnapshot: Equatable {
    let sourceLanguageCode: String?
    let translationLanguageOptions: [String]
    let translationLanguageMenuOptions: [String]

    static let empty = CaptureLanguageSnapshot(
        sourceLanguageCode: nil,
        translationLanguageOptions: [],
        translationLanguageMenuOptions: []
    )

    init(sourceLanguageCode: String?, translationLanguageOptions: [String], translationLanguageMenuOptions: [String]) {
        self.sourceLanguageCode = sourceLanguageCode
        self.translationLanguageOptions = translationLanguageOptions
        self.translationLanguageMenuOptions = translationLanguageMenuOptions
    }

    init(text: String, interfaceLanguageCode: String, learningLanguageCodes: [String]) {
        let sourceLanguageCode = CaptureLanguageResolver.detectSourceLanguage(
            text: text,
            interfaceLanguageCode: interfaceLanguageCode,
            learningLanguageCodes: learningLanguageCodes
        )
        let translationLanguageOptions = CaptureLanguageResolver.translationLanguageOptions(
            sourceLanguageCode: sourceLanguageCode,
            interfaceLanguageCode: interfaceLanguageCode,
            learningLanguageCodes: learningLanguageCodes
        )
        var menuOptions = translationLanguageOptions
        if let sourceLanguageCode,
           !menuOptions.contains(where: { CaptureLanguageResolver.isSameLanguage($0, sourceLanguageCode) }) {
            menuOptions.append(sourceLanguageCode)
        }

        self.sourceLanguageCode = sourceLanguageCode
        self.translationLanguageOptions = translationLanguageOptions
        self.translationLanguageMenuOptions = menuOptions
    }
}

private struct CaptureLanguageDecision {
    let sourceLanguageCode: String?
    let targetLanguageCode: String
}

private enum CaptureLanguageResolver {
    static func ocrRecognitionLanguageCodes(interfaceLanguageCode: String, learningLanguageCodes: [String]) -> [String] {
        uniqueLanguageCodes([interfaceLanguageCode] + learningLanguageCodes)
    }

    static func detectSourceLanguage(text: String, interfaceLanguageCode: String, learningLanguageCodes: [String]) -> String? {
        detectLanguage(in: text, candidates: uniqueLanguageCodes([interfaceLanguageCode] + learningLanguageCodes))
    }

    static func translationLanguageOptions(sourceLanguageCode: String?, interfaceLanguageCode: String, learningLanguageCodes: [String]) -> [String] {
        let candidates = uniqueLanguageCodes(learningLanguageCodes + [interfaceLanguageCode])
        guard let sourceLanguageCode, !sourceLanguageCode.isEmpty else {
            return candidates
        }
        return candidates.filter { !isSameLanguage($0, sourceLanguageCode) }
    }

    static func isSameLanguage(_ lhs: String, _ rhs: String) -> Bool {
        guard !lhs.isEmpty, !rhs.isEmpty else { return false }
        return languageFamily(lhs) == languageFamily(rhs)
    }

    static func resolve(text: String, interfaceLanguageCode: String, learningLanguageCode: String) -> CaptureLanguageDecision? {
        let displayLanguage = normalizedTranslationLanguageCode(interfaceLanguageCode)
        let learningLanguage = normalizedTranslationLanguageCode(learningLanguageCode)
        guard languageFamily(displayLanguage) != languageFamily(learningLanguage) else { return nil }
        guard let detectedLanguage = detectLanguage(in: text, candidates: [displayLanguage, learningLanguage]) else { return nil }
        let detectedFamily = languageFamily(detectedLanguage)
        if detectedFamily == languageFamily(displayLanguage) {
            return CaptureLanguageDecision(sourceLanguageCode: displayLanguage, targetLanguageCode: learningLanguage)
        }
        if detectedFamily == languageFamily(learningLanguage) {
            return CaptureLanguageDecision(sourceLanguageCode: learningLanguage, targetLanguageCode: displayLanguage)
        }
        return nil
    }

    private static func detectLanguage(in text: String, candidates: [String]) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let families = Set(candidates.map(languageFamily))
        if families.contains("zh"), containsScalar(in: trimmed, ranges: [0x4E00...0x9FFF, 0x3400...0x4DBF]) {
            return candidates.first { languageFamily($0) == "zh" }
        }
        if families.contains("ja"), containsScalar(in: trimmed, ranges: [0x3040...0x30FF]) {
            return candidates.first { languageFamily($0) == "ja" }
        }
        if families.contains("ko"), containsScalar(in: trimmed, ranges: [0xAC00...0xD7AF, 0x1100...0x11FF]) {
            return candidates.first { languageFamily($0) == "ko" }
        }

        let recognizer = NLLanguageRecognizer()
        recognizer.processString(trimmed)
        let hypotheses = recognizer.languageHypotheses(withMaximum: 5)
        for (language, confidence) in hypotheses where confidence >= 0.2 {
            let family = languageFamily(language.rawValue)
            if let candidate = candidates.first(where: { languageFamily($0) == family }) {
                return candidate
            }
        }
        return nil
    }

    private static func normalizedTranslationLanguageCode(_ code: String) -> String {
        let lowered = code.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "_", with: "-")
            .lowercased()
        if lowered == "zh" || lowered.hasPrefix("zh-hans") || lowered.hasPrefix("zh-cn") {
            return "zh-Hans"
        }
        if lowered.hasPrefix("zh-hant") || lowered.hasPrefix("zh-tw") || lowered.hasPrefix("zh-hk") {
            return "zh-Hant"
        }
        if let primary = lowered.split(separator: "-").first {
            return String(primary)
        }
        return lowered
    }

    private static func languageFamily(_ code: String) -> String {
        let normalized = normalizedTranslationLanguageCode(code).lowercased()
        if normalized.hasPrefix("zh") { return "zh" }
        if let primary = normalized.split(separator: "-").first {
            return String(primary)
        }
        return normalized
    }

    private static func containsScalar(in text: String, ranges: [ClosedRange<Int>]) -> Bool {
        text.unicodeScalars.contains { scalar in
            ranges.contains { $0.contains(Int(scalar.value)) }
        }
    }

    private static func uniqueLanguageCodes(_ codes: [String]) -> [String] {
        var result: [String] = []
        for code in codes {
            let normalized = normalizedTranslationLanguageCode(code)
            guard !normalized.isEmpty, !result.contains(where: { languageFamily($0) == languageFamily(normalized) }) else { continue }
            result.append(normalized)
        }
        return result
    }
}

private enum OCRConfirmViewError: Error {
    case imageEncodingFailed
}

private struct PreparedOCRImage {
    let previewImage: PlatformImage?
    let imageData: Data
}

#if os(iOS)
private final class FastRecognizedUITextView: UITextView {
    var onUserTouchBegan: (() -> Void)?
    var onLayoutReady: ((UITextView) -> Void)?
    private var touchBeganPoint: CGPoint?
    private var touchBeganAt: TimeInterval?
    private var didMoveDuringTouch = false
    private weak var preciseTapRecognizer: UITapGestureRecognizer?

    func installPreciseTapRecognizerIfNeeded() {
        if preciseTapRecognizer == nil {
            let recognizer = UITapGestureRecognizer(target: self, action: #selector(handlePreciseSingleTap(_:)))
            recognizer.numberOfTapsRequired = 1
            recognizer.numberOfTouchesRequired = 1
            recognizer.cancelsTouchesInView = true
            recognizer.delaysTouchesBegan = false
            recognizer.delaysTouchesEnded = false
            addGestureRecognizer(recognizer)
            preciseTapRecognizer = recognizer
        }
        suppressSystemTapRecognizers()
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let touch = touches.first {
            touchBeganPoint = touch.location(in: self)
            touchBeganAt = ProcessInfo.processInfo.systemUptime
            didMoveDuringTouch = false
        }
        onUserTouchBegan?()
        super.touchesBegan(touches, with: event)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let touch = touches.first, let touchBeganPoint {
            let point = touch.location(in: self)
            let distance = hypot(point.x - touchBeganPoint.x, point.y - touchBeganPoint.y)
            if distance > 8 {
                didMoveDuringTouch = true
            }
        }
        super.touchesMoved(touches, with: event)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        let point = touches.first?.location(in: self)
        let tapCount = touches.first?.tapCount ?? 1
        let elapsed = touchBeganAt.map { ProcessInfo.processInfo.systemUptime - $0 } ?? 0
        let shouldPlaceCaret = preciseTapRecognizer == nil && point != nil && tapCount == 1 && !didMoveDuringTouch && elapsed < 0.45

        super.touchesEnded(touches, with: event)

        if shouldPlaceCaret, let point {
            DispatchQueue.main.async { [weak self] in
                self?.placeCaretPrecisely(at: point)
            }
        }

        touchBeganPoint = nil
        touchBeganAt = nil
        didMoveDuringTouch = false
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 1, bounds.height > 1 else { return }
        suppressSystemTapRecognizers()
        onLayoutReady?(self)
    }

    func disableLigaturesForCurrentText() {
        let fullRange = NSRange(location: 0, length: ((text ?? "") as NSString).length)
        guard fullRange.length > 0 else { return }
        textStorage.addAttribute(.ligature, value: 0, range: fullRange)
    }

    @objc private func handlePreciseSingleTap(_ recognizer: UITapGestureRecognizer) {
        guard recognizer.state == .ended else { return }
        if !isFirstResponder {
            becomeFirstResponder()
        }
        placeCaretPrecisely(at: recognizer.location(in: self))
    }

    private func suppressSystemTapRecognizers() {
        guard let preciseTapRecognizer else { return }
        for recognizer in gestureRecognizers ?? [] where recognizer !== preciseTapRecognizer {
            let recognizerName = String(describing: type(of: recognizer))
            guard recognizerName.localizedCaseInsensitiveContains("tap") else { continue }
            recognizer.require(toFail: preciseTapRecognizer)
        }
    }

    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        switch action {
        case #selector(select(_:)),
             #selector(selectAll(_:)):
            return false
        default:
            return super.canPerformAction(action, withSender: sender)
        }
    }

    private func placeCaretPrecisely(at viewPoint: CGPoint) {
        guard isFirstResponder else { return }
        layoutIfNeeded()
        layoutManager.ensureLayout(for: textContainer)

        let textLength = ((text ?? "") as NSString).length
        guard textLength > 0, layoutManager.numberOfGlyphs > 0 else {
            selectedRange = NSRange(location: 0, length: 0)
            return
        }

        var containerPoint = viewPoint
        containerPoint.x += contentOffset.x - textContainerInset.left
        containerPoint.y += contentOffset.y - textContainerInset.top

        var insertionFraction: CGFloat = 0
        let characterIndex = layoutManager.characterIndex(
            for: containerPoint,
            in: textContainer,
            fractionOfDistanceBetweenInsertionPoints: &insertionFraction
        )

        let insertionLocation = min(
            max(characterIndex + (insertionFraction >= 0.5 ? 1 : 0), 0),
            textLength
        )

        applyPreciseSelectedRange(NSRange(location: min(max(insertionLocation, 0), textLength), length: 0))
    }

    private func applyPreciseSelectedRange(_ range: NSRange) {
        setPreciseSelectedRange(range)
    }

    private func setPreciseSelectedRange(_ range: NSRange) {
        selectedRange = range
        guard
            let start = position(from: beginningOfDocument, offset: range.location),
            let end = position(from: start, offset: range.length),
            let textRange = textRange(from: start, to: end)
        else {
            return
        }
        selectedTextRange = textRange
    }

}

private struct RecognizedTextView: UIViewRepresentable {
    @Binding var text: String
    @Binding var isFocused: Bool
    let maximumLength: Int
    let doneTitle: String

    func makeUIView(context: Context) -> UITextView {
        let textView = FastRecognizedUITextView()
        textView.delegate = context.coordinator
        textView.backgroundColor = .white
        textView.font = UIFont.preferredFont(forTextStyle: .body)
        textView.textColor = UIColor(AppColors.textPrimary)
        textView.tintColor = UIColor(AppColors.primary)
        textView.keyboardDismissMode = .interactive
        textView.keyboardType = .default
        textView.autocorrectionType = .no
        textView.autocapitalizationType = .sentences
        textView.spellCheckingType = .no
        textView.smartQuotesType = .no
        textView.smartDashesType = .no
        textView.smartInsertDeleteType = .no
        textView.dataDetectorTypes = []
        textView.textDragInteraction?.isEnabled = false
        textView.returnKeyType = .default
        textView.isScrollEnabled = false
        textView.alwaysBounceVertical = false
        textView.inputAssistantItem.leadingBarButtonGroups = []
        textView.inputAssistantItem.trailingBarButtonGroups = []
        textView.textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        textView.textContainer.lineFragmentPadding = 0
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textView.setContentHuggingPriority(.defaultLow, for: .vertical)
        textView.typingAttributes[.ligature] = 0
        textView.text = text
        textView.disableLigaturesForCurrentText()
        textView.installPreciseTapRecognizerIfNeeded()
        context.coordinator.attach(textView: textView)
        context.coordinator.installKeyboardObserver()
        textView.inputAccessoryView = context.coordinator.makeInputAccessoryView(doneTitle: doneTitle)
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        context.coordinator.maximumLength = maximumLength
        context.coordinator.isFocused = $isFocused
        context.coordinator.attach(textView: textView)
        context.coordinator.updateDoneTitle(doneTitle)

        if textView.text != text {
            textView.text = text
            (textView as? FastRecognizedUITextView)?.disableLigaturesForCurrentText()
            context.coordinator.prewarmTextLayoutIfNeeded(textView)
        }

        if isFocused {
            if !textView.isFirstResponder {
                DispatchQueue.main.async {
                    textView.becomeFirstResponder()
                }
            }
        } else if textView.isFirstResponder {
            textView.resignFirstResponder()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text, isFocused: $isFocused, maximumLength: maximumLength, doneTitle: doneTitle)
    }

    static func dismantleUIView(_ uiView: UITextView, coordinator: Coordinator) {
        coordinator.invalidate()
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        private let logger = Logger(subsystem: "com.paipai.readalong", category: "KeyboardPipeline")
        var text: Binding<String>
        var isFocused: Binding<Bool>
        var maximumLength: Int
        private weak var textView: UITextView?
        private weak var attachedTextView: UITextView?
        private weak var doneButton: UIButton?
        private var doneTitle: String
        private var focusTapStartedAt: TimeInterval?
        private var keyboardTapStartedAt: TimeInterval?
        private var caretTapStartedAt: TimeInterval?
        private var warmedLayoutKey = ""
        private var keyboardDidShowObserver: NSObjectProtocol?

        init(text: Binding<String>, isFocused: Binding<Bool>, maximumLength: Int, doneTitle: String) {
            self.text = text
            self.isFocused = isFocused
            self.maximumLength = maximumLength
            self.doneTitle = doneTitle
        }

        func attach(textView: UITextView) {
            self.textView = textView
            guard attachedTextView !== textView else { return }
            attachedTextView = textView
            if let fastTextView = textView as? FastRecognizedUITextView {
                fastTextView.onUserTouchBegan = { [weak self, weak textView] in
                    self?.recordUserTouchBegan(isFirstResponder: textView?.isFirstResponder == true)
                }
                fastTextView.onLayoutReady = { [weak self] textView in
                    self?.prewarmTextLayoutIfNeeded(textView)
                }
            }
        }

        func installKeyboardObserver() {
            guard keyboardDidShowObserver == nil else { return }
            keyboardDidShowObserver = NotificationCenter.default.addObserver(
                forName: UIResponder.keyboardDidShowNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                guard let self, let startedAt = self.keyboardTapStartedAt else { return }
                let elapsedMs = self.elapsedMilliseconds(since: startedAt)
                self.keyboardTapStartedAt = nil
                self.logger.info("ocr_confirm_keyboard_did_show elapsedMs=\(elapsedMs, privacy: .public)")
            }
        }

        func invalidate() {
            if let keyboardDidShowObserver {
                NotificationCenter.default.removeObserver(keyboardDidShowObserver)
            }
            keyboardDidShowObserver = nil
        }

        func prewarmTextLayoutIfNeeded(_ textView: UITextView) {
            let width = Int(textView.bounds.width.rounded())
            let layoutKey = "\(textView.text?.count ?? 0)-\(max(width, 0))"
            guard !layoutKey.isEmpty, layoutKey != warmedLayoutKey else { return }
            warmedLayoutKey = layoutKey
            DispatchQueue.main.async { [weak self, weak textView] in
                guard let self, let textView else { return }
                // 识别结果回填后提前完成 TextKit 布局，避免用户第一次点击正文移动光标时再同步计算行高和字形位置。
                textView.layoutIfNeeded()
                textView.layoutManager.ensureLayout(for: textView.textContainer)
                self.logger.debug("ocr_confirm_text_layout_prewarmed length=\(textView.text.count, privacy: .public)")
            }
        }

        func makeInputAccessoryView(doneTitle: String) -> UIView {
            let toolbar = UIToolbar(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 50))
            toolbar.translatesAutoresizingMaskIntoConstraints = false
            toolbar.heightAnchor.constraint(greaterThanOrEqualToConstant: 50).isActive = true

            let closeButton = UIButton(type: .system)
            closeButton.setTitle("×", for: .normal)
            closeButton.titleLabel?.font = UIFont.systemFont(ofSize: 30, weight: .regular)
            closeButton.titleLabel?.adjustsFontForContentSizeCategory = true
            closeButton.tintColor = UIColor(AppColors.primary)
            closeButton.frame = CGRect(x: 0, y: 0, width: 52, height: 44)
            closeButton.accessibilityLabel = doneTitle
            closeButton.addTarget(self, action: #selector(dismissKeyboard), for: .touchUpInside)
            doneButton = closeButton

            toolbar.items = [
                UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
                UIBarButtonItem(customView: closeButton)
            ]
            return toolbar
        }

        func updateDoneTitle(_ title: String) {
            guard doneTitle != title else { return }
            doneTitle = title
            doneButton?.accessibilityLabel = title
        }

        @objc private func dismissKeyboard() {
            isFocused.wrappedValue = false
            textView?.resignFirstResponder()
        }

        private func recordUserTouchBegan(isFirstResponder: Bool) {
            let now = ProcessInfo.processInfo.systemUptime
            if isFirstResponder {
                // 已经是第一响应者时，触摸主要用于调整光标；只记录到 selection 回调，不触发 SwiftUI 状态更新。
                caretTapStartedAt = now
            } else {
                focusTapStartedAt = now
                keyboardTapStartedAt = now
            }
        }

        private func elapsedMilliseconds(since start: TimeInterval) -> Int {
            max(0, Int((ProcessInfo.processInfo.systemUptime - start) * 1000))
        }

        func textViewDidBeginEditing(_ textView: UITextView) {
            if let startedAt = focusTapStartedAt {
                let elapsedMs = elapsedMilliseconds(since: startedAt)
                focusTapStartedAt = nil
                logger.info("ocr_confirm_input_focus_ready elapsedMs=\(elapsedMs, privacy: .public) targetMs=100")
            }
            if !isFocused.wrappedValue {
                isFocused.wrappedValue = true
            }
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            if isFocused.wrappedValue {
                isFocused.wrappedValue = false
            }
        }

        func textViewShouldBeginEditing(_ textView: UITextView) -> Bool {
            let now = ProcessInfo.processInfo.systemUptime
            if focusTapStartedAt == nil {
                focusTapStartedAt = now
            }
            if keyboardTapStartedAt == nil {
                keyboardTapStartedAt = now
            }
            return true
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard let startedAt = caretTapStartedAt else { return }
            let elapsedMs = elapsedMilliseconds(since: startedAt)
            caretTapStartedAt = nil
            logger.info("ocr_confirm_caret_position_ready elapsedMs=\(elapsedMs, privacy: .public) targetMs=50")
        }

        func textViewDidChange(_ textView: UITextView) {
            let currentText = textView.text ?? ""
            guard currentText.count > maximumLength else {
                guard text.wrappedValue != currentText else { return }
                text.wrappedValue = currentText
                return
            }

            let limitedText = String(currentText.prefix(maximumLength))
            if textView.text != limitedText {
                textView.text = limitedText
            }
            guard text.wrappedValue != limitedText else { return }
            text.wrappedValue = limitedText
            let limitedUTF16Length = (limitedText as NSString).length
            let currentLocation = min(textView.selectedRange.location, limitedUTF16Length)
            textView.selectedRange = NSRange(location: currentLocation, length: 0)
        }

        func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText replacement: String) -> Bool {
            guard let currentRange = Range(range, in: textView.text ?? "") else { return true }
            let currentText = textView.text ?? ""
            let proposedText = currentText.replacingCharacters(in: currentRange, with: replacement)

            guard proposedText.count > maximumLength else { return true }

            if replacement.isEmpty {
                return true
            }

            let existingCount = currentText.count - currentText[currentRange].count
            let remainingCharacters = maximumLength - existingCount
            guard remainingCharacters > 0 else { return false }

            let limitedReplacement = String(replacement.prefix(remainingCharacters))
            let updatedText = currentText.replacingCharacters(in: currentRange, with: limitedReplacement)
            textView.text = updatedText
            text.wrappedValue = updatedText

            let caretLocation = min((range.location + (limitedReplacement as NSString).length), (updatedText as NSString).length)
            textView.selectedRange = NSRange(location: caretLocation, length: 0)
            return false
        }
    }
}
#endif

private struct PaipaiOCRLoadingBanner: View {
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 12) {
            PaipaiLoadingMark(size: 54)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(AppTypography.footnote.weight(.semibold))
                    .foregroundColor(Color(hex: "#16345E"))
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)
                Text(subtitle)
                    .font(AppTypography.caption)
                    .foregroundColor(AppColors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
        .padding(12)
        .background(
            LinearGradient(
                colors: [Color(hex: "#EAF8FF"), Color(hex: "#FFF7DD")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(AppColors.primary.opacity(0.18), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

private struct PaipaiLoadingMark: View {
    let size: CGFloat
    private let spinDuration: TimeInterval = 2.6

    var body: some View {
        TimelineView(.animation) { timeline in
            let progress = timeline.date.timeIntervalSinceReferenceDate
                .truncatingRemainder(dividingBy: spinDuration) / spinDuration
            let angle = Angle.degrees(progress * 360)

            ZStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [AppColors.secondary.opacity(0.26), Color.clear],
                            center: .center,
                            startRadius: 2,
                            endRadius: size * 0.42
                        )
                    )
                    .scaleEffect(0.9 + 0.08 * sin(progress * .pi * 2))

                Circle()
                    .trim(from: 0.08, to: 0.86)
                    .stroke(
                        AngularGradient(
                            colors: [
                                AppColors.accentYellow,
                                Color(hex: "#FFE891"),
                                AppColors.secondary,
                                Color(hex: "#42DFF4"),
                                AppColors.accentYellow
                            ],
                            center: .center
                        ),
                        style: StrokeStyle(lineWidth: max(4, size * 0.08), lineCap: .round)
                    )
                    .rotationEffect(angle)
                    .shadow(color: AppColors.secondary.opacity(0.22), radius: 4, x: 0, y: 2)

                PaipaiScanCorners(size: size)
                    .stroke(Color(hex: "#42DFF4"), style: StrokeStyle(lineWidth: max(3, size * 0.07), lineCap: .round, lineJoin: .round))
                    .rotationEffect(angle)
                    .shadow(color: AppColors.secondary.opacity(0.18), radius: 3, x: 0, y: 2)

                Image("PaipaiLoadingIcon")
                    .resizable()
                    .scaledToFill()
                    .frame(width: size * 0.48, height: size * 0.48)
                    .clipShape(RoundedRectangle(cornerRadius: size * 0.12, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: size * 0.12, style: .continuous)
                            .stroke(Color.white.opacity(0.8), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)
            }
        }
        .frame(width: size, height: size)
    }
}

private struct PaipaiScanCorners: Shape {
    let size: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let inset = max(3, size * 0.06)
        let length = max(12, size * 0.23)
        let minX = rect.minX + inset
        let maxX = rect.maxX - inset
        let minY = rect.minY + inset
        let maxY = rect.maxY - inset

        path.move(to: CGPoint(x: minX, y: minY + length))
        path.addLine(to: CGPoint(x: minX, y: minY))
        path.addLine(to: CGPoint(x: minX + length, y: minY))

        path.move(to: CGPoint(x: maxX - length, y: minY))
        path.addLine(to: CGPoint(x: maxX, y: minY))
        path.addLine(to: CGPoint(x: maxX, y: minY + length))

        path.move(to: CGPoint(x: maxX, y: maxY - length))
        path.addLine(to: CGPoint(x: maxX, y: maxY))
        path.addLine(to: CGPoint(x: maxX - length, y: maxY))

        path.move(to: CGPoint(x: minX + length, y: maxY))
        path.addLine(to: CGPoint(x: minX, y: maxY))
        path.addLine(to: CGPoint(x: minX, y: maxY - length))

        return path
    }
}

struct OCRModeChip: View {
    let title: String
    let subtitle: String
    let isSelected: Bool
    let isDisabled: Bool
    let badgeText: String?
    let action: () -> Void

    init(title: String, subtitle: String, isSelected: Bool, isDisabled: Bool, badgeText: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.isSelected = isSelected
        self.isDisabled = isDisabled
        self.badgeText = badgeText
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 3) {
                    Text(title)
                        .font(AppTypography.footnote.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Text(subtitle)
                        .font(AppTypography.scaledFont(size: 10, weight: .medium))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .foregroundColor(foregroundColor)
                .frame(maxWidth: .infinity, minHeight: 50)
                .padding(.top, badgeText == nil ? 0 : 4)

                if let badgeText {
                    Text(badgeText)
                        .font(AppTypography.scaledFont(size: 8, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(AppColors.error)
                        .clipShape(Capsule())
                        .rotationEffect(.degrees(18))
                        .offset(x: 3, y: -3)
                        .shadow(color: AppColors.error.opacity(0.28), radius: 4, x: 0, y: 2)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(borderColor, lineWidth: isSelected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.62 : 1)
    }

    private var backgroundColor: Color {
        if isDisabled { return Color(red: 0.94, green: 0.94, blue: 0.94) }
        return isSelected ? AppColors.primary.opacity(0.12) : Color.white
    }

    private var foregroundColor: Color {
        if isDisabled { return AppColors.textSecondary }
        return isSelected ? AppColors.primary : AppColors.textPrimary
    }

    private var borderColor: Color {
        if isDisabled { return Color(red: 0.84, green: 0.84, blue: 0.84) }
        return isSelected ? AppColors.primary : AppColors.border
    }
}

private extension PlatformImage {
    #if os(iOS)
    func preparedOCRImage(cropGeometry: RecognitionCropGeometry?) throws -> PreparedOCRImage {
        let frameImage = recognitionFrameImage(cropGeometry: cropGeometry)
        guard let imageData = frameImage.jpegData(compressionQuality: 0.92) else {
            throw OCRConfirmViewError.imageEncodingFailed
        }
        return PreparedOCRImage(
            previewImage: frameImage,
            imageData: imageData
        )
    }

    func recognitionFrameImage(cropGeometry: RecognitionCropGeometry?) -> UIImage {
        guard let cropGeometry else {
            return normalizedForPixelEditing()
        }

        let normalizedImage = normalizedForPixelEditing()
        guard let cgImage = normalizedImage.cgImage,
              cropGeometry.previewSize.width > 1,
              cropGeometry.previewSize.height > 1 else {
            return normalizedImage
        }

        let imageSize = CGSize(width: CGFloat(cgImage.width), height: CGFloat(cgImage.height))
        guard let boundedCropRect = RecognitionCropMapper.cropRect(
                imageSize: imageSize,
                cropGeometry: cropGeometry,
                imageOrientation: imageOrientation
              ),
              let croppedCGImage = cgImage.cropping(to: boundedCropRect) else {
            return normalizedImage
        }
        return UIImage(cgImage: croppedCGImage, scale: 1, orientation: .up)
    }

    private func resizedForOCR(maxSide: CGFloat) -> UIImage {
        let largestSide = max(size.width, size.height)
        guard largestSide.isFinite, largestSide > maxSide, maxSide > 0 else { return self }
        let ratio = maxSide / largestSide
        let targetSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: targetSize, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }
    #else
    func preparedOCRImage() throws -> PreparedOCRImage {
        guard let tiff = self.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff),
              let imageData = rep.representation(using: .jpeg, properties: [.compressionFactor: 0.92]) else {
            throw OCRConfirmViewError.imageEncodingFailed
        }
        return PreparedOCRImage(previewImage: nil, imageData: imageData)
    }
    #endif
}

#if os(iOS)
extension UIApplication {
    /// 关闭当前 key window 的第一响应者输入，即收回软键盘。
    /// 较 resignFirstResponder 更可靠，不依赖讨论链数量。
    func endEditingEverywhere() {
        for scene in connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows {
                window.endEditing(true)
            }
        }
    }
}
#endif
