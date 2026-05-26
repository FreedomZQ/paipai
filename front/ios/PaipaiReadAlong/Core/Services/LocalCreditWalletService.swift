import Foundation
import CryptoKit
import Security
#if canImport(StoreKit)
import StoreKit
#endif

enum LocalCreditServiceType: String, Codable, CaseIterable, Hashable {
    case localOcr = "local_ocr"
    case localTts = "local_tts"
    // 中文说明：云端功能积分先保留类型和存储位，当前版本不在界面展示，也不开放购买和扣减。
    case apiCallCredits = "api_call_credits"
}

struct LocalCreditCostRule: Codable, Hashable {
    let walletType: String
    let serviceType: LocalCreditServiceType
    let featureCode: String
    let actionCode: String
    let costCredits: Int
    let displayName: [String: String]

    func localizedDisplayName(locale: String) -> String {
        let normalized = AppLocaleCatalog.normalize(locale)
        if normalized.hasPrefix("zh") {
            return displayName["zh-Hans"] ?? featureCode
        }
        return displayName["en"] ?? displayName["zh-Hans"] ?? featureCode
    }
}

enum LocalCreditConsumptionPolicy {
    static let policyVersion = "local-credit-cost-20260524-001"

    // 首发合规策略：积分消耗规则固定内置在 App 中，不能从远程配置或个人开发者后端动态下发。
    // 若未来要提高既有核心功能成本，必须新增 featureCode/actionCode，不能修改旧规则稀释已购权益。
    static let items: [LocalCreditCostRule] = [
        LocalCreditCostRule(
            walletType: "local_device",
            serviceType: .localOcr,
            featureCode: "photo_ocr",
            actionCode: "single_capture",
            costCredits: 1,
            displayName: [
                "zh-Hans": "拍照识字",
                "en": "Photo OCR"
            ]
        ),
        LocalCreditCostRule(
            walletType: "local_device",
            serviceType: .localTts,
            featureCode: "read_aloud",
            actionCode: "default",
            costCredits: 1,
            displayName: [
                "zh-Hans": "朗读",
                "en": "Read aloud"
            ]
        )
    ]

    enum PolicyError: LocalizedError {
        case missingRule(featureCode: String, actionCode: String)
        case invalidCost(featureCode: String, actionCode: String)
        case serviceMismatch(expected: LocalCreditServiceType, actual: LocalCreditServiceType)

        var errorDescription: String? {
            switch self {
            case let .missingRule(featureCode, actionCode):
                return "未找到本地积分消耗规则：\(featureCode)/\(actionCode)。"
            case let .invalidCost(featureCode, actionCode):
                return "本地积分消耗规则无效：\(featureCode)/\(actionCode)。"
            case let .serviceMismatch(expected, actual):
                return "本地积分规则服务类型不匹配：需要 \(expected.rawValue)，实际 \(actual.rawValue)。"
            }
        }
    }

    static func rule(
        serviceType: LocalCreditServiceType,
        featureCode: String,
        actionCode: String
    ) throws -> LocalCreditCostRule {
        guard let item = items.first(where: {
            $0.featureCode == featureCode && $0.actionCode == actionCode
        }) else {
            throw PolicyError.missingRule(featureCode: featureCode, actionCode: actionCode)
        }
        guard item.serviceType == serviceType else {
            throw PolicyError.serviceMismatch(expected: serviceType, actual: item.serviceType)
        }
        guard item.costCredits > 0 else {
            throw PolicyError.invalidCost(featureCode: featureCode, actionCode: actionCode)
        }
        return item
    }

    static func cost(
        serviceType: LocalCreditServiceType,
        featureCode: String,
        actionCode: String
    ) throws -> Int {
        try rule(serviceType: serviceType, featureCode: featureCode, actionCode: actionCode).costCredits
    }
}

struct LocalCreditProductDefinition: Hashable {
    let productCode: String
    let productId: String
    let serviceType: LocalCreditServiceType
    let amount: Int

