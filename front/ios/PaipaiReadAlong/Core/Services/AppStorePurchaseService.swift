import Foundation
#if canImport(StoreKit)
import StoreKit
#endif

struct StoreProductDisplayInfo: Hashable {
    let productId: String
    let displayName: String
    let displayPrice: String
}

struct LocalPurchaseRefreshResult: Hashable {
    let snapshot: LocalCreditWalletSnapshot
    let processedTransactionCount: Int
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
                return "请先通过家长验证，再发起购买或恢复购买。"
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
                return "没有新的可恢复项目。本机积分依赖当前设备保存，换机或抹掉设备后可能无法恢复。"
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

    func purchaseLocalCredits(product: CreditProduct, wallet: LocalCreditWalletService) async throws -> LocalCreditWalletSnapshot {
        guard let productId = product.appStoreProductId, !productId.isEmpty else {
            throw PurchaseError.productIdMissing
        }
        guard LocalCreditProductCatalog.definition(productId: productId) != nil else {
            throw LocalCreditWalletService.WalletError.unsupportedProduct(productId)
        }

        #if canImport(StoreKit)
        let products = try await Product.products(for: [productId])
        guard let storeProduct = products.first else {
            throw PurchaseError.productNotFound(productId)
        }
        let result = try await storeProduct.purchase()
        switch result {
        case let .success(verification):
            let transaction = try verifiedTransaction(from: verification)
            let snapshot = try await wallet.grantIfNeeded(transaction: transaction)
            await transaction.finish()
            return snapshot
        case .pending:
            throw PurchaseError.verificationFailed("购买正在等待 Apple 确认。稍后可在家长区点“恢复/刷新购买状态”处理未完成购买。")
        case .userCancelled:
            throw PurchaseError.userCancelled
        @unknown default:
            throw PurchaseError.verificationFailed("这次购买暂时没有明确结果，请稍后再试。")
        }
        #else
        throw PurchaseError.storeKitUnavailable
        #endif
    }

    func restoreLocalPurchases(wallet: LocalCreditWalletService) async throws -> LocalPurchaseRefreshResult {
        #if canImport(StoreKit)
        try await AppStore.sync()
        return try await processUnfinishedLocalTransactions(wallet: wallet)
        #else
        throw PurchaseError.storeKitUnavailable
        #endif
    }

    #if canImport(StoreKit)
    func processUnfinishedLocalTransactions(wallet: LocalCreditWalletService) async throws -> LocalPurchaseRefreshResult {
        var processedCount = 0
        var latestSnapshot = try await wallet.snapshot()
        for await result in Transaction.unfinished {
            let transaction = try verifiedTransaction(from: result)
            guard transaction.revocationDate == nil else {
                await transaction.finish()
                continue
            }
            guard LocalCreditProductCatalog.definition(productId: transaction.productID) != nil else {
                continue
            }
            let beforeCount = latestSnapshot.processedTransactionCount
            latestSnapshot = try await wallet.grantIfNeeded(transaction: transaction)
            if latestSnapshot.processedTransactionCount > beforeCount {
                processedCount += 1
            }
            await transaction.finish()
        }
        return LocalPurchaseRefreshResult(snapshot: latestSnapshot, processedTransactionCount: processedCount)
    }

    func processTransactionUpdate(_ result: VerificationResult<Transaction>, wallet: LocalCreditWalletService) async throws -> LocalPurchaseRefreshResult {
        let transaction = try verifiedTransaction(from: result)
        if transaction.revocationDate != nil {
            await transaction.finish()
            return LocalPurchaseRefreshResult(snapshot: try await wallet.snapshot(), processedTransactionCount: 0)
        }
        guard LocalCreditProductCatalog.definition(productId: transaction.productID) != nil else {
            return LocalPurchaseRefreshResult(snapshot: try await wallet.snapshot(), processedTransactionCount: 0)
        }
        let beforeSnapshot = try await wallet.snapshot()
        let snapshot = try await wallet.grantIfNeeded(transaction: transaction)
        await transaction.finish()
        return LocalPurchaseRefreshResult(
            snapshot: snapshot,
            processedTransactionCount: snapshot.processedTransactionCount > beforeSnapshot.processedTransactionCount ? 1 : 0
        )
    }

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
