import Foundation

enum PowerSyncRuntimeStatus: String, Codable {
    case idle
    case bootstrapping
    case syncing
    case paused
    case error
    case disabled
}

struct PowerSyncRejectedItemView: Codable, Hashable, Identifiable {
    var id: String { "\(entityType):\(entityId):\(reasonCode)" }
    let entityType: String
    let entityId: String
    let reasonCode: String
    let reasonMessage: String
}

struct SyncRuntimeState: Codable, Hashable {
    let status: PowerSyncRuntimeStatus
    let installationId: String?
    let cloudSyncEnabled: Bool
    let initialSyncCompleted: Bool
    let pendingChangeCount: Int
    let lastSyncAt: String?
    let lastErrorMessage: String?
    let rejectedItems: [PowerSyncRejectedItemView]

    static let idle = SyncRuntimeState(
        status: .idle,
        installationId: nil,
        cloudSyncEnabled: false,
        initialSyncCompleted: false,
        pendingChangeCount: 0,
        lastSyncAt: nil,
        lastErrorMessage: nil,
        rejectedItems: []
    )
}

enum PowerSyncEntityType: String, Codable {
    case childProfile = "child_profile"
    case reviewCard = "review_card"
    case reviewEvent = "review_event"
    case usageSession = "usage_session"
    case userPreference = "user_preference"
}

enum PowerSyncOperation: String, Codable {
    case upsert
    case delete
}

enum PowerSyncPayloadValue: Codable, Hashable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case object([String: PowerSyncPayloadValue])
    case array([PowerSyncPayloadValue])
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Int.self) {
            self = .int(value)
        } else if let value = try? container.decode(Double.self) {
            self = .double(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([String: PowerSyncPayloadValue].self) {
            self = .object(value)
        } else if let value = try? container.decode([PowerSyncPayloadValue].self) {
            self = .array(value)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported payload value")
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .string(value): try container.encode(value)
        case let .int(value): try container.encode(value)
        case let .double(value): try container.encode(value)
        case let .bool(value): try container.encode(value)
        case let .object(value): try container.encode(value)
        case let .array(value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }
}

struct PendingSyncChange: Codable, Hashable, Identifiable {
    var id: String { "\(entityType.rawValue):\(entityId)" }
    let entityType: PowerSyncEntityType
    let operation: PowerSyncOperation
    let entityId: String
    let clientUpdatedAt: String
    let payload: [String: PowerSyncPayloadValue]

    var requestItem: PowerSyncChangeItemPayload {
        PowerSyncChangeItemPayload(
            entityType: entityType.rawValue,
            operation: operation.rawValue,
            entityId: entityId,
            clientUpdatedAt: clientUpdatedAt,
            payload: payload
        )
    }
}

struct PowerSyncBootstrapRequestPayload: Encodable {
    let installationId: String
    let deviceId: String?
    let clientPlatform: String
    let deviceModel: String?
    let appVersion: String?
    let cloudSyncEnabled: Bool
    let powersyncClientId: String?
}

struct PowerSyncBootstrapView: Codable {
    let appCode: String
    let installationId: String
    let cloudSyncEnabled: Bool
    let initialSyncCompleted: Bool
    let powerSyncEndpoint: String
    let tokenExpiresAt: String?
    let shouldRebuild: Bool
    let serverTime: String
}

struct PowerSyncTokenRequestPayload: Encodable {
    let installationId: String
}

struct PowerSyncTokenClaimsView: Codable {
    let appCode: String
    let userId: Int
    let installationId: String
}

struct PowerSyncTokenView: Codable {
    let endpoint: String
    let token: String
    let expiresAt: String
    let claims: PowerSyncTokenClaimsView
}

struct PowerSyncRebuildRequestPayload: Encodable {
    let installationId: String
    let reason: String?
}

struct PowerSyncRebuildView: Codable {
    let installationId: String
    let shouldRebuild: Bool
    let message: String
}

struct PowerSyncChangeItemPayload: Encodable, Hashable {
    let entityType: String
    let operation: String
    let entityId: String
    let clientUpdatedAt: String
    let payload: [String: PowerSyncPayloadValue]
}

struct PowerSyncUploadEnvelopePayload: Encodable {
    let installationId: String
    let changes: [PowerSyncChangeItemPayload]
}

struct PowerSyncAcceptedItem: Codable, Hashable, Identifiable {
    var id: String { "\(entityType):\(entityId)" }
    let entityType: String
    let entityId: String
    let serverUpdatedAt: String
}

struct PowerSyncRejectedItem: Codable, Hashable, Identifiable {
    var id: String { "\(entityType):\(entityId):\(reasonCode)" }
    let entityType: String
    let entityId: String
    let reasonCode: String
    let reasonMessage: String
}

struct PowerSyncUploadResult: Codable {
    let accepted: [PowerSyncAcceptedItem]
    let rejected: [PowerSyncRejectedItem]
}

struct StoredPowerSyncCredentials: Codable, Hashable {
    let endpoint: String
    let token: String
    let expiresAt: String
}
