import Foundation
import PowerSync

final class EntitlementRecordRepository {
    private let database: PowerSyncDatabaseProtocol

    init(database: PowerSyncDatabaseProtocol) {
        self.database = database
    }

    func loadPage(accountId: String, serviceType: String?, statusFilter: String? = nil, page: Int, pageSize: Int) async -> EntitlementRecordPage {
        let safePage = max(page, 1)
        let safePageSize = min(max(pageSize, 1), 50)
        let offset = (safePage - 1) * safePageSize
        let normalizedServiceType = normalizedServiceType(serviceType)
        let normalizedStatusFilter = normalizedStatusFilter(statusFilter)
        let now = SyncClock.nowString()
        var whereClause = "app_code = ? AND account_id = ?"
        var parameters: [Any?] = [AppIdentity.appCode, accountId]
        if let normalizedServiceType {
            whereClause += " AND service_type = ?"
            parameters.append(normalizedServiceType)
        }
        if let normalizedStatusFilter {
            switch normalizedStatusFilter {
            case "active":
                whereClause += " AND (COALESCE(expires_at, '') = '' OR expires_at > ?)"
                parameters.append(now)
            case "invalid":
                whereClause += " AND COALESCE(expires_at, '') <> '' AND expires_at <= ?"
                parameters.append(now)
            default:
                break
            }
        }

        let total = (try? await database.getOptional(
            sql: "SELECT COUNT(*) AS total FROM \(ReadingSyncTableName.entitlementRecordCache) WHERE \(whereClause)",
            parameters: parameters
        ) { cursor in
            try cursor.getIntOptional(name: "total") ?? 0
        }) ?? 0

        var pageParameters = parameters
        pageParameters.append(safePageSize)
        pageParameters.append(offset)
        let records = (try? await database.getAll(
            sql: """
                SELECT record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code
                FROM \(ReadingSyncTableName.entitlementRecordCache)
                WHERE \(whereClause)
                ORDER BY acquired_at DESC, record_id DESC
                LIMIT ? OFFSET ?
                """,
            parameters: pageParameters
        ) { cursor in
            EntitlementRecord(
                id: try cursor.getString(name: "record_id"),
                serviceType: try cursor.getString(name: "service_type"),
                grantType: try cursor.getString(name: "grant_type"),
                acquireMethod: try cursor.getString(name: "acquire_method"),
                totalCount: try cursor.getIntOptional(name: "total_count") ?? 0,
                usedCount: try cursor.getIntOptional(name: "used_count") ?? 0,
                remainingCount: try cursor.getIntOptional(name: "remaining_count") ?? 0,
                acquiredAt: try cursor.getStringOptional(name: "acquired_at") ?? "",
                expiresAt: try cursor.getStringOptional(name: "expires_at") ?? "",
                productCode: try cursor.getStringOptional(name: "product_code")
            )
        }) ?? []

        return EntitlementRecordPage(
            page: safePage,
            pageSize: safePageSize,
            hasMore: total > offset + records.count,
            records: records
        )
    }

    func loadActiveCreditSummary(accountId: String, serviceType: String, now: String) async -> EntitlementUsageSummary {
        let normalizedServiceType = normalizedServiceType(serviceType) ?? serviceType
        return (try? await database.getOptional(
            sql: """
                SELECT
                    COALESCE(SUM(total_count), 0) AS total_count,
                    COALESCE(SUM(used_count), 0) AS used_count,
                    COALESCE(SUM(remaining_count), 0) AS remaining_count
                FROM \(ReadingSyncTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND service_type = ?
                  AND LOWER(COALESCE(grant_type, '')) NOT IN ('daily_gift', 'daily_grant')
                  AND (COALESCE(expires_at, '') = '' OR expires_at > ?)
                """,
            parameters: [AppIdentity.appCode, accountId, normalizedServiceType, now]
        ) { cursor in
            let total = max(try cursor.getIntOptional(name: "total_count") ?? 0, 0)
            let used = min(max(try cursor.getIntOptional(name: "used_count") ?? 0, 0), total)
            let remaining = max(try cursor.getIntOptional(name: "remaining_count") ?? max(total - used, 0), 0)
            return EntitlementUsageSummary(
                serviceType: normalizedServiceType,
                totalCount: total,
                usedCount: used,
                remainingCount: remaining
            )
        }) ?? .empty(serviceType: normalizedServiceType)
    }