    func displayName(locale: String) -> String {
        let isChinese = AppLocaleCatalog.normalize(locale).hasPrefix("zh")
        switch serviceType {
        case .localOcr, .localTts:
            // 中文说明：商品历史上分属识字/朗读 SKU，前端统一展示为本机功能积分包。
            return isChinese ? "本机功能积分 \(amount)" : "Local feature credits \(amount)"
        case .apiCallCredits:
            return isChinese ? "云端功能积分 \(amount)" : "Cloud feature credits \(amount)"
        }
    }

    func displayDescription(locale: String) -> String {
        let isChinese = AppLocaleCatalog.normalize(locale).hasPrefix("zh")
        switch serviceType {
        case .localOcr, .localTts:
            // 中文说明：说明文案不再区分识字/朗读积分类型，只说明本机总积分的使用范围。
            return isChinese
                ? "获得 \(amount) 本机功能积分。识字和朗读使用同一积分余额，仅保存在此设备。"
                : "Adds \(amount) local feature credits. OCR and read-aloud use the same balance on this device."
        case .apiCallCredits:
            return isChinese
                ? "云端功能积分暂未开放。"
                : "Cloud feature credits are not enabled yet."
        }
    }
}

enum LocalCreditProductCatalog {
    static let definitions: [LocalCreditProductDefinition] = [
        LocalCreditProductDefinition(
            productCode: "local_ocr_100",
            productId: "com.paipai.readalong.local.ocr.100",
            serviceType: .localOcr,
            amount: 100
        ),
        LocalCreditProductDefinition(
            productCode: "local_ocr_300",
            productId: "com.paipai.readalong.local.ocr.300",
            serviceType: .localOcr,
            amount: 300
        ),
        LocalCreditProductDefinition(
            productCode: "local_tts_100",
            productId: "com.paipai.readalong.local.tts.100",
            serviceType: .localTts,
            amount: 100
        ),
        LocalCreditProductDefinition(
            productCode: "local_tts_300",
            productId: "com.paipai.readalong.local.tts.300",
            serviceType: .localTts,
            amount: 300
        )
    ]

    static var productIds: [String] {
        definitions.map(\.productId)
    }

    static func definition(productId: String) -> LocalCreditProductDefinition? {
        definitions.first { $0.productId == productId }
    }

    static func definition(productCode: String) -> LocalCreditProductDefinition? {
        definitions.first { $0.productCode == productCode }
    }

    static func creditProducts(
        locale: String,
        displayInfo: [String: StoreProductDisplayInfo] = [:]
    ) -> [CreditProduct] {
        definitions.map { definition in
            let storeInfo = displayInfo[definition.productId]
            return CreditProduct(
                productCode: definition.productCode,
                packageType: definition.serviceType == .apiCallCredits ? "cloud_feature" : "local_device",
                serviceType: definition.serviceType.rawValue,
                // 中文说明：商品名称和说明使用 App 内置合规文案，避免 App Store Connect
                // 元数据临时写成“次数包/有效期”等旧口径时污染 App 内购买页；价格仍以 StoreKit 返回为准。
                displayName: definition.displayName(locale: locale),
                displayDescription: definition.displayDescription(locale: locale),
                amount: definition.amount,
                quantityUnit: "credit",
                displayPrice: storeInfo?.displayPrice ?? (AppLocaleCatalog.normalize(locale).hasPrefix("zh") ? "以 Apple 确认为准" : "Confirmed by Apple"),
                currency: nil,
                priceAmountCents: nil,
                validDays: 0,
                appStoreProductId: definition.productId,
                enabled: true,
                status: "active",
                sortOrder: definitions.firstIndex(of: definition) ?? 0,
                disabledMessage: nil,
                messageKey: nil
            )
        }
    }
}

struct LocalCreditWalletSnapshot: Codable, Hashable {
    let schemaVersion: Int
    let walletId: String
    let createdAt: String
    let balances: [String: Int]
    let lifetimeGranted: [String: Int]
    let lifetimeConsumed: [String: Int]
    let processedTransactionCount: Int
    let lastMutationSeq: Int
    let lastMutationAt: String?
    let integrityState: String

    static let empty = LocalCreditWalletSnapshot(
        schemaVersion: 1,
        walletId: "",
        createdAt: "",
        balances: LocalCreditWallet.zeroBalances(),
        lifetimeGranted: LocalCreditWallet.zeroBalances(),
        lifetimeConsumed: LocalCreditWallet.zeroBalances(),
        processedTransactionCount: 0,
        lastMutationSeq: 0,
        lastMutationAt: nil,
        integrityState: "unknown"
    )

