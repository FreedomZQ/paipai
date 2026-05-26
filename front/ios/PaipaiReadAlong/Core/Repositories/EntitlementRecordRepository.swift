import Foundation

final class EntitlementRecordRepository {
    private let database: LocalDatabase

    init(database: LocalDatabase) {
        self.database = database
    }

    func loadPage(accountId: String, serviceType: String?, statusFilter: String? = nil, page: Int, pageSize: Int) async -> EntitlementRecordPage {
        let safePage = max(page, 1)
        let safePageSize = min(max(pageSize, 1), 50)
        let offset = (safePage - 1) * safePageSize
        let normalizedServiceType = normalizedServiceType(serviceType)
        let normalizedStatusFilter = normalizedStatusFilter(statusFilter)
        let now = AppClock.nowString()
        var whereClause = "app_code = ? AND account_id = ?"
        var parameters: [Any?] = [AppIdentity.appCode, accountId]
        // 中文说明：新版每日赠送只展示 local_device 单条记录；旧版本遗留的识字/朗读拆分日赠记录不再进入列表。
        whereClause += " AND NOT (grant_type IN ('daily_gift', 'daily_grant') AND service_type IN ('local_ocr', 'local_tts', 'device_ocr', 'device_tts'))"
        if let normalizedServiceType {
            let aliases = serviceTypeAliases(for: normalizedServiceType)
            whereClause += " AND service_type IN (\(placeholders(count: aliases.count)))"
            parameters.append(contentsOf: aliases)
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
            sql: "SELECT COUNT(*) AS total FROM \(ReadingLocalTableName.entitlementRecordCache) WHERE \(whereClause)",
            parameters: parameters
        ) { cursor in
            try cursor.getIntOptional(name: "total") ?? 0
        }) ?? 0

