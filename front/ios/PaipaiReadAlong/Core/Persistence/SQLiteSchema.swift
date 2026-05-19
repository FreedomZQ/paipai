import Foundation

enum ReadingLocalTableName {
    static let childProfile = "reading_child_profile"
    static let reviewCard = "reading_review_card"
    static let reviewEvent = "reading_review_event"
    static let usageSession = "reading_usage_session"
    static let learningEvent = "reading_learning_event"
    static let userPreference = "reading_user_preference"
    static let entitlementRecordCache = "reading_entitlement_record_cache"
    static let weeklyReport = "reading_weekly_report"
}

enum SQLiteSchema {
    static let bootstrapStatements: [String] = [
        """
        CREATE TABLE IF NOT EXISTS reading_child_profile (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            nickname TEXT NOT NULL,
            age_band TEXT NOT NULL,
            learning_track_code TEXT NOT NULL,
            avatar_emoji TEXT,
            profile_status TEXT NOT NULL DEFAULT 'active',
            deleted_at TEXT,
            record_version INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_review_card (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            device_id TEXT,
            child_id TEXT,
            learning_track_code TEXT,
            learning_language_code TEXT,
            encrypted_text TEXT NOT NULL,
            text_preview TEXT,
            support_hint TEXT,
            proficiency INTEGER NOT NULL DEFAULT 0,
            next_review_at TEXT,
            card_status TEXT NOT NULL DEFAULT 'active',
            source_text TEXT,
            translated_text TEXT,
            source_language_code TEXT,
            target_language_code TEXT,
            source_type TEXT,
            content_encryption_version TEXT,
            content_key_id TEXT,
            last_reviewed_at TEXT,
            is_review_completed INTEGER NOT NULL DEFAULT 0,
            review_count INTEGER NOT NULL DEFAULT 0,
            deleted_at TEXT,
            record_version INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_review_event (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            user_id INTEGER,
            child_id TEXT NOT NULL,
            card_id TEXT NOT NULL,
            event_type TEXT NOT NULL,
            result_level TEXT NOT NULL,
            event_at TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_usage_session (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            user_id INTEGER,
            child_id TEXT NOT NULL,
            source_page TEXT NOT NULL,
            started_at TEXT NOT NULL,
            ended_at TEXT,
            duration_seconds INTEGER,
            client_platform TEXT,
            device_model TEXT,
            deleted_at TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_learning_event (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            user_id INTEGER,
            child_id TEXT NOT NULL,
            learning_date TEXT NOT NULL,
            source_page TEXT NOT NULL,
            event_at TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_user_preference (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            user_id INTEGER,
            ui_locale TEXT,
            source_language_code TEXT,
            target_language_code TEXT,
            reading_track_code TEXT,
            tts_voice_code TEXT,
            translation_mode TEXT,
            record_version INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_entitlement_record_cache (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            account_id TEXT NOT NULL,
            record_id TEXT NOT NULL,
            service_type TEXT NOT NULL,
            grant_type TEXT NOT NULL,
            acquire_method TEXT NOT NULL,
            total_count INTEGER NOT NULL DEFAULT 0,
            used_count INTEGER NOT NULL DEFAULT 0,
            remaining_count INTEGER NOT NULL DEFAULT 0,
            acquired_at TEXT,
            expires_at TEXT,
            product_code TEXT,
            synced_at TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS reading_weekly_report (
            id TEXT PRIMARY KEY NOT NULL,
            app_code TEXT NOT NULL,
            child_id TEXT NOT NULL,
            child_name TEXT NOT NULL,
            week_start TEXT NOT NULL,
            week_end TEXT NOT NULL,
            locale_code TEXT NOT NULL,
            report_json TEXT NOT NULL,
            open_count INTEGER NOT NULL DEFAULT 0,
            generated_at TEXT NOT NULL,
            last_opened_at TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_reading_review_card_child ON reading_review_card(child_id)",
        "CREATE INDEX IF NOT EXISTS idx_reading_review_card_updated ON reading_review_card(updated_at)",
        "CREATE INDEX IF NOT EXISTS idx_reading_usage_session_child ON reading_usage_session(child_id)",
        "CREATE INDEX IF NOT EXISTS idx_reading_usage_session_started ON reading_usage_session(started_at)",
        "CREATE INDEX IF NOT EXISTS idx_reading_weekly_report_child_week ON reading_weekly_report(child_id, week_start)",
        "CREATE INDEX IF NOT EXISTS idx_reading_entitlement_record_account ON reading_entitlement_record_cache(account_id)",
        "CREATE INDEX IF NOT EXISTS idx_reading_entitlement_record_service ON reading_entitlement_record_cache(service_type)"
    ]
}
