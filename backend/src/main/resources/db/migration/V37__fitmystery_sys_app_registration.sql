-- Register FitMystery in the common app registry.
-- Earlier FitMystery migrations seeded fit_* tables and sys_remote_config rows, but a fresh
-- first-version database still missed the sys_app registry row. Keep this as an idempotent
-- upsert so both active Flyway migration and squashed first-version initialization agree.

INSERT INTO public.sys_app (app_code, app_name, status, created_at, updated_at)
VALUES ('fitmystery', 'FitMystery', 'active', now(), now())
ON CONFLICT (app_code) DO UPDATE
SET app_name = EXCLUDED.app_name,
    status = EXCLUDED.status,
    updated_at = now();
