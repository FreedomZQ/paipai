CREATE INDEX IF NOT EXISTS idx_reading_review_card_user_language_created
ON reading_review_card (app_code, user_id, source_language_code, target_language_code, created_at DESC)
WHERE deleted_at IS NULL;