    static func safetyMode(_ state: String = "safe_mode") -> LocalCreditWalletSnapshot {
        LocalCreditWalletSnapshot(
            schemaVersion: 1,
            walletId: "",
            createdAt: "",
            balances: LocalCreditWallet.zeroBalances(),
            lifetimeGranted: LocalCreditWallet.zeroBalances(),
            lifetimeConsumed: LocalCreditWallet.zeroBalances(),
            processedTransactionCount: 0,
            lastMutationSeq: 0,
            lastMutationAt: nil,
            integrityState: state
        )
    }

    func balance(for serviceType: LocalCreditServiceType) -> Int {
        max(balances[serviceType.rawValue] ?? 0, 0)
    }

    func localDeviceBalance() -> Int {
        // 中文说明：本机功能积分统一展示为总池；兼容旧版本分别写入的识字/朗读余额。
        balance(for: .localOcr) + balance(for: .localTts)
    }

    func lifetimeGranted(for serviceType: LocalCreditServiceType) -> Int {
        max(lifetimeGranted[serviceType.rawValue] ?? 0, 0)
    }

    func localDeviceLifetimeGranted() -> Int {
        // 中文说明：统计本机总积分发放量时合并旧的识字积分和朗读积分账本。
        lifetimeGranted(for: .localOcr) + lifetimeGranted(for: .localTts)
    }

    func lifetimeConsumed(for serviceType: LocalCreditServiceType) -> Int {
        max(lifetimeConsumed[serviceType.rawValue] ?? 0, 0)
    }

    func localDeviceLifetimeConsumed() -> Int {
        // 中文说明：统计本机总积分消耗量时合并旧的识字积分和朗读积分账本。
        lifetimeConsumed(for: .localOcr) + lifetimeConsumed(for: .localTts)
    }
}

