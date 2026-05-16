-- Repair legacy aggregate cloud usage counters so they remain consistent with
-- authoritative credit grants and cannot expose negative or impossible balances.
UPDATE public.reading_cloud_service_usage
SET
    trial_limit = GREATEST(COALESCE(trial_limit, 0), 0),
    trial_used = LEAST(GREATEST(COALESCE(trial_used, 0), 0), GREATEST(COALESCE(trial_limit, 0), 0)),
    purchased_credits = GREATEST(COALESCE(purchased_credits, 0), 0),
    purchased_used = LEAST(GREATEST(COALESCE(purchased_used, 0), 0), GREATEST(COALESCE(purchased_credits, 0), 0)),
    updated_at = CURRENT_TIMESTAMP
WHERE
    trial_limit IS NULL
    OR trial_used IS NULL
    OR purchased_credits IS NULL
    OR purchased_used IS NULL
    OR trial_limit < 0
    OR trial_used < 0
    OR purchased_credits < 0
    OR purchased_used < 0
    OR trial_used > trial_limit
    OR purchased_used > purchased_credits;

UPDATE public.reading_cloud_service_credit_grant
SET
    used_count = LEAST(GREATEST(COALESCE(used_count, 0), 0), total_count),
    updated_at = CURRENT_TIMESTAMP
WHERE used_count IS NULL OR used_count < 0 OR used_count > total_count;

CREATE INDEX IF NOT EXISTS idx_reading_credit_grant_use_priority
    ON public.reading_cloud_service_credit_grant(app_code, user_id, service_type, expires_at, grant_type, id)
    WHERE used_count < total_count;