        var pageParameters = parameters
        pageParameters.append(safePageSize)
        pageParameters.append(offset)
        let records: [EntitlementRecord] = (try? await database.getAll(
            sql: """
                SELECT record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code
                FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE \(whereClause)
                ORDER BY acquired_at DESC, record_id DESC
                LIMIT ? OFFSET ?
                """,
            parameters: pageParameters
        ) { cursor in
            let rawServiceType = try cursor.getString(name: "service_type")
            return EntitlementRecord(
                id: try cursor.getString(name: "record_id"),
                serviceType: self.normalizedServiceType(rawServiceType) ?? rawServiceType,
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
        let aliases = serviceTypeAliases(for: normalizedServiceType)
        return (try? await database.getOptional(
            sql: """
                SELECT
                    COALESCE(SUM(total_count), 0) AS total_count,
                    COALESCE(SUM(used_count), 0) AS used_count,
                    COALESCE(SUM(remaining_count), 0) AS remaining_count
                FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND service_type IN (\(placeholders(count: aliases.count)))
                  AND LOWER(COALESCE(grant_type, '')) NOT IN ('daily_gift', 'daily_grant')
                  AND (COALESCE(expires_at, '') = '' OR expires_at > ?)
                """,
            parameters: [AppIdentity.appCode, accountId] + aliases + [now]
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
        let localCompensationRecords = await loadLocalCompensationRecords(accountId: accountId)
        let incomingKeys = Set(records.compactMap { record -> String? in
            guard let productCode = record.productCode, !productCode.isEmpty else { return nil }
            let serviceType = normalizedServiceType(record.serviceType) ?? record.serviceType
            return compensationKey(serviceType: serviceType, compensationCode: productCode)
        })
        _ = try? await database.execute(
            sql: """
                DELETE FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE app_code = ? AND account_id = ?
                """,
            parameters: [AppIdentity.appCode, accountId]
        )
        for record in records {
            let serviceType = normalizedServiceType(record.serviceType) ?? record.serviceType
            _ = try? await database.execute(
                sql: """
                    INSERT OR REPLACE INTO \(ReadingLocalTableName.entitlementRecordCache)
                    (id, app_code, account_id, record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code, synced_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                parameters: [
                    cacheId(accountId: accountId, recordId: record.id),
                    AppIdentity.appCode,
                    accountId,
                    record.id,
                    serviceType,
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
        for record in localCompensationRecords where !incomingKeys.contains(compensationKey(serviceType: record.serviceType, compensationCode: record.productCode ?? "")) {
            await upsertCachedRecord(accountId: accountId, record: record, syncedAt: syncedAt)
        }
    }

    func upsertCompensationRedeemReceipt(accountId: String, receipt: CompensationRedeemReceipt, syncedAt: String) async {
        guard let serviceType = normalizedServiceType(receipt.serviceType),
              let grantCount = receipt.grantCount,
              grantCount > 0 else {
            return
        }
        if await hasCompensationRecord(accountId: accountId, serviceType: serviceType, compensationCode: receipt.compensationCode) {
            return
        }
        let recordId = "compensation#\(receipt.compensationCode)#\(serviceType)"
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.entitlementRecordCache)
                (id, app_code, account_id, record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            parameters: [
                cacheId(accountId: accountId, recordId: recordId),
                AppIdentity.appCode,
                accountId,
                recordId,
                serviceType,
                "compensation_code",
                "补偿兑换",
                grantCount,
                0,
                grantCount,
                receipt.redeemedAt ?? syncedAt,
                receipt.validUntil ?? "",
                receipt.compensationCode,
                syncedAt
            ]
        )
    }

    func upsertDailyGrant(accountId: String, serviceType: String, totalCount: Int, usedCount: Int, quotaDate: String, acquiredAt: String, expiresAt: String, syncedAt: String) async {
        let normalizedServiceType = normalizedServiceType(serviceType) ?? serviceType
        let safeTotal = max(totalCount, 0)
        let safeUsed = min(max(usedCount, 0), safeTotal)
        let recordId = dailyGrantRecordId(accountId: accountId, serviceType: normalizedServiceType, quotaDate: quotaDate)
        if normalizedServiceType == "local_device" {
            // 中文说明：写入统一日赠记录前清理同一天旧版拆分记录，保证权益页只呈现一条每日赠送。
            _ = try? await database.execute(
                sql: """
                    DELETE FROM \(ReadingLocalTableName.entitlementRecordCache)
                    WHERE app_code = ?
                      AND account_id = ?
                      AND grant_type IN ('daily_gift', 'daily_grant')
                      AND service_type IN ('local_ocr', 'local_tts', 'device_ocr', 'device_tts')
                      AND (record_id LIKE ? OR acquired_at >= ? AND acquired_at <= ?)
                    """,
                parameters: [
                    AppIdentity.appCode,
                    accountId,
                    "%\(quotaDate)%",
                    acquiredAt,
                    expiresAt
                ]
            )
        }
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.entitlementRecordCache)
                (id, app_code, account_id, record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            parameters: [
                cacheId(accountId: accountId, recordId: recordId),
                AppIdentity.appCode,
                accountId,
                recordId,
                normalizedServiceType,
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
                FROM \(ReadingLocalTableName.entitlementRecordCache)
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
                FROM \(ReadingLocalTableName.entitlementRecordCache)
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
                UPDATE \(ReadingLocalTableName.entitlementRecordCache)
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
        let aliases = serviceTypeAliases(for: normalizedServiceType)
        let expectedRecordId = "daily-\(normalizedServiceType)-\(quotaDate)"
        let count = (try? await database.getOptional(
            sql: """
                SELECT COUNT(*) AS total
                FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND service_type IN (\(placeholders(count: aliases.count)))
                  AND grant_type = 'daily_gift'
                  AND record_id = ?
                """,
            parameters: [AppIdentity.appCode, accountId] + aliases + [expectedRecordId]
        ) { cursor in
            try cursor.getIntOptional(name: "total") ?? 0
        }) ?? 0
        return count > 0
    }

    func clear(accountId: String) async {
        _ = try? await database.execute(
            sql: "DELETE FROM \(ReadingLocalTableName.entitlementRecordCache) WHERE app_code = ? AND account_id = ?",
            parameters: [AppIdentity.appCode, accountId]
        )
    }

    func purgeHistoricalCountEntitlements(accountId: String) async {
        _ = try? await database.execute(
            sql: """
                DELETE FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND (
                    service_type NOT IN ('local_ocr', 'local_tts', 'device_ocr', 'device_tts')
                    OR grant_type IN ('legacy_count', 'legacy_quota', 'membership_quota', 'cloud_count_pack')
                    OR acquire_method IN ('内部购买', '后台赠送', '权益赠送')
                  )
                """,
            parameters: [AppIdentity.appCode, accountId]
        )
    }

    private func loadLocalCompensationRecords(accountId: String) async -> [EntitlementRecord] {
        (try? await database.getAll(
            sql: """
                SELECT record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code
                FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND grant_type = 'compensation_code'
                """,
            parameters: [AppIdentity.appCode, accountId]
        ) { cursor in
            let rawServiceType = try cursor.getString(name: "service_type")
            return EntitlementRecord(
                id: try cursor.getString(name: "record_id"),
                serviceType: self.normalizedServiceType(rawServiceType) ?? rawServiceType,
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
    }

    private func upsertCachedRecord(accountId: String, record: EntitlementRecord, syncedAt: String) async {
        let serviceType = normalizedServiceType(record.serviceType) ?? record.serviceType
        _ = try? await database.execute(
            sql: """
                INSERT OR REPLACE INTO \(ReadingLocalTableName.entitlementRecordCache)
                (id, app_code, account_id, record_id, service_type, grant_type, acquire_method, total_count, used_count, remaining_count, acquired_at, expires_at, product_code, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            parameters: [
                cacheId(accountId: accountId, recordId: record.id),
                AppIdentity.appCode,
                accountId,
                record.id,
                serviceType,
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

    private func hasCompensationRecord(accountId: String, serviceType: String, compensationCode: String) async -> Bool {
        let aliases = serviceTypeAliases(for: serviceType)
        let count = (try? await database.getOptional(
            sql: """
                SELECT COUNT(*) AS total
                FROM \(ReadingLocalTableName.entitlementRecordCache)
                WHERE app_code = ?
                  AND account_id = ?
                  AND service_type IN (\(placeholders(count: aliases.count)))
                  AND product_code = ?
                """,
            parameters: [AppIdentity.appCode, accountId] + aliases + [compensationCode]
        ) { cursor in
            try cursor.getIntOptional(name: "total") ?? 0
        }) ?? 0
        return count > 0
    }

    private func normalizedServiceType(_ serviceType: String?) -> String? {
        guard let serviceType = serviceType?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !serviceType.isEmpty,
              serviceType != "all" else {
            return nil
        }
        switch serviceType {
        case "local_device", "local_credits", "local_feature", "daily_login_gift":
            return "local_device"
        case "capture", "local_capture", "ocr", "local_ocr":
            return "local_ocr"
        case "speech", "local_speech", "tts", "local_tts", "device_tts":
            return "local_tts"
        case "cloud_ocr", "cloud_capture":
            return "cloud_ocr"
        case "cloud_tts", "cloud_speech":
            return "cloud_tts"
        default:
            return serviceType
        }
    }

    private func serviceTypeAliases(for serviceType: String) -> [String] {
        switch normalizedServiceType(serviceType) ?? serviceType {
        case "local_device":
            return ["local_device", "local_credits", "local_feature", "daily_login_gift"]
        case "local_ocr":
            return ["local_ocr", "capture", "local_capture", "ocr"]
        case "local_tts":
            return ["local_tts", "speech", "local_speech", "tts", "device_tts"]
        case "cloud_ocr":
            return ["cloud_ocr", "cloud_capture"]
        case "cloud_tts":
            return ["cloud_tts", "cloud_speech"]
        default:
            return [serviceType]
        }
    }

    private func placeholders(count: Int) -> String {
        Array(repeating: "?", count: max(count, 1)).joined(separator: ", ")
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

    private func compensationKey(serviceType: String, compensationCode: String) -> String {
        "\(normalizedServiceType(serviceType) ?? serviceType)#\(compensationCode)"
    }

    private func dailyGrantRecordId(accountId: String, serviceType: String, quotaDate: String) -> String {
        "local_daily_grant#\(accountId)#\(serviceType)#\(quotaDate)"
    }
}