actor LocalCreditWalletService {
    static let shared = LocalCreditWalletService()

    enum WalletError: LocalizedError {
        case keychainUnavailable(OSStatus)
        case encryptionFailed
        case decryptionFailed
        case integrityCheckFailed
        case unsupportedProduct(String)
        case revokedTransaction
        case insufficientBalance(LocalCreditServiceType, required: Int, available: Int)
        case invalidAmount

        var errorDescription: String? {
            switch self {
            case let .keychainUnavailable(status):
                return "本机安全存储暂时不可用（\(status)）。"
            case .encryptionFailed:
                return "本机积分钱包加密失败，请稍后重试。"
            case .decryptionFailed:
                return "本机积分钱包暂时无法读取。"
            case .integrityCheckFailed:
                return "本机积分钱包校验失败。为保护权益，已暂停使用付费积分。"
            case let .unsupportedProduct(productId):
                return "当前商品不在本机积分白名单中：\(productId)"
            case .revokedTransaction:
                return "这笔购买已被撤销，不能发放积分。"
            case let .insufficientBalance(serviceType, required, available):
                let label = serviceType == .apiCallCredits ? "云端功能" : "本机功能"
                return "\(label)积分不足：本次需要 \(required)，当前剩余 \(available)。"
            case .invalidAmount:
                return "积分数量无效。"
            }
        }
    }

    private let keychainStore: LocalCreditKeychainStore
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(
        keychainStore: LocalCreditKeychainStore = LocalCreditKeychainStore(),
        encoder: JSONEncoder = JSONEncoder(),
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.keychainStore = keychainStore
        self.encoder = encoder
        self.decoder = decoder
        self.encoder.outputFormatting = [.sortedKeys]
    }

    func snapshot() async throws -> LocalCreditWalletSnapshot {
        let wallet = try loadOrCreateWallet()
        return wallet.snapshot(integrityState: "verified")
    }

    func consume(
        serviceType: LocalCreditServiceType,
        amount: Int = 1,
        reason: String
    ) async throws -> LocalCreditWalletSnapshot {
        guard amount > 0 else { throw WalletError.invalidAmount }
        var wallet = try loadOrCreateWallet()
        let available = wallet.balance(for: serviceType)
        guard available >= amount else {
            throw WalletError.insufficientBalance(serviceType, required: amount, available: available)
        }
        wallet.balances[serviceType.rawValue] = available - amount
        wallet.lifetimeConsumed[serviceType.rawValue] = wallet.lifetimeConsumedValue(for: serviceType) + amount
        wallet.appendMutation(
            type: "consume",
            serviceType: serviceType,
            delta: -amount,
            reason: reason,
            transactionHash: nil
        )
        try save(wallet: wallet)
        return wallet.snapshot(integrityState: "verified")
    }

    func consumeLocalDeviceCredits(amount: Int = 1, reason: String) async throws -> LocalCreditWalletSnapshot {
        guard amount > 0 else { throw WalletError.invalidAmount }
        var wallet = try loadOrCreateWallet()
        let available = wallet.localDeviceBalance()
        guard available >= amount else {
            throw WalletError.insufficientBalance(.localOcr, required: amount, available: available)
        }

        // 中文说明：本机积分按总池扣减；为兼容旧钱包，先扣旧识字余额，再扣旧朗读余额。
        var remainingToConsume = amount
        let ocrDeduction = min(wallet.balance(for: .localOcr), remainingToConsume)
        if ocrDeduction > 0 {
            wallet.balances[LocalCreditServiceType.localOcr.rawValue] = wallet.balance(for: .localOcr) - ocrDeduction
            wallet.lifetimeConsumed[LocalCreditServiceType.localOcr.rawValue] = wallet.lifetimeConsumedValue(for: .localOcr) + ocrDeduction
            wallet.appendMutation(
                type: "consume",
                serviceType: .localOcr,
                delta: -ocrDeduction,
                reason: reason,
                transactionHash: nil
            )
            remainingToConsume -= ocrDeduction
        }

        let ttsDeduction = min(wallet.balance(for: .localTts), remainingToConsume)
        if ttsDeduction > 0 {
            wallet.balances[LocalCreditServiceType.localTts.rawValue] = wallet.balance(for: .localTts) - ttsDeduction
            wallet.lifetimeConsumed[LocalCreditServiceType.localTts.rawValue] = wallet.lifetimeConsumedValue(for: .localTts) + ttsDeduction
            wallet.appendMutation(
                type: "consume",
                serviceType: .localTts,
                delta: -ttsDeduction,
                reason: reason,
                transactionHash: nil
            )
        }

        try save(wallet: wallet)
        return wallet.snapshot(integrityState: "verified")
    }

    func resetLocalWalletAfterParentConfirmation() async throws -> LocalCreditWalletSnapshot {
        try keychainStore.delete(account: LocalCreditKeychainStore.walletAccount)
        try keychainStore.delete(account: LocalCreditKeychainStore.walletKeyAccount)
        let wallet = LocalCreditWallet.createdNow()
        try save(wallet: wallet)
        return wallet.snapshot(integrityState: "verified")
    }

    #if canImport(StoreKit)
    func grantIfNeeded(transaction: StoreKit.Transaction) async throws -> LocalCreditWalletSnapshot {
        guard transaction.revocationDate == nil else {
            throw WalletError.revokedTransaction
        }
        guard let definition = LocalCreditProductCatalog.definition(productId: transaction.productID) else {
            throw WalletError.unsupportedProduct(transaction.productID)
        }
        var wallet = try loadOrCreateWallet()
        let transactionHash = Self.sha256Hex(String(transaction.id))
        if wallet.processedStoreTransactions[transactionHash] != nil {
            return wallet.snapshot(integrityState: "verified")
        }
        wallet.balances[definition.serviceType.rawValue] = wallet.balance(for: definition.serviceType) + definition.amount
        wallet.lifetimeGranted[definition.serviceType.rawValue] = wallet.lifetimeGrantedValue(for: definition.serviceType) + definition.amount
        wallet.processedStoreTransactions[transactionHash] = LocalCreditProcessedStoreTransaction(
            productId: transaction.productID,
            serviceType: definition.serviceType.rawValue,
            amount: definition.amount,
            purchaseDate: AppClock.string(from: transaction.purchaseDate),
            environment: String(describing: transaction.environment),
            finished: true
        )
        wallet.appendMutation(
            type: "grant",
            serviceType: definition.serviceType,
            delta: definition.amount,
            reason: "storekit_purchase",
            transactionHash: transactionHash
        )
        try save(wallet: wallet)
        return wallet.snapshot(integrityState: "verified")
    }
    #endif

    private func loadOrCreateWallet() throws -> LocalCreditWallet {
        guard let sealedWallet = try keychainStore.data(account: LocalCreditKeychainStore.walletAccount) else {
            let wallet = LocalCreditWallet.createdNow()
            try save(wallet: wallet)
            return wallet
        }
        let key = try loadOrCreateSymmetricKey()
        let plainData: Data
        do {
            let sealedBox = try AES.GCM.SealedBox(combined: sealedWallet)
            plainData = try AES.GCM.open(sealedBox, using: key)
        } catch {
            throw WalletError.decryptionFailed
        }
        var wallet = try decoder.decode(LocalCreditWallet.self, from: plainData)
        try validate(wallet: wallet)
        wallet.ensureKnownBalanceKeys()
        return wallet
    }

    private func save(wallet originalWallet: LocalCreditWallet) throws {
        var wallet = originalWallet
        wallet.ensureKnownBalanceKeys()
        wallet.ledgerHash = try ledgerHash(for: wallet)
        let data = try encoder.encode(wallet)
        let key = try loadOrCreateSymmetricKey()
        guard let sealedData = try AES.GCM.seal(data, using: key).combined else {
            throw WalletError.encryptionFailed
        }
        try keychainStore.set(sealedData, account: LocalCreditKeychainStore.walletAccount)
    }

    private func loadOrCreateSymmetricKey() throws -> SymmetricKey {
        if let data = try keychainStore.data(account: LocalCreditKeychainStore.walletKeyAccount) {
            return SymmetricKey(data: data)
        }
        let key = SymmetricKey(size: .bits256)
        let keyData = key.withUnsafeBytes { Data($0) }
        try keychainStore.set(keyData, account: LocalCreditKeychainStore.walletKeyAccount)
        return key
    }

    private func validate(wallet: LocalCreditWallet) throws {
        guard let existingHash = wallet.ledgerHash, !existingHash.isEmpty else { return }
        let expectedHash = try ledgerHash(for: wallet)
        guard existingHash == expectedHash else {
            throw WalletError.integrityCheckFailed
        }
    }

    private func ledgerHash(for wallet: LocalCreditWallet) throws -> String {
        var canonical = wallet
        canonical.ledgerHash = nil
        let data = try encoder.encode(canonical)
        return Self.sha256Hex(data)
    }

    static func sha256Hex(_ value: String) -> String {
        sha256Hex(Data(value.utf8))
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

private struct LocalCreditWallet: Codable {
    var schemaVersion: Int
    var walletId: String
    var createdAt: String
    var balances: [String: Int]
    var lifetimeGranted: [String: Int]
    var lifetimeConsumed: [String: Int]
    var processedStoreTransactions: [String: LocalCreditProcessedStoreTransaction]
    var localMutations: [LocalCreditMutation]
    var lastMutationSeq: Int
    var lastMutationAt: String?
    var ledgerHash: String?

    static func createdNow() -> LocalCreditWallet {
        LocalCreditWallet(
            schemaVersion: 1,
            walletId: "local-\(UUID().uuidString.lowercased())",
            createdAt: AppClock.nowString(),
            balances: zeroBalances(),
            lifetimeGranted: zeroBalances(),
            lifetimeConsumed: zeroBalances(),
            processedStoreTransactions: [:],
            localMutations: [],
            lastMutationSeq: 0,
            lastMutationAt: nil,
            ledgerHash: nil
        )
    }

    static func zeroBalances() -> [String: Int] {
        Dictionary(uniqueKeysWithValues: LocalCreditServiceType.allCases.map { ($0.rawValue, 0) })
    }

    func balance(for serviceType: LocalCreditServiceType) -> Int {
        max(balances[serviceType.rawValue] ?? 0, 0)
    }

    func localDeviceBalance() -> Int {
        // 中文说明：旧版本按识字/朗读分别入账，新版本购买和使用只看本机功能积分总池。
        balance(for: .localOcr) + balance(for: .localTts)
    }

    func lifetimeGrantedValue(for serviceType: LocalCreditServiceType) -> Int {
        max(lifetimeGranted[serviceType.rawValue] ?? 0, 0)
    }

    func lifetimeConsumedValue(for serviceType: LocalCreditServiceType) -> Int {
        max(lifetimeConsumed[serviceType.rawValue] ?? 0, 0)
    }

    mutating func ensureKnownBalanceKeys() {
        for serviceType in LocalCreditServiceType.allCases {
            balances[serviceType.rawValue] = max(balances[serviceType.rawValue] ?? 0, 0)
            lifetimeGranted[serviceType.rawValue] = max(lifetimeGranted[serviceType.rawValue] ?? 0, 0)
            lifetimeConsumed[serviceType.rawValue] = max(lifetimeConsumed[serviceType.rawValue] ?? 0, 0)
        }
    }

    mutating func appendMutation(
        type: String,
        serviceType: LocalCreditServiceType,
        delta: Int,
        reason: String,
        transactionHash: String?
    ) {
        let nextSeq = lastMutationSeq + 1
        let createdAt = AppClock.nowString()
        let seed = "\(nextSeq)|\(type)|\(serviceType.rawValue)|\(delta)|\(reason)|\(transactionHash ?? "")|\(createdAt)"
        let mutation = LocalCreditMutation(
            seq: nextSeq,
            type: type,
            serviceType: serviceType.rawValue,
            delta: delta,
            reason: reason,
            transactionHash: transactionHash,
            createdAt: createdAt,
            entryHash: LocalCreditWalletService.sha256Hex(seed)
        )
        localMutations.append(mutation)
        if localMutations.count > 500 {
            localMutations = Array(localMutations.suffix(500))
        }
        lastMutationSeq = nextSeq
        lastMutationAt = createdAt
    }

    func snapshot(integrityState: String) -> LocalCreditWalletSnapshot {
        LocalCreditWalletSnapshot(
            schemaVersion: schemaVersion,
            walletId: walletId,
            createdAt: createdAt,
            balances: balances,
            lifetimeGranted: lifetimeGranted,
            lifetimeConsumed: lifetimeConsumed,
            processedTransactionCount: processedStoreTransactions.count,
            lastMutationSeq: lastMutationSeq,
            lastMutationAt: lastMutationAt,
            integrityState: integrityState
        )
    }
}

private struct LocalCreditProcessedStoreTransaction: Codable, Hashable {
    let productId: String
    let serviceType: String
    let amount: Int
    let purchaseDate: String
    let environment: String
    let finished: Bool
}

private struct LocalCreditMutation: Codable, Hashable {
    let seq: Int
    let type: String
    let serviceType: String
    let delta: Int
    let reason: String
    let transactionHash: String?
    let createdAt: String
    let entryHash: String
}

struct LocalCreditKeychainStore {
    static let walletAccount = "wallet.v1"
    static let walletKeyAccount = "walletKey.v1"

    private let service: String

    init(bundleIdentifier: String = AppIdentity.bundleIdentifier) {
        service = "com.paipai.readalong.local-wallet.\(bundleIdentifier)"
    }

    func data(account: String) throws -> Data? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess else {
            throw LocalCreditWalletService.WalletError.keychainUnavailable(status)
        }
        return item as? Data
    }

    func set(_ data: Data, account: String) throws {
        var query = baseQuery(account: account)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess {
            return
        }
        guard updateStatus == errSecItemNotFound else {
            throw LocalCreditWalletService.WalletError.keychainUnavailable(updateStatus)
        }

        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        query[kSecAttrSynchronizable as String] = kCFBooleanFalse
        let addStatus = SecItemAdd(query as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw LocalCreditWalletService.WalletError.keychainUnavailable(addStatus)
        }
    }

    func delete(account: String) throws {
        let status = SecItemDelete(baseQuery(account: account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw LocalCreditWalletService.WalletError.keychainUnavailable(status)
        }
    }

    private func baseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            // 本机积分只承诺当前设备持久化，不随 iCloud Keychain 或跨设备备份同步。
            kSecAttrSynchronizable as String: kCFBooleanFalse as Any
        ]
    }
}