    func replaceAll(accountId: String, records: [EntitlementRecord], syncedAt: String) async {
        _ = try? await database.execute(
            sql: """
                DELETE FROM \(ReadingSyncTableName.entitlementRecordCache)
                WHERE app_code = ? AND account_id = ?
                """,
            parameters: [AppIdentity.appCode, accountId]
        )
        for record in records {
            _ = try? await database.execute(
                sql: """
                    INSERT OR REPLACE INTO \(ReadingSyncTableName.entitlementRecordCache)
                    (id, app_code, account_id, record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code, synced_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                parameters: [
                    cacheId(accountId: accountId, recordId: record.id),
                    AppIdentity.appCode,
                    accountId,
                    record.id,
                    record.serviceType,
                    record.grantType,
                    record.acquireMethod,
                    record.totalCount,
                    record.usedCount,
                    record.remainingCount,
                    record.acquiredAt,
                    record.expiresAt,
                    record.productCode,
                    syncedAt
                ]
            )
        }
    }

    func upsertDailyGrant(accountId: String, serviceType: String, totalCount: Int, usedCount: Int, quotaDate: String, acquiredAt: String, expiresAt: String, syncedAt: String) async {
        let safeTotal = max(totalCount, 0)
        let safeUsed = min(max(usedCount, 0), safeTotal)
        let recordId = dailyGrantRecordId(accountId: accountId, serviceType: serviceType, quotaDate: quotaDate)
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingSyncTableName.entitlementRecordCache)
                (id, app_code, account_id, record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            parameters: [
                cacheId(accountId: accountId, recordId: recordId),
                AppIdentity.appCode,
                accountId,
                recordId,
                serviceType,
                "daily_grant",
                "每日赠送",
                safeTotal,
                safeUsed,
                max(safeTotal - safeUsed, 0),
                acquiredAt,
                expiresAt,
                nil,
                syncedAt
            ]
        )
    }

    func incrementDailyGrantUsage(accountId: String, serviceType: String, amount: Int, quotaDate: String, totalCount: Int, acquiredAt: String, expiresAt: String, syncedAt: String) async {
        guard amount > 0 else { return }
        let recordId = dailyGrantRecordId(accountId: accountId, serviceType: serviceType, quotaDate: quotaDate)
        let existing = try? await database.getOptional(
            sql: """
                SELECT used_count
                FROM \(ReadingSyncTableName.entitlementRecordCache)
                WHERE app_code = ? AND account_id = ? AND record_id = ?
                """,
            parameters: [AppIdentity.appCode, accountId, recordId]
        ) { cursor in
            try cursor.getIntOptional(name: "used_count") ?? 0
        }
        let nextUsed = min(max((existing ?? 0) + amount, 0), max(totalCount, 0))
        await upsertDailyGrant(
            accountId: accountId,
            serviceType: serviceType,
            totalCount: totalCount,
            usedCount: nextUsed,
            quotaDate: quotaDate,
            acquiredAt: acquiredAt,
            expiresAt: expiresAt,
            syncedAt: syncedAt
        )
    }

    func incrementCachedDailyGrantUsage(accountId: String, serviceType: String, amount: Int, quotaDate: String, fallbackTotalCount: Int, acquiredAt: String, expiresAt: String, syncedAt: String) async {
        guard amount > 0 else { return }
        let normalizedServiceType = normalizedServiceType(serviceType) ?? serviceType
        let backendRecordId = "daily-\(normalizedServiceType)-\(quotaDate)"
        let localRecordId = dailyGrantRecordId(accountId: accountId, serviceType: normalizedServiceType, quotaDate: quotaDate)
        let existing = try? await database.getOptional(
            sql: """
                SELECT record_id, total_count, used_count
                FROM \(ReadingSyncTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND service_type = ?
                  AND grant_type IN ('daily_gift', 'daily_grant')
                  AND record_id IN (?, ?)
                ORDER BY CASE WHEN record_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """,
            parameters: [AppIdentity.appCode, accountId, normalizedServiceType, backendRecordId, localRecordId, backendRecordId]
        ) { cursor in
            (
                recordId: try cursor.getString(name: "record_id"),
                totalCount: try cursor.getIntOptional(name: "total_count") ?? 0,
                usedCount: try cursor.getIntOptional(name: "used_count") ?? 0
            )
        }
        guard let existing else {
            await upsertDailyGrant(
                accountId: accountId,
                serviceType: normalizedServiceType,
                totalCount: fallbackTotalCount,
                usedCount: min(max(amount, 0), max(fallbackTotalCount, 0)),
                quotaDate: quotaDate,
                acquiredAt: acquiredAt,
                expiresAt: expiresAt,
                syncedAt: syncedAt
            )
            return
        }
        let safeTotal = max(existing.totalCount, fallbackTotalCount, 0)
        let nextUsed = min(max(existing.usedCount + amount, 0), safeTotal)
        _ = try? await database.execute(
            sql: """
                UPDATE \(ReadingSyncTableName.entitlementRecordCache)
                SET total_count = ?,
                    used_count = ?,
                    remaining_count = ?,
                    synced_at = ?
                WHERE app_code = ?
                  AND account_id = ?
                  AND record_id = ?
                """,
            parameters: [
                safeTotal,
                nextUsed,
                max(safeTotal - nextUsed, 0),
                syncedAt,
                AppIdentity.appCode,
                accountId,
                existing.recordId
            ]
        )
    }

    func hasAuthoritativeDailyGift(accountId: String, serviceType: String, quotaDate: String) async -> Bool {
        let normalizedServiceType = normalizedServiceType(serviceType) ?? serviceType
        let expectedRecordId = "daily-\(normalizedServiceType)-\(quotaDate)"
        let count = (try? await database.getOptional(
            sql: """
                SELECT COUNT(*) AS total
                FROM \(ReadingSyncTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND service_type = ?
                  AND grant_type = 'daily_gift'
                  AND record_id = ?
                """,
            parameters: [AppIdentity.appCode, accountId, normalizedServiceType, expectedRecordId]
        ) { cursor in
            try cursor.getIntOptional(name: "total") ?? 0
        }) ?? 0
        return count > 0
    }

    func clear(accountId: String) async {
        _ = try? await database.execute(
            sql: "DELETE FROM \(ReadingSyncTableName.entitlementRecordCache) WHERE app_code = ? AND account_id = ?",
            parameters: [AppIdentity.appCode, accountId]
        )
    }

    private func normalizedServiceType(_ serviceType: String?) -> String? {
        guard let serviceType = serviceType?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !serviceType.isEmpty,
              serviceType != "all" else {
            return nil
        }
        return serviceType
    }

    private func normalizedStatusFilter(_ statusFilter: String?) -> String? {
        guard let statusFilter = statusFilter?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !statusFilter.isEmpty,
              statusFilter != "all" else {
            return nil
        }
        if statusFilter == "valid" || statusFilter == "active" {
            return "active"
        }
        if statusFilter == "invalid" || statusFilter == "expired" {
            return "invalid"
        }
        return nil
    }

    private func cacheId(accountId: String, recordId: String) -> String {
        "\(accountId)#\(recordId)"
    }

    private func dailyGrantRecordId(accountId: String, serviceType: String, quotaDate: String) -> String {
        "local_daily_grant#\(accountId)#\(serviceType)#\(quotaDate)"
    }
}
