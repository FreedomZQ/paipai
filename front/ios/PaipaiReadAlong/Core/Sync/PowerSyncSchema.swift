import Foundation
import PowerSync

enum ReadingSyncTableName {
    static let childProfile = "reading_child_profile"
    static let reviewCard = "reading_review_card"
    static let reviewEvent = "reading_review_event"
    static let usageSession = "reading_usage_session"
    static let learningEvent = "reading_learning_event"
    static let userPreference = "reading_user_preference"
    static let entitlementRecordCache = "reading_entitlement_record_cache"
    static let weeklyReport = "reading_weekly_report"
}

let readingChildProfileTable = Table(
    name: ReadingSyncTableName.childProfile,
    columns: [
        .text("app_code"),
        .integer("user_id"),
        .text("nickname"),
        .text("age_band"),
        .text("learning_track_code"),
        .text("avatar_emoji"),
        .text("profile_status"),
        .text("deleted_at"),
        .text("last_modified_by_installation_id"),
        .integer("record_version"),
        .text("created_at"),
        .text("updated_at")
    ],
    indexes: [
        .ascending(name: "child_profile_updated_at", column: "updated_at")
    ]
)

let readingReviewCardTable = Table(
    name: ReadingSyncTableName.reviewCard,
    columns: [
        .text("app_code"),
        .integer("user_id"),
        .text("device_id"),
        .text("child_id"),
        .text("learning_track_code"),
        .text("learning_language_code"),
        .text("encrypted_text"),
        .text("text_preview"),
        .text("support_hint"),
        .integer("proficiency"),
        .text("next_review_at"),
        .integer("sync_enabled"),
        .text("storage_mode"),
        .text("card_status"),
        .text("source_text"),
        .text("translated_text"),
        .text("source_language_code"),
        .text("target_language_code"),
        .text("source_type"),
        .text("content_encryption_version"),
        .text("content_key_id"),
        .text("last_reviewed_at"),
        .integer("is_review_completed"),
        .integer("review_count"),
        .text("deleted_at"),
        .text("last_modified_by_installation_id"),
        .integer("record_version"),
        .text("created_at"),
        .text("updated_at")
    ],
    indexes: [
        .ascending(name: "review_card_child_id", column: "child_id"),
        .ascending(name: "review_card_device_id", column: "device_id"),
        .ascending(name: "review_card_next_review_at", column: "next_review_at"),
        .ascending(name: "review_card_updated_at", column: "updated_at")
    ]
)

let readingReviewEventTable = Table(
    name: ReadingSyncTableName.reviewEvent,
    columns: [
        .text("app_code"),
        .integer("user_id"),
        .text("child_id"),
        .text("card_id"),
        .text("event_type"),
        .text("result_level"),
        .text("event_at"),
        .text("last_modified_by_installation_id"),
        .text("created_at"),
        .text("updated_at")
    ],
    indexes: [
        .ascending(name: "review_event_card_id", column: "card_id"),
        .ascending(name: "review_event_event_at", column: "event_at")
    ]
)

let readingUsageSessionTable = Table(
    name: ReadingSyncTableName.usageSession,
    columns: [
        .text("app_code"),
        .integer("user_id"),
        .text("child_id"),
        .text("source_page"),
        .text("started_at"),
        .text("ended_at"),
        .integer("duration_seconds"),
        .text("client_platform"),
        .text("device_model"),
        .text("last_modified_by_installation_id"),
        .text("deleted_at"),
        .text("created_at"),
        .text("updated_at")
    ],
    indexes: [
        .ascending(name: "usage_session_child_id", column: "child_id"),
        .ascending(name: "usage_session_started_at", column: "started_at")
    ]
)

let readingLearningEventTable = Table(
    name: ReadingSyncTableName.learningEvent,
    columns: [
        .text("app_code"),
        .integer("user_id"),
        .text("child_id"),
        .text("learning_date"),
        .text("source_page"),
        .text("event_at"),
        .text("last_modified_by_installation_id"),
        .text("created_at"),
        .text("updated_at")
    ],
    indexes: [
        .ascending(name: "learning_event_child_id", column: "child_id"),
        .ascending(name: "learning_event_date", column: "learning_date")
    ]
)

let readingUserPreferenceTable = Table(
    name: ReadingSyncTableName.userPreference,
    columns: [
        .text("app_code"),
        .integer("user_id"),
        .text("ui_locale"),
        .text("source_language_code"),
        .text("target_language_code"),
        .text("reading_track_code"),
        .text("tts_voice_code"),
        .text("translation_mode"),
        .integer("cloud_sync_enabled"),
        .text("last_modified_by_installation_id"),
        .integer("record_version"),
        .text("created_at"),
        .text("updated_at")
    ]
)

let readingEntitlementRecordCacheTable = Table(
    name: ReadingSyncTableName.entitlementRecordCache,
    columns: [
        .text("app_code"),
        .text("account_id"),
        .text("record_id"),
        .text("service_type"),
        .text("grant_type"),
        .text("acquire_method"),
        .integer("total_count"),
        .integer("used_count"),
        .integer("remaining_count"),
        .text("acquired_at"),
        .text("expires_at"),
        .text("product_code"),
        .text("synced_at")
    ],
    indexes: [
        .ascending(name: "entitlement_record_account", column: "account_id"),
        .ascending(name: "entitlement_record_service", column: "service_type"),
        .ascending(name: "entitlement_record_expires_at", column: "expires_at")
    ]
)

let readingWeeklyReportTable = Table(
    name: ReadingSyncTableName.weeklyReport,
    columns: [
        .text("app_code"),
        .text("child_id"),
        .text("child_name"),
        .text("week_start"),
        .text("week_end"),
        .text("locale_code"),
        .text("report_json"),
        .integer("open_count"),
        .text("generated_at"),
        .text("last_opened_at"),
        .text("created_at"),
        .text("updated_at")
    ],
    indexes: [
        .ascending(name: "weekly_report_child_week", column: "child_id"),
        .ascending(name: "weekly_report_week_start", column: "week_start"),
        .ascending(name: "weekly_report_generated_at", column: "generated_at")
    ]
)

let ReadingPowerSyncSchema = Schema(
    readingChildProfileTable,
    readingReviewCardTable,
    readingReviewEventTable,
    readingUsageSessionTable,
    readingLearningEventTable,
    readingUserPreferenceTable,
    readingEntitlementRecordCacheTable,
    readingWeeklyReportTable
)
