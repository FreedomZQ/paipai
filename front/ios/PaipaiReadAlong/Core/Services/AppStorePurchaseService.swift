import Foundation
#if canImport(StoreKit)
import StoreKit
#endif

struct StoreProductDisplayInfo: Hashable {
    let productId: String
    let displayName: String
    let displayPrice: String
}

@MainActor
final class AppStorePurchaseService {
    enum PurchaseError: LocalizedError {
        case authenticationRequired
        case productIdMissing
        case productNotFound(String)
        case verificationFailed(String)
        case userCancelled
        case storeKitUnavailable
        case nothingToRestore

        var errorDescription: String? {
            switch self {
            case .authenticationRequired:
                return "请先使用 Apple 登录，再发起购买或恢复购买。"
            case .productIdMissing:
                return "当前方案暂时还不能购买，请稍后再试。"
            case .productNotFound:
                return "当前购买项暂时不可用，请稍后再试。"
            case let .verificationFailed(message):
                return message
            case .userCancelled:
                return "已取消购买。"
            case .storeKitUnavailable:
                return "当前设备暂时无法完成购买，请稍后再试。"
            case .nothingToRestore:
                return "当前 Apple 账号下没有可恢复的订阅。"
            }
        }
    }

    func productDisplayInfo(for productIds: [String]) async -> [String: StoreProductDisplayInfo] {
        let uniqueProductIds = Array(Set(productIds.filter { !$0.isEmpty }))
        guard !uniqueProductIds.isEmpty else { return [:] }
        #if canImport(StoreKit)
        do {
            let products = try await Product.products(for: uniqueProductIds)
            return Dictionary(uniqueKeysWithValues: products.map { product in
                (
                    product.id,
                    StoreProductDisplayInfo(
                        productId: product.id,
                        displayName: product.displayName,
                        displayPrice: product.displayPrice
                    )
                )
            })
        } catch {
            return [:]
        }
        #else
        return [:]
        #endif
    }

    func purchase(plan: Plan, backend: BackendClient) async throws -> TransactionIntakeReceipt {
        guard backend.hasAuthenticatedSession else {
            throw PurchaseError.authenticationRequired
        }
        guard let productId = plan.appStoreProductId, !productId.isEmpty else {
            throw PurchaseError.productIdMissing
        }

        #if canImport(StoreKit)
        let products = try await Product.products(for: [productId])
        guard let product = products.first else {
            throw PurchaseError.productNotFound(productId)
        }
        let result = try await product.purchase()
        switch result {
        case let .success(verification):
            let jws = verification.jwsRepresentation
            let transaction = try verifiedTransaction(from: verification)
            let receipt = try await backend.submitTransactionIntake(
                source: .purchase,
                payload: StoreTransactionPayload(
                    productId: transaction.productID,
                    transactionId: String(transaction.id),
                    originalTransactionId: String(transaction.originalID),
                    environment: String(describing: transaction.environment),
                    storefront: nil,
                    appAccountToken: transaction.appAccountToken?.uuidString,
                    signedTransactionInfo: jws,
                    signedRenewalInfo: nil
                )
            )
            await transaction.finish()
            return receipt
        case .pending:
            throw PurchaseError.verificationFailed("购买正在处理中，等 Apple 确认完成后，再回来点“恢复购买”更新权益。")
        case .userCancelled:
            throw PurchaseError.userCancelled
        @unknown default:
            throw PurchaseError.verificationFailed("这次购买暂时没有明确结果，请稍后再试。")
        }
        #else
        throw PurchaseError.storeKitUnavailable
        #endif
    }

    func restore(backend: BackendClient) async throws -> [TransactionIntakeReceipt] {
        guard backend.hasAuthenticatedSession else {
            throw PurchaseError.authenticationRequired
        }

        #if canImport(StoreKit)
        try await AppStore.sync()
        var receipts: [TransactionIntakeReceipt] = []
        for await result in Transaction.currentEntitlements {
            let jws = result.jwsRepresentation
            let transaction = try verifiedTransaction(from: result)
            let receipt = try await backend.submitTransactionIntake(
                source: .restore,
                payload: StoreTransactionPayload(
                    productId: transaction.productID,
                    transactionId: String(transaction.id),
                    originalTransactionId: String(transaction.originalID),
                    environment: String(describing: transaction.environment),
                    storefront: nil,
                    appAccountToken: transaction.appAccountToken?.uuidString,
                    signedTransactionInfo: jws,
                    signedRenewalInfo: nil
                )
            )
            receipts.append(receipt)
            await transaction.finish()
        }
        guard !receipts.isEmpty else {
            throw PurchaseError.nothingToRestore
        }
        return receipts
        #else
        throw PurchaseError.storeKitUnavailable
        #endif
    }

    #if canImport(StoreKit)
    private func verifiedTransaction(from result: VerificationResult<Transaction>) throws -> Transaction {
        switch result {
        case let .verified(transaction):
            return transaction
        case let .unverified(_, error):
            throw PurchaseError.verificationFailed(error.localizedDescription)
        }
    }
    #endif
}
